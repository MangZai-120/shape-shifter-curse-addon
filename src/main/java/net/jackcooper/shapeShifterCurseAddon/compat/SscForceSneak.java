package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.KeepSneakingPower;

/** Evaluate the existing SSC powers, including their explicit water exemption. */
public final class SscForceSneak {
    private SscForceSneak() { }

    public static boolean required(PlayerEntity player) {
        if (player == null || !player.isAlive() || player.isRemoved()) return false;
        if (PowerHolderComponent.getPowers(player, KeepSneakingPower.class).stream()
                .anyMatch(power -> power.isActive() && power.shouldForceSneak(player))) {
            return true;
        }
        // Moisture forms use air as their wetness bar. SSC clamps exhausted air to 0/-1,
        // but the upgrade form never had the keep_sneaking power, and an origin/config
        // update can leave it missing. Dry land must still force the crawl pose.
        return player.getAir() <= 0
                && !player.isSubmergedInWater()
                && !player.isInsideWaterOrBubbleColumn()
                && FormUtils.isMoistureDependent(player);
    }
}
