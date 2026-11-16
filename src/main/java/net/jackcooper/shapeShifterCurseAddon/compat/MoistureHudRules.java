package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.CustomWaterBreathingPower;

/** The same moisture-bar visibility rule for the vanilla and Forge HUDs. */
public final class MoistureHudRules {
    private MoistureHudRules() {}

    public static boolean requiresBar(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, CustomWaterBreathingPower.class)
                .stream().anyMatch(Power::isActive);
    }
}
