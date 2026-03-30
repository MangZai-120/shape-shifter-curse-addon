package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BlueFireRingEffect extends StatusEffect {

    // 冻结水面的概率（6%，每次火环攻击间隔触发）
    private static final float FREEZE_CHANCE = 0.06f;
    // 冻结半径（与火环area_of_effect半径一致：6格）
    private static final int FREEZE_RADIUS = 6;
    // 火环攻击间隔：effects_loop interval=4 × internal_timer阈值4 = 16tick
    private static final int ATTACK_INTERVAL = 16;

    public BlueFireRingEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x3366FF);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // 与火环攻击间隔一致，每16tick触发一次
        return duration % ATTACK_INTERVAL == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        World world = entity.getWorld();
        if (world.isClient()) return;

        BlockPos center = entity.getBlockPos();
        ServerWorld serverWorld = (ServerWorld) world;

        // 遍历周围方块，有概率将水源方块转为冰霜行者冰（范围与火环攻击范围一致）
        for (int x = -FREEZE_RADIUS; x <= FREEZE_RADIUS; x++) {
            for (int z = -FREEZE_RADIUS; z <= FREEZE_RADIUS; z++) {
                // 圆形范围检查
                if (x * x + z * z > FREEZE_RADIUS * FREEZE_RADIUS) continue;

                // 检查脚下及脚下一格位置的水
                for (int yOff = -1; yOff <= 0; yOff++) {
                    BlockPos pos = center.add(x, yOff, z);

                    if (world.getBlockState(pos).isOf(Blocks.WATER)
                            && world.getFluidState(pos).isStill()
                            && world.getBlockState(pos.up()).isAir()) {

                        if (world.getRandom().nextFloat() < FREEZE_CHANCE) {
                            world.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState());
                            // 调度冰块融化（60-120tick后开始）
                            serverWorld.scheduleBlockTick(
                                    pos, Blocks.FROSTED_ICE,
                                    MathHelper.nextInt(world.getRandom(), 60, 120));
                        }
                    }
                }
            }
        }
    }
}
