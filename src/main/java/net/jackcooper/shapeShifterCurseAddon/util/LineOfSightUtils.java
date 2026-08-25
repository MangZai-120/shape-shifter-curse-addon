package net.jackcooper.shapeShifterCurseAddon.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * 战斗视线判定工具（仿原版 PR #523 豹猫冲刺穿墙修复，按最新 commit 5c6b842e 对齐）。
 * <p>
 * 原版 `SneakingJumpClashPower` 的修复方式（最新版）：从施法者身体中心到目标身体中心
 * 做一次 `COLLIDER` 方块射线（不处理流体），射线先撞到方块（BLOCK）则视为被遮挡，
 * 跳过该目标——避免冲刺/撞击类 AOE 隔着墙命中墙后生物。
 * <p>
 * 本类把同一逻辑抽成公共工具，供 SSCA 豹猫系（风灵/朔望）各冲刺/撞击
 * AOE 伤害点复用；起点/终点均取身体中心（getBodyY 0.5），与 PR 最新版逐字一致。
 */
public final class LineOfSightUtils {
    private LineOfSightUtils() {
    }

    /**
     * 判定施法者到目标之间是否有视线（无方块遮挡）。与 PR #523 最新版一致：
     * 起点/终点取身体中心（getBodyY 0.5），ShapeType 用 COLLIDER，FluidHandling NONE。
     *
     * @param world     服务端世界（仅服务端调用）
     * @param context   施法者实体（射线起点取身体中心）
     * @param target    目标实体（射线终点取身体中心）
     * @return true = 视线畅通可命中；false = 被方块遮挡应跳过
     */
    public static boolean hasLineOfSight(World world, Entity context, Entity target) {
        if (world == null || target == null || context == null) {
            return true;
        }
        Vec3d from = new Vec3d(context.getX(), context.getBodyY(0.5f), context.getZ());
        Vec3d to = new Vec3d(target.getX(), target.getBodyY(0.5f), target.getZ());
        HitResult hit = world.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                context));
        return hit.getType() != HitResult.Type.BLOCK;
    }
}
