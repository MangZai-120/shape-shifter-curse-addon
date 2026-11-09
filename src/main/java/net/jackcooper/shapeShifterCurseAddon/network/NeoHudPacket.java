package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.Objects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;

/** Exact HUD values over a versioned, fixed schema. No simulation or quantization happens here. */
public final class NeoHudPacket {
    private NeoHudPacket() {}

    private record Field(String name, int type) {}
    // Changing the order or type requires a new channel version, never an in-place wire change.
    private static final Field[] FIELDS = {
            bool("Visible"), integer("PlayerId"), string("Dimension"), integer("MasteryExpTen"),
            bool("BookEquipped"), integer("CastingLevel"), decimal("DangerMax"), decimal("C"),
            decimal("S"), decimal("BodyS"), integer("Recovery"), integer("RecoveryDelay"),
            decimal("FrostSpellMultiplier"), longNumber("FrostReadyUntil"), longNumber("NovaQuickCastUntil"),
            decimal("Q"), decimal("Failure"), decimal("ForecastFailure"), decimal("Offset"),
            bool("HasSpell"), integer("LockBoundary"), string("ResultId"), string("Result"),
            decimal("FailureBasis"), integer("CD"), bool("Stored"), integer("Age"), integer("Prep"),
            decimal("Paid"), decimal("Yaw"), decimal("Pitch"), decimal("Cumulative"), string("Spell"),
            integer("CollapseStage"), integer("Blocked"), string("Form"), bool("Reference"), string("Profile")
    };
    private static final long VALID_MASK = (1L << FIELDS.length) - 1;
    private static final long REQUIRED_MASK = VALID_MASK & ~(1L << 21) & ~(1L << 22);
    private static Field bool(String name) { return new Field(name, NbtElement.BYTE_TYPE); }
    private static Field integer(String name) { return new Field(name, NbtElement.INT_TYPE); }
    private static Field longNumber(String name) { return new Field(name, NbtElement.LONG_TYPE); }
    private static Field decimal(String name) { return new Field(name, NbtElement.DOUBLE_TYPE); }
    private static Field string(String name) { return new Field(name, NbtElement.STRING_TYPE); }

    public record Update(boolean full, long revision, long base, NbtCompound values, long removed) {
        public Update {
            if (revision <= 0 || base < 0 || full && base != 0 || !full && base >= revision)
                throw new IllegalArgumentException("Invalid HUD revision");
            values = values.copy();
            if ((removed & ~VALID_MASK) != 0 || full && removed != 0)
                throw new IllegalArgumentException("Invalid HUD removals");
        }
    }

    public static Update between(NbtCompound before, NbtCompound after, long revision, long base, boolean full) {
        NbtCompound changed = new NbtCompound();
        long removed = 0;
        int present = 0;
        for (int i = 0; i < FIELDS.length; i++) {
            String key = FIELDS[i].name;
            var value = after.get(key);
            if (value != null) present++;
            if (full || !Objects.equals(before.get(key), value)) {
                if (value != null) changed.put(key, value.copy());
                else if (!full) removed |= 1L << i;
            }
        }
        if (present != after.getSize()) throw new IllegalArgumentException("HUD schema needs a new channel version");
        return new Update(full, revision, full ? 0 : base, changed, removed);
    }

    public static void write(PacketByteBuf buf, Update update) {
        long changed = 0;
        for (int i = 0; i < FIELDS.length; i++) {
            Field field = FIELDS[i];
            if (update.values.contains(field.name)) {
                if (!update.values.contains(field.name, field.type))
                    throw new IllegalArgumentException("Invalid HUD type: " + field.name);
                changed |= 1L << i;
            }
        }
        if (Long.bitCount(changed) != update.values.getSize()) throw new IllegalArgumentException("Unknown HUD fields");
        validateMask(update.full, changed, update.removed);
        buf.writeBoolean(update.full);
        buf.writeVarLong(update.revision);
        if (!update.full) buf.writeVarLong(update.base);
        buf.writeLong(changed);
        if (!update.full) buf.writeLong(update.removed);
        for (int i = 0; i < FIELDS.length; i++) if ((changed & (1L << i)) != 0) {
            Field f = FIELDS[i];
            switch (f.type) {
                case NbtElement.BYTE_TYPE -> buf.writeBoolean(update.values.getBoolean(f.name));
                case NbtElement.INT_TYPE -> buf.writeVarInt(update.values.getInt(f.name));
                case NbtElement.LONG_TYPE -> buf.writeVarLong(update.values.getLong(f.name));
                case NbtElement.DOUBLE_TYPE -> buf.writeDouble(update.values.getDouble(f.name));
                case NbtElement.STRING_TYPE -> buf.writeString(update.values.getString(f.name));
                default -> throw new IllegalStateException("Unsupported HUD type");
            }
        }
    }

    public static Update read(PacketByteBuf buf) {
        boolean full = buf.readBoolean();
        long revision = buf.readVarLong(), base = full ? 0 : buf.readVarLong();
        long changed = buf.readLong(), removed = full ? 0 : buf.readLong();
        validateMask(full, changed, removed);
        NbtCompound values = new NbtCompound();
        for (int i = 0; i < FIELDS.length; i++) if ((changed & (1L << i)) != 0) {
            Field f = FIELDS[i];
            switch (f.type) {
                case NbtElement.BYTE_TYPE -> values.putBoolean(f.name, buf.readBoolean());
                case NbtElement.INT_TYPE -> values.putInt(f.name, buf.readVarInt());
                case NbtElement.LONG_TYPE -> values.putLong(f.name, buf.readVarLong());
                case NbtElement.DOUBLE_TYPE -> values.putDouble(f.name, buf.readDouble());
                case NbtElement.STRING_TYPE -> values.putString(f.name, buf.readString());
                default -> throw new IllegalStateException("Unsupported HUD type");
            }
        }
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing HUD data");
        return new Update(full, revision, base, values, removed);
    }

    private static void validateMask(boolean full, long changed, long removed) {
        if (((changed | removed) & ~VALID_MASK) != 0 || (changed & removed) != 0
                || full && (changed & REQUIRED_MASK) != REQUIRED_MASK
                || !full && (removed & REQUIRED_MASK) != 0)
            throw new IllegalArgumentException("Invalid HUD field mask");
    }

    /** Client-owned authoritative mirror; mutable HUD flash flags never enter its delta baseline. */
    public static final class Receiver {
        public enum Result { APPLIED, MISSING_BASE, STALE }
        private NbtCompound state = new NbtCompound();
        private long revision;
        public void clear() { state = new NbtCompound(); revision = 0; }
        public NbtCompound state() { return state.copy(); }
        public long revision() { return revision; }
        public Result accept(Update update) {
            if (update.revision <= revision) return Result.STALE;
            if (!update.full && (revision == 0 || revision != update.base)) return Result.MISSING_BASE;
            NbtCompound next = update.full ? new NbtCompound() : state.copy();
            for (int i = 0; i < FIELDS.length; i++) if ((update.removed & (1L << i)) != 0)
                next.remove(FIELDS[i].name);
            update.values.getKeys().forEach(key -> next.put(key, update.values.get(key).copy()));
            state = next;
            revision = update.revision;
            return Result.APPLIED;
        }
    }
}
