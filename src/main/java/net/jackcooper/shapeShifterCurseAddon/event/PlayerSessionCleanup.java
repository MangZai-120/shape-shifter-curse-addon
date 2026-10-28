package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** Main-thread, once-per-connection cleanup, also invoked before disconnect player data is saved. */
public final class PlayerSessionCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger("ssc_addon/session_cleanup");
    private static final List<BiConsumer<ServerPlayNetworkHandler, MinecraftServer>> CALLBACKS = new ArrayList<>();
    private static final Set<ServerPlayNetworkHandler> CLEANED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private PlayerSessionCleanup() {}

    public static void register(BiConsumer<ServerPlayNetworkHandler, MinecraftServer> callback) {
        CALLBACKS.add(callback);
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register(PlayerSessionCleanup::disconnect);
        // Minecraft saves players before disconnectAllPlayers during shutdown.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var player : List.copyOf(server.getPlayerManager().getPlayerList())) {
                disconnect(player.networkHandler, server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLEANED.clear());
    }

    public static void disconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        if (!server.isOnThread()) {
            server.execute(() -> disconnect(handler, server));
            return;
        }
        var online = server.getPlayerManager().getPlayer(handler.player.getUuid());
        if (online != null && online.networkHandler != handler) return;
        if (!CLEANED.add(handler)) return;
        for (var callback : CALLBACKS) {
            try {
                callback.accept(handler, server);
            } catch (RuntimeException exception) {
                LOGGER.error("Player cleanup failed for {} in {}; continuing remaining cleanup",
                        handler.player.getUuid(), callback.getClass().getName(), exception);
            }
        }
    }
}
