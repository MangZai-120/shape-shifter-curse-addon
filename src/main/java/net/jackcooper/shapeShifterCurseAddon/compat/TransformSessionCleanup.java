package net.jackcooper.shapeShifterCurseAddon.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.mixin.TransformManagerAccessor;

/** SSC 1.10 keeps finished transform entries alive until the next server start. */
public final class TransformSessionCleanup {
    private TransformSessionCleanup() {}

    public static void init() {
        // PlayerSessionCleanup verifies this is the current connection before invoking us.
        // Removing the current UUID also covers an old entity retained through respawn.
        PlayerSessionCleanup.register((handler, server) ->
                TransformManagerAccessor.ssca$playerData().remove(handler.player.getUuid()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var entries = TransformManagerAccessor.ssca$playerData();
            var previous = entries.get(handler.player.getUuid());
            if (previous != null && previous.player != handler.player)
                entries.remove(handler.player.getUuid(), previous);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                TransformManagerAccessor.ssca$playerData().clear());
    }
}
