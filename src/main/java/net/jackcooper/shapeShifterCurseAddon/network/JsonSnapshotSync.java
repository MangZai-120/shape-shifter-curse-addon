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
    private record Key(UUID player, Identifier channel) {}
    private record Payload(Map<String, String> content, byte[] bytes) {}
    private record Sent(Object player, Object connection, Payload payload) {}
    private static final Map<Identifier, Payload> ENCODED = new HashMap<>();
    private static final Map<Key, Sent> SENT = new HashMap<>();
    private JsonSnapshotSync() {}

    public static void register() {
        PlayerSessionCleanup.register((handler, server) -> {
            SENT.entrySet().removeIf(entry -> entry.getValue().connection == handler);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SENT.clear(); ENCODED.clear(); });
    }

    public static void send(ServerPlayerEntity player, Identifier channel, Map<String, String> content, boolean force) {
        if (!ServerPlayNetworking.canSend(player, channel)) return;
        Payload payload = ENCODED.get(channel);
        if (payload == null || !payload.content.equals(content)) {
            var buf = PacketByteBufs.create();
            try {
                buf.writeInt(content.size());
                content.forEach((id, json) -> { buf.writeString(id, 256); buf.writeString(json, 2000000); });
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                payload = new Payload(Map.copyOf(content), bytes);
                ENCODED.put(channel, payload);
            } finally { buf.release(); }
        }
        Key key = new Key(player.getUuid(), channel);
        Sent previous = SENT.get(key);
        if (!force && previous != null && previous.player == player && previous.connection == player.networkHandler
                && previous.payload == payload) return;
        var out = PacketByteBufs.create();
        out.writeBytes(payload.bytes);
        ServerPlayNetworking.send(player, channel, out);
        SENT.put(key, new Sent(player, player.networkHandler, payload));
    }
}
