package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 风灵被动「风压领域」。
 *
 * <p>风灵周围 8 格半径内，<b>严格白名单外的生物发射的弹射物</b>（箭矢/火球/三叉戟等）
 * 速度降低 30%（×0.7）。白名单内的生物（玩家、驯服宠物、召唤物）发射的弹射物不受影响。
 * 风灵本人发射的弹射物也不受影响（owner==风灵）。
 *
 * <p>判定逻辑由 {@link WindSpiritProjectilePressureMixin}（拦 {@code ProjectileEntity.tick}）调用本类的
 * {@link #tryApplySlow(ProjectileEntity)}，每枚弹射物只处理一次（用 age + command tag 控制）。
 */
public final class WindSpiritWindPressureManager {

    private static final double RANGE = 8.0;
    private static final double SLOW_FACTOR = 0.7;
    /** 弹射物发射后前 N tick 内应用减速（超过则不再处理）。 */
    private static final int APPLY_WITHIN_AGE = 5;
    /** 已减速标记（防反复减速）。 */
    static final String SLOWED_TAG = "ssc_addon_wind_slowed";

    private WindSpiritWindPressureManager() {
    }

    /**
     * 由弹射物 tick mixin 调用：检查并应用风压减速。
     * 返回 true 表示已应用减速（mixin 可据此做额外处理）。
     */
    public static boolean tryApplySlow(ProjectileEntity projectile) {
        // 仅服务端处理（多人一致）
        if (projectile.getWorld().isClient()) return false;
        if (!(projectile.getWorld() instanceof ServerWorld world)) return false;

        // 发射后超过窗口期不再处理
        if (projectile.age > APPLY_WITHIN_AGE) return false;
        // 已减速过则跳过
        if (projectile.getCommandTags().contains(SLOWED_TAG)) return false;

        Entity ownerEntity = projectile.getOwner();
        if (ownerEntity == null) return false;

        // 寻找范围内是否有风灵
        Box checkBox = projectile.getBoundingBox().expand(RANGE);
        ServerPlayerEntity windSpirit = findWindSpiritInRange(world, projectile, checkBox);
        if (windSpirit == null) return false;

        // 风灵本人发射的弹射物不受影响
        if (ownerEntity == windSpirit) return false;

        // 严格白名单：owner 是受保护的（玩家/宠物/召唤物）则不减速
        if (ownerEntity instanceof LivingEntity ownerLiving) {
            if (WhitelistUtils.isProtected(windSpirit, ownerLiving)) return false;
        }

        // 应用减速
        Vec3d v = projectile.getVelocity();
        projectile.setVelocity(v.multiply(SLOW_FACTOR));
        projectile.velocityModified = true;
        projectile.addCommandTag(SLOWED_TAG);
        return true;
    }

    /** 在范围内找一个风灵玩家。 */
    private static ServerPlayerEntity findWindSpiritInRange(ServerWorld world, Entity projectile, Box box) {
        for (Entity e : world.getOtherEntities(projectile, box)) {
            if (!(e instanceof ServerPlayerEntity sp)) continue;
            if (!FormUtils.isOcelotSP(sp)) continue;
            if (e.squaredDistanceTo(projectile) <= RANGE * RANGE) {
                return sp;
            }
        }
        return null;
    }
}

