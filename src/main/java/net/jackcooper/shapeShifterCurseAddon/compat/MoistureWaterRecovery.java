package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.CustomWaterBreathingPower;

/**
 * SSC's moisture drain treats only fully submerged players as "in water".
 * A player standing or crawling in shallow water is touching water but not
 * submerged, so SSC keeps applying the land drain and the moisture bar sticks
 * at zero / one bubble. Restore the pre-tick air plus normal water recovery
 * when any water contact is present.
 */
public final class MoistureWaterRecovery {
    private static final int WATER_RECOVERY = 4;
    // The integrated server and local client share a UUID but are different entities.
    // Per-entity storage also avoids boxed values and map-node allocation every tick.
    private static volatile long epoch;

    private MoistureWaterRecovery() { }

    public static void capture(PlayerEntity player) {
        if (player != null) ((MoistureTickSnapshot) player).ssca$moistureBefore(epoch, player.getAir());
    }

    /** 显式支付湿润度，同时校正浅水恢复的tick起点，避免恢复逻辑退回这笔消耗。 */
    public static boolean consume(PlayerEntity player,int amount) {
        if(player==null||player.getWorld().isClient||amount<0||player.getAir()<amount)return false;
        if(amount==0)return true;
        player.setAir(player.getAir()-amount);
        var snapshot = (MoistureTickSnapshot) player;
        int before = snapshot.ssca$moistureBefore(epoch);
        if (before != Integer.MIN_VALUE) snapshot.ssca$moistureBefore(epoch, Math.max(0, before - amount));
        return true;
    }

    public static void restore(PlayerEntity player) {
        if (player == null) return;
        var snapshot = (MoistureTickSnapshot) player;
        int before = snapshot.ssca$moistureBefore(epoch);
        snapshot.ssca$moistureBefore(epoch, Integer.MIN_VALUE);
        if (before == Integer.MIN_VALUE || player.getAir() >= player.getMaxAir()) return;
        if (!player.isTouchingWater() && !player.isInsideWaterOrBubbleColumn()) return;
        // Fully submerged players already take SSC's water branch.
        if (player.isSubmergedInWater()) return;
        if (!hasActiveWaterBreathing(player)) return;
        int target = Math.min(player.getMaxAir(), before + WATER_RECOVERY);
        if (target > player.getAir()) player.setAir(target);
    }

    public static void clear() {
        epoch++;
    }

    private static boolean hasActiveWaterBreathing(PlayerEntity player) {
        return PowerHolderComponent.getPowers(player, CustomWaterBreathingPower.class).stream()
                .anyMatch(Power::isActive);
    }
}
