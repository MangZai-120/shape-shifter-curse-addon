package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Change-driven transport for a future bar; countdowns are interpolated by the client. */
public final class AnomalyNetwork {
    public static final Identifier STATE = new Identifier("ssc_addon", "anomaly_snapshot");
    public static final Identifier CONFIG = new Identifier("ssc_addon", "anomaly_rules");
    public static final int MAX_CONFIG_CHARS = 262144;
    private static final AnomalySnapshot EMPTY = new AnomalySnapshot(500, java.util.Map.of(), 0, "", 0, List.of());
    private static MinecraftServer server;
    private static boolean initialized;

    public record Message(Identifier dimension, int entityId, UUID entityUuid, AnomalySnapshot snapshot) {}

    private AnomalyNetwork() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(current -> server = current);
        ServerLifecycleEvents.SERVER_STOPPED.register(current -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, current) -> {
            configuration(handler.player);
            sync(handler.player);
        });
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living && (AnomalyManager.state(living) != null || AnomalyEffects.hasState(living)))
                send(living, player, AnomalyManager.snapshot(living));
        });
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living && (AnomalyManager.state(living) != null || AnomalyEffects.hasState(living)))
                send(living, player, EMPTY);
        });
        AnomalyRules.onReload(() -> {
            MinecraftServer current = server;
            if (current != null) current.execute(() -> {
                if (server == current)
                    for (var player : current.getPlayerManager().getPlayerList()) configuration(player);
            });
        });
    }

    public static void sync(LivingEntity target) { broadcast(target, null); }
    public static void clear(LivingEntity target) { broadcast(target, EMPTY); }

    private static void broadcast(LivingEntity target, AnomalySnapshot supplied) {
        var viewers = PlayerLookup.tracking(target);
        ServerPlayerEntity self = target instanceof ServerPlayerEntity player ? player : null;
        if (viewers.isEmpty() && (self == null || !ServerPlayNetworking.canSend(self, STATE))) return;
        AnomalySnapshot snapshot = supplied == null ? AnomalyManager.snapshot(target) : supplied;
        // The common owner-only/single-observer case needs no intermediate copy.
        if (viewers.isEmpty()) { send(target, self, snapshot); return; }
        if (viewers.size() == 1 && (self == null || viewers.contains(self))) {
            send(target, viewers.iterator().next(), snapshot);
            return;
        }
        // Every observer receives the same authoritative bytes. Encode once, while each
        // connection retains its own buffer so asynchronous packet encoding cannot share indices.
        var encoded = PacketByteBufs.create();
        try {
            write(encoded, new Message(target.getWorld().getRegistryKey().getValue(), target.getId(), target.getUuid(), snapshot));
            for (var viewer : viewers) sendEncoded(viewer, encoded);
            if (self != null && !viewers.contains(self)) sendEncoded(self, encoded);
        } finally { encoded.release(); }
    }

    private static void sendEncoded(ServerPlayerEntity player, PacketByteBuf encoded) {
        if (!ServerPlayNetworking.canSend(player, STATE)) return;
        ServerPlayNetworking.send(player, STATE, PacketByteBufs.copy(encoded));
        AnomalyManager.recordPacket();
    }

    private static void send(LivingEntity target, ServerPlayerEntity player, AnomalySnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(player, STATE)) return;
        var buffer = PacketByteBufs.create();
        write(buffer, new Message(target.getWorld().getRegistryKey().getValue(), target.getId(), target.getUuid(), snapshot));
        ServerPlayNetworking.send(player, STATE, buffer);
        AnomalyManager.recordPacket();
    }

    private static void configuration(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, CONFIG)) return;
        var buffer = PacketByteBufs.create();
        buffer.writeString(AnomalyRules.snapshotJson(), MAX_CONFIG_CHARS);
        ServerPlayNetworking.send(player, CONFIG, buffer);
        AnomalyManager.recordPacket();
    }

    /** The codec is also exercised without a live network connection by integration probes. */
    public static void write(PacketByteBuf buffer, Message message) {
        var snapshot = message.snapshot();
        buffer.writeIdentifier(message.dimension());
        buffer.writeVarInt(message.entityId());
        buffer.writeUuid(message.entityUuid());
        buffer.writeDouble(snapshot.maximum());
        buffer.writeVarInt(snapshot.remainingTicks());
        buffer.writeVarInt(snapshot.values().size());
        snapshot.values().forEach((element, amount) -> {
            buffer.writeString(element, 128);
            buffer.writeDouble(amount);
        });
        buffer.writeString(snapshot.lastElement(), 128);
        buffer.writeVarInt(snapshot.linkRemaining());
        buffer.writeVarInt(snapshot.effects().size());
        for (var effect : snapshot.effects()) {
            buffer.writeString(effect.id(), 128);
            buffer.writeVarInt(effect.ticks());
            buffer.writeDouble(effect.strength());
        }
    }

    public static Message read(PacketByteBuf buffer) {
        Identifier dimension = buffer.readIdentifier();
        int entityId = buffer.readVarInt();
        UUID uuid = buffer.readUuid();
        double maximum = buffer.readDouble();
        if (!Double.isFinite(maximum) || maximum <= 0 || maximum > 1_000_000)
            throw new IllegalArgumentException("Invalid anomaly maximum");
        int remaining = bounded(buffer.readVarInt(), 0, AnomalyLedger.RETENTION_TICKS);
        int count = bounded(buffer.readVarInt(), 0, 64);
        var values = new LinkedHashMap<String, Double>();
        for (int i = 0; i < count; i++) {
            String element = buffer.readString(128);
            double amount = buffer.readDouble();
            if (element.isBlank() || !Double.isFinite(amount) || amount < 0 || amount > maximum
                    || values.put(element, amount) != null)
                throw new IllegalArgumentException("Invalid anomaly meter");
        }
        String last = buffer.readString(128);
        int link = bounded(buffer.readVarInt(), 0, AnomalyLedger.LINK_TICKS);
        int effectCount = bounded(buffer.readVarInt(), 0, 64);
        var effects = new java.util.ArrayList<AnomalyEffects.EffectView>(effectCount);
        for (int i = 0; i < effectCount; i++) {
            String id = buffer.readString(128);
            int ticks = bounded(buffer.readVarInt(), 0, 72000);
            double strength = buffer.readDouble();
            if (id.isBlank() || !Double.isFinite(strength) || strength < 0)
                throw new IllegalArgumentException("Invalid anomaly effect");
            effects.add(new AnomalyEffects.EffectView(id, ticks, strength));
        }
        return new Message(dimension, entityId, uuid,
                new AnomalySnapshot(maximum, values, remaining, last, link, effects));
    }

    private static int bounded(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Invalid anomaly count or duration");
        return value;
    }
}
