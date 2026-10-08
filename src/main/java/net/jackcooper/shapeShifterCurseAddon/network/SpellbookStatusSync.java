package net.jackcooper.shapeShifterCurseAddon.network;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookStatus;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Sends deadline changes immediately and a sparse recovery snapshot; never predicts or changes mana. */
public final class SpellbookStatusSync {
    public static final Identifier STATE = new Identifier("ssc_addon", "spellbook_status_v1");
    private static final int HEARTBEAT_TICKS = 100;
    private record Audience(ServerPlayerEntity player, ServerWorld world, SpellbookStatus status, int sent) {}
    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();

    public record Snapshot(Identifier dimension, int entityId, long time, SpellbookStatus status) {
        public void write(PacketByteBuf buf) {
            buf.writeIdentifier(dimension);
            buf.writeVarInt(entityId);
            buf.writeLong(time);
            buf.writeBoolean(status != null);
            if (status != null) {
                buf.writeLong(status.naturalRegenAt());
                buf.writeLong(status.swapReadyAt());
                buf.writeLong(status.castReadyAt());
                buf.writeBoolean(status.casting());
                buf.writeVarInt(status.naturalRegenPerSecond());
            }
        }

        public static Snapshot read(PacketByteBuf buf) {
            Identifier dimension = buf.readIdentifier();
            int entityId = buf.readVarInt();
            long time = buf.readLong();
            SpellbookStatus status = buf.readBoolean()
                    ? new SpellbookStatus(buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readVarInt()) : null;
            return new Snapshot(dimension, entityId, time, status);
        }
    }

    private SpellbookStatusSync() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) server.getPlayerManager().getPlayerList().forEach(SpellbookStatusSync::sync);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> sync(player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sync(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AUDIENCES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AUDIENCES.clear());
    }

    public static void sync(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, STATE)) return;
        var book = SpellCastManager.getEquippedBook(player);
        SpellbookStatus status = book == null || book.isEmpty() ? null
                : new SpellbookStatus(FormCastingStyle.getNaturalRegenAt(player),
                FormCastingStyle.getSwapReadyAt(player), SpellChannelManager.getNextCastAt(player),
                SpellChannelManager.isCasting(player), FormCastingStyle.naturalRegenPerSecond(book));
        Audience previous = AUDIENCES.get(player.getUuid());
        if (status == null && previous == null) return;
        int tick = player.getServer().getTicks();
        if (previous != null && previous.player == player && previous.world == player.getWorld()
                && Objects.equals(previous.status, status) && tick - previous.sent < HEARTBEAT_TICKS) return;
        Snapshot snapshot = new Snapshot(player.getWorld().getRegistryKey().getValue(), player.getId(),
                player.getWorld().getTime(), status);
        PacketByteBuf buf = PacketByteBufs.create();
        snapshot.write(buf);
        ServerPlayNetworking.send(player, STATE, buf);
        if (status == null) AUDIENCES.remove(player.getUuid());
        else AUDIENCES.put(player.getUuid(), new Audience(player, player.getServerWorld(), status, tick));
    }
}
