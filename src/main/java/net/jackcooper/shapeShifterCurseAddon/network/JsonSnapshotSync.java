package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Main-thread content snapshots. Reliable play packets establish the baseline per connection. */
public final class JsonSnapshotSync {
    public static final Identifier CHUNKS = new Identifier("ssc_addon", "json_snapshot_v1");
    public static final Identifier BEGIN = new Identifier("ssc_addon", "configuration_begin_v1");
    public static final Identifier SCOPED_CHUNKS = new Identifier("ssc_addon", "json_snapshot_v2");
    public static final Identifier BEGIN_SCOPED = new Identifier("ssc_addon", "configuration_begin_v2");
    private record Key(UUID player, Identifier channel) {}
    private record Payload(Map<String, String> content, byte[] bytes) {}
    private record Sent(Object player, Object connection, Payload payload) {}
    private static final Map<Identifier, Payload> ENCODED = new HashMap<>();
    private static final Map<Key, Sent> SENT = new HashMap<>();
    private static long transferSequence;
    private JsonSnapshotSync() {}

    public static void register() {
        PlayerSessionCleanup.register((handler, server) -> {
            SENT.entrySet().removeIf(entry -> entry.getValue().connection == handler);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SENT.clear(); ENCODED.clear(); transferSequence = 0; });
    }

    private static Payload payload(Identifier channel, Map<String, String> content) {
        Payload payload = ENCODED.get(channel);
        if (payload == null || !payload.content.equals(content)) {
            payload = new Payload(Map.copyOf(content), JsonSnapshotWire.encode(content));
            ENCODED.put(channel, payload);
        }
        return payload;
    }

    public static boolean canSend(ServerPlayerEntity player, Identifier channel, Map<String, String> content) {
        return supportsScoped(player) || ServerPlayNetworking.canSend(player, CHUNKS)
                || ServerPlayNetworking.canSend(player, channel) && payload(channel, content).bytes.length <= JsonSnapshotWire.LEGACY_LIMIT;
    }

    public static boolean supportsScoped(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, BEGIN_SCOPED) && ServerPlayNetworking.canSend(player, SCOPED_CHUNKS);
    }

    /** Called only by the complete configuration sender after its matching BEGIN. */
    public static void sendScoped(ServerPlayerEntity player, Identifier channel, Map<String, String> content,
                                  long session, long revision) {
        JsonSnapshotWire.Kind kind = channel.equals(SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC)
                ? JsonSnapshotWire.Kind.SPELLS : JsonSnapshotWire.Kind.EVOLUTION;
        for (byte[] frame : JsonSnapshotWire.frames(kind, ++transferSequence, payload(channel, content).bytes)) {
            var out = PacketByteBufs.create();
            out.writeBytes(JsonSnapshotWire.scope(session, revision, frame));
            ServerPlayNetworking.send(player, SCOPED_CHUNKS, out);
        }
    }

    public static boolean send(ServerPlayerEntity player, Identifier channel, Map<String, String> content, boolean force) {
        // v2 registries belong to BalanceIntegration's complete transaction, including reloads and retries.
        if (supportsScoped(player)) return false;
        if (!canSend(player, channel, content)) return false;
        Payload payload = payload(channel, content);
        Key key = new Key(player.getUuid(), channel);
        Sent previous = SENT.get(key);
        if (!force && previous != null && previous.player == player && previous.connection == player.networkHandler
                && previous.payload == payload) return true;
        if (payload.bytes.length <= JsonSnapshotWire.LEGACY_LIMIT && ServerPlayNetworking.canSend(player, channel)) {
            var out = PacketByteBufs.create();
            out.writeBytes(payload.bytes);
            ServerPlayNetworking.send(player, channel, out);
        } else {
            JsonSnapshotWire.Kind kind = channel.equals(SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC)
                    ? JsonSnapshotWire.Kind.SPELLS : JsonSnapshotWire.Kind.EVOLUTION;
            for (byte[] frame : JsonSnapshotWire.frames(kind, ++transferSequence, payload.bytes)) {
                var out = PacketByteBufs.create();
                out.writeBytes(frame);
                ServerPlayNetworking.send(player, CHUNKS, out);
            }
        }
        SENT.put(key, new Sent(player, player.networkHandler, payload));
        return true;
    }
}
