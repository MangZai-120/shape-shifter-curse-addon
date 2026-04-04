package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;

/**
 * 金沙岚SP - 金沙终裁（大招）
 * 对周围15格内所有带有凋零效果的非白名单生物进行结算：
 * - 敌对目标：立即结算凋零效果，造成 剩余凋零时间(tick) * (等级+1) / 40 的真实伤害，并移除凋零
 * - 友方（白名单内）目标：同公式但转化为治疗量，并移除凋零
 * CD: 90秒（1800tick），使用自定义资源 GOLDEN_SANDSTORM_ULTIMATE_CD
 * 触发键：潜行+主技能键
 */
public class GoldenSandstormGoldenVerdict {

    private GoldenSandstormGoldenVerdict() {
    }

    // ==================== 常量 ====================
    /** AoE半径 */
    private static final double RADIUS = 15.0;
    /** CD时间（tick） */
    private static final int COOLDOWN_TICKS = 1800; // 90秒
    /** 伤害/治疗公式除数 */
    private static final float FORMULA_DIVISOR = 40.0f;

    /**
     * 玩家按下技能键触发
     */
    public static boolean execute(ServerPlayerEntity player) {
        // CD检查
        int cd = PowerUtils.getResourceValue(player, FormIdentifiers.GOLDEN_SANDSTORM_ULTIMATE_CD);
        if (cd > 0) {
            return false;
        }

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

        // 获取范围内所有带凋零的生物
        Box box = player.getBoundingBox().expand(RADIUS);
        List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive()
                        && e.hasStatusEffect(StatusEffects.WITHER)
                        && e.squaredDistanceTo(player) <= RADIUS * RADIUS);

        if (targets.isEmpty()) {
            // 没有带凋零的目标，不消耗CD
            return false;
        }

        // 设置CD
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.GOLDEN_SANDSTORM_ULTIMATE_CD, COOLDOWN_TICKS);

        // 释放音效
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.8f, 1.5f);
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.2f);

        // 释放粒子：巨大的金色冲击波
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(),
                100, RADIUS * 0.5, 2.0, RADIUS * 0.5, 0.1);
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                60, RADIUS * 0.3, 1.5, RADIUS * 0.3, 0.05);

        RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));

        for (LivingEntity target : targets) {
            StatusEffectInstance witherEffect = target.getStatusEffect(StatusEffects.WITHER);
            if (witherEffect == null) continue;

            int remainingTicks = witherEffect.getDuration();
            int amplifier = witherEffect.getAmplifier();
            float amount = remainingTicks * (amplifier + 1) / FORMULA_DIVISOR;

            // 移除凋零效果
            target.removeStatusEffect(StatusEffects.WITHER);

            boolean isProtected = WhitelistUtils.isProtected(player, target);

            if (isProtected) {
                // 友方：治疗
                target.heal(amount);

                // 治疗粒子
                ParticleUtils.spawnParticles(serverWorld, ParticleTypes.HEART,
                        target.getX(), target.getY() + target.getHeight(), target.getZ(),
                        5, 0.3, 0.3, 0.3, 0.1);
            } else {
                // 敌方：真实伤害
                Vec3d oldVelocity = target.getVelocity();
                if (target.damage(target.getDamageSources().create(magicKey, player, player), amount)) {
                    target.setVelocity(oldVelocity);
                }

                // 伤害粒子
                ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
                        target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1);
            }

            // 结算连线粒子（从施法者到目标）
            Vec3d from = player.getPos().add(0, 1, 0);
            Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
            Vec3d dir = to.subtract(from);
            double len = dir.length();
            if (len > 0) {
                Vec3d step = dir.normalize().multiply(0.5);
                Vec3d pos = from;
                for (double d = 0; d < len; d += 0.5) {
                    serverWorld.spawnParticles(ParticleTypes.END_ROD,
                            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                    pos = pos.add(step);
                }
            }
        }

        return true;
    }
}
