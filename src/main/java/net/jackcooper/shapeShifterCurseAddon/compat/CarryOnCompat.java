package net.jackcooper.shapeShifterCurseAddon.compat;

import net.minecraft.entity.player.PlayerEntity;

/** Optional client bridge, also usable with SSC builds predating its Carry On integration. */
public final class CarryOnCompat {
    private CarryOnCompat() {}

    public static boolean isInCarryingAnimation(PlayerEntity player) {
        // Replaced by the client mixin only when Carry On is installed.
        return false;
    }
}
