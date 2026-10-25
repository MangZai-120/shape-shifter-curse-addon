package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.compat.SscForceSneak;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.onixary.shapeShifterCurseFabric.client.ClientPlayerStateManager;

/** Keep the server request separate from the locally applicable input override. */
public final class SscForceSneakClient {
    private static boolean requested;
    private static ClientPlayerEntity settledOwner;
    private static boolean settledRequired;
    private SscForceSneakClient() { }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            settledOwner = client.player;
            settledRequired = SscForceSneak.required(client.player);
        });
    }

    public static boolean receive(boolean force) {
        requested = force;
        return applicable();
    }

    public static void refresh() {
        ClientPlayerStateManager.shouldForceSneak = applicable();
    }

    private static boolean applicable() {
        var player = MinecraftClient.getInstance().player;
        // Vanilla restores air before input; SSC cancels that restoration at the end of the tick.
        // Keep the last settled requirement for this player through that temporary air increase.
        return requested && (SscForceSneak.required(player)
                || player != null && player == settledOwner && settledRequired);
    }

    private static void reset() {
        requested = false;
        settledOwner = null;
        settledRequired = false;
        ClientPlayerStateManager.shouldForceSneak = false;
        AxolotlSecondaryInput.reset();
    }
}
