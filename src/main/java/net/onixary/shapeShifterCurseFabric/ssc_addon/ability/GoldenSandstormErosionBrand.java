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
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 侵蚀烙印（主动②）
 * 释放后标记视线前方8格锥形范围内所有非白名单生物，
 * 标记持续6秒，期间每次攻击标记目标叠加1层烙印（最多3层）。
 * 3层满时自动引爆，造成 4 + 3*(凋零等级+1) 点真实伤害。
 * CD: 20秒（400tick）
 */
public class GoldenSandstormErosionBrand {

    private GoldenSandstormErosionBrand() {
    }

    // ==================== 常量 ====================
    /** 标记锥形范围 */
    private static final double CONE_RANGE = 8.0;
    /** 锥形角度余弦值（约60度锥形，dot > 0.5） */
    private static final double CONE_DOT = 0.5;
    /** 标记持续时间（tick） */
    private static final int BRAND_DURATION = 120; // 6秒
    /** 最大叠加层数 */
    private static final int MAX_STACKS = 3;
    /** 基础引爆伤害 */
    private static final float BASE_BURST_DAMAGE = 4.0f;
    /** 每级凋零额外伤害 */
    private static final float WITHER_BONUS_PER_LEVEL = 3.0f;
    /** CD时间（tick） */
    private static final int COOLDOWN_TICKS = 400; // 20秒

    // ==================== 状态追踪 ====================
    /** 玩家UUID -> 被标记目标及其数据 */
    private static final ConcurrentHashMap<UUID, BrandSession> ACTIVE_BRANDS = new ConcurrentHashMap<>();

    private static class BrandSession {
        int remainingTicks;
        /** 目标UUID -> 叠加层数 */
        final Map<UUID, Integer> brandedTargets = new HashMap<>();

        BrandSession() {
            this.remainingTicks = BRAND_DURATION;
        }
    }

    /**
     * 玩家按下技能键触发
     */
    public static boolean execute(ServerPlayerEntity player) {
        // CD检查
        int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD);
        if (cd > 0) {
            return false;
        }

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

        // 标记视线前方的目标
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0F);
        Box box = player.getBoundingBox().expand(CONE_RANGE);
        List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        BrandSession session = new BrandSession();
        for (LivingEntity target : targets) {
            // 白名单检查
            if (WhitelistUtils.isProtected(player, target)) continue;

            // 锥形范围检查
            Vec3d toTarget = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(eyePos).normalize();
            double dot = lookVec.dotProduct(toTarget);
            double distSq = player.squaredDistanceTo(target);

            if (dot > CONE_DOT && distSq <= CONE_RANGE * CONE_RANGE) {
                session.brandedTargets.put(target.getUuid(), 0);

                // 标记粒子
                ParticleUtils.spawnParticles(serverWorld, ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                        10, 0.3, 0.3, 0.3, 0.1);
            }
        }

        if (session.brandedTargets.isEmpty()) {
            // 没有标记到任何目标，不进入CD
            return false;
        }

        // 设置CD
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, COOLDOWN_TICKS);
        ACTIVE_BRANDS.put(player.getUuid(), session);

        // 释放音效
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);

        return true;
    }

    /**
     * 当玩家攻击目标时调用（由Mixin或Event触发）
     * 检查目标是否被烙印标记，如果是则叠加层数
     */
    public static void onPlayerAttack(ServerPlayerEntity player, LivingEntity target) {
        BrandSession session = ACTIVE_BRANDS.get(player.getUuid());
        if (session == null) return;

        UUID targetUuid = target.getUuid();
        if (!session.brandedTargets.containsKey(targetUuid)) return;

        int currentStacks = session.brandedTargets.get(targetUuid) + 1;
        session.brandedTargets.put(targetUuid, currentStacks);

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        // 叠加粒子提示
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                5 * currentStacks, 0.2, 0.2, 0.2, 0.05);

        // 达到最大层数时引爆
        if (currentStacks >= MAX_STACKS) {
            detonateBrand(player, target, serverWorld);
            session.brandedTargets.remove(targetUuid);
        }
    }

    /**
     * 引爆烙印，造成真实伤害
     */
    private static void detonateBrand(ServerPlayerEntity player, LivingEntity target, ServerWorld serverWorld) {
        // 计算伤害：4 + 3*(凋零等级+1)
        StatusEffectInstance witherEffect = target.getStatusEffect(StatusEffects.WITHER);
        int witherAmplifier = (witherEffect != null) ? witherEffect.getAmplifier() : -1;
        float damage = BASE_BURST_DAMAGE;
        if (witherAmplifier >= 0) {
            damage += WITHER_BONUS_PER_LEVEL * (witherAmplifier + 1);
        }

        // 造成真实伤害（magic类型，绕过护甲）
        RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
        Vec3d oldVelocity = target.getVelocity();
        if (target.damage(target.getDamageSources().create(magicKey, player, player), damage)) {
            target.setVelocity(oldVelocity); // 保持原速度，不被击退
        }

        // 引爆粒子和音效
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                30, 0.5, 0.5, 0.5, 0.1);
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
                target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                1, 0, 0, 0, 0);
        serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * 每tick更新，倒计时烙印持续时间
     */
    public static void tick(ServerPlayerEntity player) {
        BrandSession session = ACTIVE_BRANDS.get(player.getUuid());
        if (session == null) return;

        // 形态检查
        if (!FormUtils.isGoldenSandstormSP(player)) {
            ACTIVE_BRANDS.remove(player.getUuid());
            return;
        }

        session.remainingTicks--;
        if (session.remainingTicks <= 0 || session.brandedTargets.isEmpty()) {
            // 时间到或所有目标已引爆，清除会话
            ACTIVE_BRANDS.remove(player.getUuid());
        }
    }

    /**
     * 玩家断线/变形时清理
     */
    public static void clearPlayer(UUID playerUuid) {
        ACTIVE_BRANDS.remove(playerUuid);
    }
}
