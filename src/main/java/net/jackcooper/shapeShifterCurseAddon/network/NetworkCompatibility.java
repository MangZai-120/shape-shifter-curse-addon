package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A single login query protects the SSCA entity metadata layout before entering PLAY. */
public final class NetworkCompatibility {
    public static final Identifier QUERY = new Identifier("ssc_addon", "entity_wire_revision");
    public static final int ENTITY_WIRE_REVISION = 2;
    public static final String FIRST_COMPATIBLE_VERSION = "8.0.0-neomagic.69";
    private static final ConcurrentMap<ServerLoginNetworkHandler, CompletableFuture<Void>> PENDING = new ConcurrentHashMap<>();
    private static boolean initialized;

    private NetworkCompatibility() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerLoginNetworking.registerGlobalReceiver(QUERY, (server, handler, understood, buffer, synchronizer, sender) -> {
            CompletableFuture<Void> validation = PENDING.remove(handler);
            if (validation != null && accepts(understood, buffer)) {
                validation.complete(null);
                return;
            }
            // The gate was installed before sending, so removing Fabric's outstanding query
            // cannot let a server tick enter PLAY while this Netty callback is still running.
            server.execute(() -> {
                handler.disconnect(clientUpdateRequired());
                if (validation != null) validation.complete(null);
            });
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var validation = new CompletableFuture<Void>();
            PENDING.put(handler, validation);
            synchronizer.waitFor(validation);
            var query = PacketByteBufs.create();
            writeRevision(query);
            sender.sendPacket(QUERY, query);
        });
        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> {
            CompletableFuture<Void> validation = PENDING.remove(handler);
            if (validation != null) validation.cancel(false);
        });
    }

    public static void writeRevision(PacketByteBuf buffer) {
        buffer.writeVarInt(ENTITY_WIRE_REVISION);
    }

    /** A frame has exactly one positive revision and no optional/trailing data. */
    public static int readRevision(PacketByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 1 || buffer.readableBytes() > 5) return -1;
        try {
            int revision = buffer.readVarInt();
            return revision > 0 && !buffer.isReadable() ? revision : -1;
        } catch (RuntimeException malformed) {
            return -1;
        }
    }

    public static boolean accepts(boolean understood, PacketByteBuf buffer) {
        return understood && readRevision(buffer) == ENTITY_WIRE_REVISION;
    }

    /** Missing SSCA on the remote server is distinct from an unnegotiated SSCA server. */
    public static boolean rejectUnconfirmedServer(boolean serverConfirmed, boolean serverHasSsca) {
        return serverHasSsca && !serverConfirmed;
    }

    public static boolean isSscaNamespace(String namespace) {
        return namespace.equals("ssc_addon") || namespace.equals("my_addon") || namespace.equals("ssca_neomagic");
    }

    public static Text clientUpdateRequired() {
        return Text.translatableWithFallback("message.ssc_addon.network.update_client",
                "SSCA client is incompatible. Update the client to %s or a compatible newer build (entity protocol %s). / 客户端版本不兼容，请更新 SSCA。",
                FIRST_COMPATIBLE_VERSION, ENTITY_WIRE_REVISION);
    }

    public static Text serverUpdateRequired() {
        return Text.translatableWithFallback("message.ssc_addon.network.update_server",
                "SSCA server is incompatible. Update the server to %s or a compatible newer build (entity protocol %s). / 服务器版本不兼容，请更新 SSCA。",
                FIRST_COMPATIBLE_VERSION, ENTITY_WIRE_REVISION);
    }
}
