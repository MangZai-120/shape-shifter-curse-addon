package net.jackcooper.shapeShifterCurseAddon.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/** Revalidate queued play requests on the server thread before touching player-owned state. */
public final class ServerPacketExecutor {
    private ServerPacketExecutor() {}

    public static void execute(MinecraftServer server, ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler, Runnable action) {
        execute(server, player, handler, true, action);
    }

    /** Pure input resets may follow the same living session across a teleport; casts may not. */
    public static void execute(MinecraftServer server, ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler, boolean worldBound, Runnable action) {
        var world = player.getWorld();
        server.execute(() -> {
            // UUID alone is insufficient: reconnect/respawn replaces the player, and
            // a respawn can reuse the same handler. A closed connection may still be
            // in PlayerManager until its disconnect task runs.
            if (handler.player != player || player.networkHandler != handler
                    || !handler.isConnectionOpen() || player.isRemoved()
                    || worldBound && player.getWorld() != world
                    || server.getPlayerManager().getPlayer(player.getUuid()) != player) return;
            action.run();
        });
    }
}
