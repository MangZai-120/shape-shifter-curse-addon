package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.KeepSneakingPower;

/** Evaluate the existing SSC powers, including their explicit water exemption. */
public final class SscForceSneak {
    private SscForceSneak() { }

    public static boolean required(PlayerEntity player) {
        return player != null && player.isAlive() && !player.isRemoved()
                && PowerHolderComponent.getPowers(player, KeepSneakingPower.class).stream()
                .anyMatch(power -> power.isActive() && power.shouldForceSneak(player));
    }
}
