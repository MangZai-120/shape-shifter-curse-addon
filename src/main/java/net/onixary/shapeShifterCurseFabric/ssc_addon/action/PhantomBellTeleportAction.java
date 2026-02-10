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

import java.util.ArrayList;
import java.util.List;

public class PhantomBellTeleportAction {

    // 检测怪物和玩家的范围
    private static final double DETECTION_RADIUS = 20.0;
    // 传送最大距离（半径）
    private static final int TP_RADIUS = 5;

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
            new Identifier("ssc_addon", "phantom_bell_teleport"),
            new SerializableData(),
            (data, entity) -> {
                if (!(entity instanceof LivingEntity player)) return;

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
                        if (e instanceof PlayerEntity) return true;
                        return false;
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
                            if (isValidSpot(world, pos, startBlockPos)) {
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

        // ===== 0. 距离原点越远越好（最高优先级：必须尽可能远离原点） =====
        double distanceToStart = Math.sqrt(pos.getSquaredDistance(startPos));
        // 给距离一个非常高的权重，确保优先选择最远的位置
        score += distanceToStart * 100.0;

        // ===== 1. 离威胁（怪物和玩家）越远越好 =====
        double minDistanceToThreat = Double.MAX_VALUE;
        if (threats.isEmpty()) {
            minDistanceToThreat = DETECTION_RADIUS; // 没有威胁时固定值
        } else {
            for (LivingEntity threat : threats) {
                double dist = Math.sqrt(threat.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
                if (dist < minDistanceToThreat) {
                    minDistanceToThreat = dist;
                }
            }
        }
        // 距离加分（权重高）
        score += minDistanceToThreat * 10.0;

        // ===== 2. 高度差惩罚（越小越好） =====
        double heightDiff = Math.abs(pos.getY() - startPos.getY());
        score -= heightDiff * 30.0; // 高权重惩罚高度差

        // ===== 3. 悬崖检测（周围地面越稳固越好） =====
        int cliffScore = calculateCliffScore(world, pos);
        score += cliffScore * 5.0;

        // ===== 4. 大片流体检测（周围流体越少越好） =====
        int fluidPenalty = calculateFluidPenalty(world, pos);
        score -= fluidPenalty * 8.0;

        // ===== 5. 空间开阔度（3x3x3空间越开阔越好） =====
        int spaceScore = calculate3x3x3SpaceScore(world, pos);
        score += spaceScore * 3.0;

        return score;
    }

    /**
     * 检查候选点是否有效
     */
    private static boolean isValidSpot(World world, BlockPos pos, BlockPos startPos) {
        BlockState floorState = world.getBlockState(pos.down());
        
        // 1. 脚下必须是实体方块
        if (!floorState.isSolidBlock(world, pos.down())) return false;

        // 2. 身体位置必须是空气（或可替换）且无流体
        BlockState stateBody = world.getBlockState(pos);
        if (stateBody.isSolidBlock(world, pos) || !stateBody.getFluidState().isEmpty()) return false;

        // 3. 头部位置必须是空气（防止窒息）
        BlockState stateHead = world.getBlockState(pos.up());
        if (stateHead.isSolidBlock(world, pos.up()) || !stateHead.getFluidState().isEmpty()) return false;

        // 4. 3x3x3空间检测：不能几乎全被方块填满
        int solidCount = 0;
        int totalCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) { // 从脚到头上方
                for (int dz = -1; dz <= 1; dz++) {
                    totalCount++;
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    if (world.getBlockState(checkPos).isSolidBlock(world, checkPos)) {
                        solidCount++;
                    }
                }
            }
        }
        // 如果超过70%被方块填满，认为太拥挤
        if ((double)solidCount / totalCount > 0.7) return false;

        // 5. 可掉落方块检测（沙子、砂砾等）
        Block floorBlock = floorState.getBlock();
        if (floorBlock instanceof FallingBlock) {
            // 检查周围是否全是可掉落方块
            int fallingBlockCount = 0;
            int totalFloorBlocks = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    totalFloorBlocks++;
                    BlockPos floorCheckPos = pos.add(dx, -1, dz);
                    Block checkBlock = world.getBlockState(floorCheckPos).getBlock();
                    if (checkBlock instanceof FallingBlock) {
                        fallingBlockCount++;
                    }
                }
            }
            // 如果周围不全是可掉落方块（<80%），则排除这个点
            if ((double)fallingBlockCount / totalFloorBlocks < 0.8) {
                return false;
            }
            // 否则（周围全是沙子），允许传送
        }

        return true;
    }

    // 悬崖检测半径
    private static final int CLIFF_DETECTION_RADIUS = 10;

    /**
     * 计算悬崖评分（周围地面越稳固分数越高，检测10格半径球形区域）
     */
    private static int calculateCliffScore(World world, BlockPos pos) {
        int score = 0;
        int solidGroundCount = 0;
        int voidCount = 0;
        
        // 检查10格半径球形区域内的地面稳固性
        for (int dx = -CLIFF_DETECTION_RADIUS; dx <= CLIFF_DETECTION_RADIUS; dx++) {
            for (int dy = -CLIFF_DETECTION_RADIUS; dy <= CLIFF_DETECTION_RADIUS; dy++) {
                for (int dz = -CLIFF_DETECTION_RADIUS; dz <= CLIFF_DETECTION_RADIUS; dz++) {
                    // 限制为球形范围
                    if (dx * dx + dy * dy + dz * dz > CLIFF_DETECTION_RADIUS * CLIFF_DETECTION_RADIUS) continue;
                    
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    
                    // 检查地面方块（dy <= 0 的区域）
                    if (dy <= 0) {
                        if (world.getBlockState(checkPos).isSolidBlock(world, checkPos)) {
                            solidGroundCount++;
                        }
                        // 检查是否是深渊/悬崖（连续的空气）
                        if (!world.getBlockState(checkPos).isSolidBlock(world, checkPos) &&
                            !world.getBlockState(checkPos.down()).isSolidBlock(world, checkPos.down()) &&
                            !world.getBlockState(checkPos.down(2)).isSolidBlock(world, checkPos.down(2))) {
                            voidCount++;
                        }
                    }
                }
            }
        }
        
        // 评分：实心地面越多越好，深渊/悬崖越少越好
        score = solidGroundCount - (voidCount * 3);
        
        return score;
    }

    /**
     * 计算流体惩罚（周围20格内的流体越多惩罚越高）
     */
    private static int calculateFluidPenalty(World world, BlockPos pos) {
        int penalty = 0;
        
        // 检查7x7x5范围内的流体
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(checkPos);
                    
                    if (!state.getFluidState().isEmpty()) {
                        // 岩浆惩罚更高
                        if (state.getBlock() == Blocks.LAVA) {
                            penalty += 3;
                        } else {
                            penalty += 1; // 水
                        }
                    }
                }
            }
        }
        
        return penalty;
    }

    /**
     * 计算3x3x3空间开阔度（越开阔分数越高）
     */
    private static int calculate3x3x3SpaceScore(World world, BlockPos pos) {
        int airCount = 0;
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(checkPos);
                    
                    if (!state.isSolidBlock(world, checkPos) && state.getFluidState().isEmpty()) {
                        airCount++;
                    }
                }
            }
        }
        
        return airCount;
    }
}
