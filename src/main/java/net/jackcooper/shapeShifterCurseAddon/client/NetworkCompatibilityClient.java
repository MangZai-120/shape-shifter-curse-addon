package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.mixin.networking.client.accessor.ClientLoginNetworkHandlerAccessor;
import net.jackcooper.shapeShifterCurseAddon.network.NetworkCompatibility;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.Registries;

/** Per-connection login confirmation, with no tick polling or PLAY canSend timing assumptions. */
@Environment(EnvType.CLIENT)
public final class NetworkCompatibilityClient {
    private static final Set<ClientConnection> CONFIRMED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static boolean initialized;

    private NetworkCompatibilityClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientLoginConnectionEvents.INIT.register((handler, client) -> CONFIRMED.remove(connection(handler)));
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> CONFIRMED.remove(connection(handler)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CONFIRMED.remove(handler.getConnection()));
        ClientLoginNetworking.registerGlobalReceiver(NetworkCompatibility.QUERY, (client, handler, buffer, listeners) -> {
            ClientConnection connection = connection(handler);
            if (NetworkCompatibility.readRevision(buffer) != NetworkCompatibility.ENTITY_WIRE_REVISION) {
                CONFIRMED.remove(connection);
                connection.disconnect(NetworkCompatibility.serverUpdateRequired());
                return CompletableFuture.completedFuture(null);
            }
            CONFIRMED.add(connection);
            var response = PacketByteBufs.create();
            NetworkCompatibility.writeRevision(response);
            return CompletableFuture.completedFuture(response);
        });
        // This event fires on the actual registration packet, so a late channel advertisement
        // is never mistaken for a missing server mod at JOIN. No SSCA channels means no restriction.
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            boolean serverHasSsca = channels.stream().anyMatch(id -> NetworkCompatibility.isSscaNamespace(id.getNamespace()));
            if (NetworkCompatibility.rejectUnconfirmedServer(CONFIRMED.contains(handler.getConnection()), serverHasSsca))
                handler.getConnection().disconnect(NetworkCompatibility.serverUpdateRequired());
        });
    }

    /**
     * Called on the client thread before applying an SSCA entity's metadata. This closes the
     * ordering gap if an old server sends metadata before its channel registration packet.
     * Vanilla/other-mod entities and servers without SSCA remain unrestricted.
     */
    public static boolean allowEntityMetadata(ClientConnection connection, Entity entity) {
        return entity == null || allowEntityType(connection, entity.getType());
    }

    /** The same check before constructing a remote SSCA entity, without consulting mutable world state. */
    public static boolean allowEntityType(ClientConnection connection, EntityType<?> type) {
        if (!NetworkCompatibility.isSscaNamespace(Registries.ENTITY_TYPE.getId(type).getNamespace())) return true;
        if (!NetworkCompatibility.rejectUnconfirmedServer(CONFIRMED.contains(connection), true)) return true;
        connection.disconnect(NetworkCompatibility.serverUpdateRequired());
        return false;
    }

    public static boolean isConfirmed(ClientConnection connection) {
        return CONFIRMED.contains(connection);
    }

    private static ClientConnection connection(ClientLoginNetworkHandler handler) {
        return ((ClientLoginNetworkHandlerAccessor) handler).getConnection();
    }
}
