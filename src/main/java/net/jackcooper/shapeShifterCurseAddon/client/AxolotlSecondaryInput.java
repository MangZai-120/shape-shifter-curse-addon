package net.jackcooper.shapeShifterCurseAddon.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.onixary.shapeShifterCurseFabric.client.ClientPlayerStateManager;

/** Apoli polls at START_CLIENT_TICK, before vanilla updates/sends the sneak input for this tick. */
public final class AxolotlSecondaryInput {
    private static ClientPlayerEntity owner;
    private static boolean wasPressed, pending, desiredSneak, emittedForHold;
    private static long deadline, pulseTick = Long.MIN_VALUE;
    private AxolotlSecondaryInput() { }

    public static boolean filter(boolean pressed) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.world == null) { reset(); return false; }
        if (owner != player) { reset(); owner = player; }
        long now = client.world.getTime();
        if (pressed && !wasPressed) {
            desiredSneak = client.options.sneakKey.isPressed() || ClientPlayerStateManager.shouldForceSneak;
            pending = true;
            deadline = now + 3;
            emittedForHold = false;
        }
        wasPressed = pressed;
        if (pending) {
            if (now > deadline) pending = false;
            else if (player.isSneaking() == desiredSneak) {
                pending = false;
                emittedForHold = pressed;
                pulseTick = now;
                return true;
            } else return false;
        }
        // Releasing sneak while holding the skill must not rearm its edge and select Play Dead.
        if (emittedForHold && pressed) return true;
        if (!pressed) emittedForHold = false;
        return pulseTick == now;
    }

    public static void reset() {
        owner = null;
        wasPressed = pending = desiredSneak = emittedForHold = false;
        pulseTick = Long.MIN_VALUE;
    }
}
