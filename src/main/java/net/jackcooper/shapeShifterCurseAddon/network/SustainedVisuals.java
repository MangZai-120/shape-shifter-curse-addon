package net.jackcooper.shapeShifterCurseAddon.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authorized visual leases. Gameplay still runs in the original ability/action. */
public final class SustainedVisuals {
    public static final Identifier STATE = new Identifier("ssc_addon", "sustained_visuals_v2");
    public record Key(UUID source, Kind kind) {}
    public record View(Key key, UUID decorationOwner, int entityId, Vec3d pos, long epoch, int duration,
                       float width, float eyeHeight, double radius, double outerRadius) {
        public void write(PacketByteBuf buf) {
            buf.writeUuid(key.source());
            buf.writeEnumConstant(key.kind());
            buf.writeUuid(decorationOwner);
            buf.writeVarInt(entityId);
            buf.writeDouble(pos.x); buf.writeDouble(pos.y); buf.writeDouble(pos.z);
            buf.writeLong(epoch); buf.writeVarInt(duration);
            buf.writeFloat(width); buf.writeFloat(eyeHeight);
            buf.writeDouble(radius); buf.writeDouble(outerRadius);
        }
        public static View read(PacketByteBuf buf) {
            Key key = new Key(buf.readUuid(), buf.readEnumConstant(Kind.class));
            return new View(key, buf.readUuid(), buf.readVarInt(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble());
        }
    }
    private static final class Entry {
        Entity source;
        ServerWorld world;
        View view;
        int touched;
    }
    private record Audience(ServerPlayerEntity player, ServerWorld world, int sent, List<View> views) {}
    private static final Map<Key, Entry> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();
    private SustainedVisuals() {}

    public static void init() {
        // Registered after Nova's END_SERVER_TICK producer; entity and world ticks have also finished.
        ServerTickEvents.END_SERVER_TICK.register(SustainedVisuals::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AUDIENCES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ACTIVE.clear(); AUDIENCES.clear(); });
    }

    /** Called instead of generating this tick's particles; no packet is sent here. */
    public static void touch(Entity source, Kind kind, int elapsed, int duration, double radius, double outerRadius) {
        if (!(source.getWorld() instanceof ServerWorld world)) return;
        Key key = new Key(source.getUuid(), kind);
        Entry entry = ACTIVE.computeIfAbsent(key, ignored -> new Entry());
        entry.source = source;
        entry.world = world;
        entry.touched = world.getServer().getTicks();
        UUID owner = source instanceof net.jackcooper.shapeShifterCurseAddon.entity.FrostStormEntity storm
                && storm.getDecorationOwner() != null ? storm.getDecorationOwner() : source.getUuid();
        entry.view = new View(key, owner, source.getId(), source.getPos(), world.getTime() - elapsed,
                duration, source.getWidth(), source.getEyeHeight(source.getPose()), radius, outerRadius);
    }

    public static void stop(Entity source, Kind kind) { ACTIVE.remove(new Key(source.getUuid(), kind)); }

    private static void flush(MinecraftServer server) {
        int tick = server.getTicks();
        ACTIVE.values().removeIf(e -> e.touched < tick || e.source.isRemoved() || e.source.getWorld() != e.world);
        if (ACTIVE.isEmpty() && AUDIENCES.isEmpty()) return;
        Map<UUID, Collection<ServerPlayerEntity>> tracking = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, STATE)) continue;
            List<View> visible = new ArrayList<>();
            for (Entry entry : ACTIVE.values()) {
                // Include the effect's extent; the client applies the original distance test per particle batch.
                double range = entry.view.key.kind.range + Math.max(entry.view.radius, entry.view.outerRadius) + 4;
                // The seed's rotating armor-stand core can be tracked farther away than its 32-block particles.
                boolean trackedCore = entry.view.key.kind == Kind.SEED_FIELD
                        && tracking.computeIfAbsent(entry.source.getUuid(), ignored -> PlayerLookup.tracking(entry.source)).contains(player);
                if (entry.world == player.getWorld() && (player.squaredDistanceTo(entry.view.pos) <= range * range || trackedCore))
                    visible.add(entry.view);
            }
            Audience previous = AUDIENCES.get(player.getUuid());
            boolean newWorld = previous == null || previous.player != player || previous.world != player.getWorld();
            if (visible.isEmpty() && (previous == null || previous.views.isEmpty())) continue;
            boolean changed = newWorld || !sameState(previous.views, visible);
            boolean moved = false;
            if (!newWorld && !changed) for (int i = 0; i < visible.size(); i++) {
                View view = visible.get(i);
                Entity source = ACTIVE.get(view.key).source;
                // Inside tracking range, use existing entity movement packets. Outside it, retain the
                // original per-tick center accuracy with one state packet, rather than dozens of particles.
                if (source != player && view.pos.squaredDistanceTo(previous.views.get(i).pos) > 1.0e-12
                        && !tracking.computeIfAbsent(source.getUuid(), ignored -> PlayerLookup.tracking(source)).contains(player)) {
                    moved = true;
                    break;
                }
            }
            if (!changed && !moved && tick - previous.sent < 20) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
            buf.writeLong(player.getWorld().getTime());
            buf.writeVarInt(visible.size());
            visible.forEach(view -> view.write(buf));
            ServerPlayNetworking.send(player, STATE, buf);
            if (visible.isEmpty()) AUDIENCES.remove(player.getUuid());
            else AUDIENCES.put(player.getUuid(), new Audience(player, player.getServerWorld(), tick, visible));
        }
    }

    static boolean sameState(List<View> a, List<View> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            View x = a.get(i), y = b.get(i);
            if (!x.key.equals(y.key) || !x.decorationOwner.equals(y.decorationOwner)
                    || x.entityId != y.entityId || x.epoch != y.epoch || x.duration != y.duration
                    || x.width != y.width || x.eyeHeight != y.eyeHeight || x.radius != y.radius || x.outerRadius != y.outerRadius) return false;
        }
        return true;
    }
}
