package net.onixary.shapeShifterCurseFabric.ssc_addon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PhantomBellTeleportAction {

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(PhantomBellTeleportAction.class);

	private PhantomBellTeleportAction() {
        // This utility class should not be instantiated
    }

    // 检测怪物和玩家的范围
    private static final double DETECTION_RADIUS = 20.0;
    // 传送最大距离（半径）
    private static final int TP_RADIUS = 5;

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
            new Identifier("ssc_addon", "phantom_bell_teleport"),
            new SerializableData(),
            (data, entity) -> {
                log.info("[PhantomBell] Action triggered!");
                
                if (!(entity instanceof LivingEntity player)) {
                    log.info("[PhantomBell] Entity is not LivingEntity, returning");
                    return;
                }

	            log.info("[PhantomBell] Player: {}", player.getName().getString());
                
                World world = player.getWorld();
                BlockPos startBlockPos = player.getBlockPos();

                // 1. 获取20格内的敌对生物和其它玩家
                List<LivingEntity> threats = world.getEntitiesByClass(
                    LivingEntity.class,
                    player.getBoundingBox().expand(DETECTION_RADIUS),
                    e -> {
                        if (e == player) return false; // 排除自己
                        // 敌对生物
                        if (e instanceof HostileEntity) return true;
                        // 正在攻击玩家的生物
                        if (e.getAttacking() == player) return true;
                        // 其它玩家
	                    return e instanceof PlayerEntity;
                    }
                );

                // 2. 寻找候选点（5格球形半径内）
                List<BlockPos> candidates = new ArrayList<>();

                for (int x = -TP_RADIUS; x <= TP_RADIUS; x++) {
                    for (int y = -TP_RADIUS; y <= TP_RADIUS; y++) {
                        for (int z = -TP_RADIUS; z <= TP_RADIUS; z++) {
                            // 限制为球形范围
                            if (x * x + y * y + z * z > TP_RADIUS * TP_RADIUS) continue;

                            BlockPos pos = startBlockPos.add(x, y, z);
                            
                            // 检查基本有效性
                            if (isValidSpot(world, pos)) {
                                candidates.add(pos);
                            }
                        }
                    }
                }

                if (candidates.isEmpty()) return; // 无处可逃

                // 3. 评分系统
                BlockPos bestPos = null;
                double maxScore = Double.NEGATIVE_INFINITY;

                for (BlockPos pos : candidates) {
                    double score = calculateScore(world, pos, startBlockPos, threats);
                    
                    if (score > maxScore) {
                        maxScore = score;
                        bestPos = pos;
                    }
                }

                // 4. 执行传送
                if (bestPos != null) {
                    player.teleport(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5);
                }
            }
        );
    }

    /**
     * 计算候选点的评分
     */
    private static double calculateScore(World world, BlockPos pos, BlockPos startPos, List<LivingEntity> threats) {
        double score = 0.0;

        // ===== 最高优先级：离威胁（怪物和玩家）越远越好 =====
        double minDistanceToThreat = Double.MAX_VALUE;
        if (threats.isEmpty()) {
            minDistanceToThreat = DETECTION_RADIUS;
        } else {
            for (LivingEntity threat : threats) {
                double dist = Math.sqrt(threat.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
                if (dist < minDistanceToThreat) {
                    minDistanceToThreat = dist;
                }
            }
        }
        // 最高权重：离威胁越远越好
        score += minDistanceToThreat * 200.0;

        // ===== 次高优先级：距离原点越远越好 =====
        double distanceToStart = Math.sqrt(pos.getSquaredDistance(startPos));
        score += distanceToStart * 50.0;

        // ===== 高度差惩罚（越小越好） =====
        double heightDiff = Math.abs(pos.getY() - startPos.getY());
        score -= heightDiff * 20.0;

        // ===== 悬崖检测（简化版：只检查5x5范围） =====
        int cliffScore = calculateSimpleCliffScore(world, pos);
        score += cliffScore * 3.0;

        // ===== 流体检测（简化版） =====
        int fluidPenalty = calculateSimpleFluidPenalty(world, pos);
        score -= fluidPenalty * 10.0;

        return score;
    }

    /**
     * 检查候选点是否有效（简化版）
     */
    private static boolean isValidSpot(World world, BlockPos pos) {
        BlockState floorState = world.getBlockState(pos.down());
        
        // 1. 脚下必须是实体方块
        if (!floorState.isSolidBlock(world, pos.down())) return false;

        // 2. 身体位置必须是空气且无流体
        BlockState stateBody = world.getBlockState(pos);
        if (stateBody.isSolidBlock(world, pos) || !stateBody.getFluidState().isEmpty()) return false;

        // 3. 头部位置必须是空气（防止窒息）
        BlockState stateHead = world.getBlockState(pos.up());
        if (stateHead.isSolidBlock(world, pos.up()) || !stateHead.getFluidState().isEmpty()) return false;

        // 4. 可掉落方块检测（沙子、砂砾等）- 简化：只检查脚下
        Block floorBlock = floorState.getBlock();
        if (floorBlock instanceof FallingBlock) {
            // 简化：如果脚下是沙子，检查周围3x3是否至少50%也是沙子
            int fallingCount = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (world.getBlockState(pos.add(dx, -1, dz)).getBlock() instanceof FallingBlock) {
                        fallingCount++;
                    }
                }
            }
            // 如果周围沙子不够多（<50%），排除
	        return fallingCount >= 5;
        }

        return true;
    }

    /**
     * 简化版悬崖检测（5x5范围）
     */
    private static int calculateSimpleCliffScore(World world, BlockPos pos) {
        int score = 0;
        
        // 检查5x5范围内的地面稳固性
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floorPos = pos.add(dx, -1, dz);
                if (world.getBlockState(floorPos).isSolidBlock(world, floorPos)) {
                    score++;
                } else {
                    // 检查下方是否有深渊
                    if (!world.getBlockState(floorPos.down()).isSolidBlock(world, floorPos.down()) &&
                        !world.getBlockState(floorPos.down(2)).isSolidBlock(world, floorPos.down(2))) {
                        score -= 3; // 悬崖惩罚
                    }
                }
            }
        }
        
        return score;
    }

    /**
     * 简化版流体检测（5x5x3范围）
     */
    private static int calculateSimpleFluidPenalty(World world, BlockPos pos) {
        int penalty = 0;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(checkPos);
                    
                    if (!state.getFluidState().isEmpty()) {
                        if (state.getBlock() == Blocks.LAVA) {
                            penalty += 5; // 岩浆高惩罚
                        } else {
                            penalty += 1; // 水
                        }
                    }
                }
            }
        }
        
        return penalty;
    }
}
