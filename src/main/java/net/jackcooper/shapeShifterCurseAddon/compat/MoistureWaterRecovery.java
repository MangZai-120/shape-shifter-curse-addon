package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.CustomWaterBreathingPower;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSC's moisture drain treats only fully submerged players as "in water".
 * A player standing or crawling in shallow water is touching water but not
 * submerged, so SSC keeps applying the land drain and the moisture bar sticks
 * at zero / one bubble. Restore the pre-tick air plus normal water recovery
 * when any water contact is present.
 */
public final class MoistureWaterRecovery {
    private static final int WATER_RECOVERY = 4;
    private static final Map<UUID, Integer> AIR_BEFORE_TICK = new ConcurrentHashMap<>();

    private MoistureWaterRecovery() { }

    public static void capture(PlayerEntity player) {
        if (player != null) AIR_BEFORE_TICK.put(player.getUuid(), player.getAir());
    }

    public static void restore(PlayerEntity player) {
        if (player == null) return;
        Integer before = AIR_BEFORE_TICK.remove(player.getUuid());
        if (before == null || player.getAir() >= player.getMaxAir()) return;
        if (!player.isTouchingWater() && !player.isInsideWaterOrBubbleColumn()) return;
        // Fully submerged players already take SSC's water branch.
        if (player.isSubmergedInWater()) return;
        if (!hasActiveWaterBreathing(player)) return;
        int target = Math.min(player.getMaxAir(), before + WATER_RECOVERY);
        if (target > player.getAir()) player.setAir(target);
    }

    public static void clear() {
        AIR_BEFORE_TICK.clear();
    }

    private static boolean hasActiveWaterBreathing(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, CustomWaterBreathingPower.class).stream()
                .anyMatch(Power::isActive);
    }
}
