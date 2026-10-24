package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.compat.SscForceSneak;
import net.minecraft.client.MinecraftClient;
import net.onixary.shapeShifterCurseFabric.client.ClientPlayerStateManager;

/** Keep the server request separate from the locally applicable input override. */
public final class SscForceSneakClient {
    private static boolean requested;
    private SscForceSneakClient() { }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static boolean receive(boolean force) {
        requested = force;
        return applicable();
    }

    public static void refresh() {
        ClientPlayerStateManager.shouldForceSneak = applicable();
    }

    private static boolean applicable() {
        return requested && SscForceSneak.required(MinecraftClient.getInstance().player);
    }

    private static void reset() {
        requested = false;
        ClientPlayerStateManager.shouldForceSneak = false;
        AxolotlSecondaryInput.reset();
    }
}
