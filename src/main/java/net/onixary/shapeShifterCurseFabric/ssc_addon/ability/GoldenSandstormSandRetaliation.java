package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 沙化反噬（被动②）
 * 生命值低于30%时自动触发：
 * - 对周围5格内所有非白名单生物施加凋零I（8秒）
 * - 自身获得速度II + 力量I（10秒）
 * - 金色粒子爆发
 * CD: 60秒（1200tick）
 * 通过自定义资源 GOLDEN_SANDSTORM_RETALIATION_CD 管理CD
 */
public class GoldenSandstormSandRetaliation {

    private GoldenSandstormSandRetaliation() {
    }

    // ==================== 常量 ====================
    /** AoE半径 */
    private static final double RADIUS = 5.0;
    /** 凋零等级（0=I） */
    private static final int WITHER_AMPLIFIER = 0;
    /** 凋零持续时间（tick） */
    private static final int WITHER_DURATION = 160; // 8秒
    /** Buff持续时间（tick） */
    private static final int BUFF_DURATION = 200; // 10秒
    /** 触发血量阈值（30%） */
    private static final float HEALTH_THRESHOLD = 0.3f;
    /** CD时间（tick） */
    private static final int COOLDOWN_TICKS = 1200; // 60秒
    /** 检查间隔（tick），避免每tick都检查 */
    private static final int CHECK_INTERVAL = 5;

    // 防止连续触发的内部锁
    private static final ConcurrentHashMap<UUID, Boolean> TRIGGERED = new ConcurrentHashMap<>();

    /**
     * 每tick检查是否需要触发
     */
    public static void tick(ServerPlayerEntity player) {
        // 形态检查
        if (!FormUtils.isGoldenSandstormSP(player)) {
            TRIGGERED.remove(player.getUuid());
            return;
        }

        // 降低检查频率
        if (player.age % CHECK_INTERVAL != 0) return;

        float healthRatio = player.getHealth() / player.getMaxHealth();

        // 血量恢复后重置触发锁
        if (healthRatio >= HEALTH_THRESHOLD) {
            TRIGGERED.remove(player.getUuid());
            return;
        }

        // 已触发过且还在低血量状态，不重复触发
        if (TRIGGERED.getOrDefault(player.getUuid(), false)) return;

        // CD检查
        int cd = PowerUtils.getResourceValue(player, FormIdentifiers.GOLDEN_SANDSTORM_RETALIATION_CD);
        if (cd > 0) return;

        // 触发被动
        executeRetaliation(player);
        TRIGGERED.put(player.getUuid(), true);
    }

    private static void executeRetaliation(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        // 设置CD
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.GOLDEN_SANDSTORM_RETALIATION_CD, COOLDOWN_TICKS);

        // 自身Buff
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, BUFF_DURATION, 1, false, true)); // 速度II
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, BUFF_DURATION, 0, false, true)); // 力量I

        // AoE凋零
        Box box = player.getBoundingBox().expand(RADIUS);
        List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= RADIUS * RADIUS);

        for (LivingEntity target : targets) {
            if (WhitelistUtils.isProtected(player, target)) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER, false, true));
        }

        // 粒子效果：金色沙暴爆发
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(), 50, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.05);
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, RADIUS * 0.3, 0.5, RADIUS * 0.3, 0.02);

        // 音效
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.5f);
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    /**
     * 玩家断线/变形时清理
     */
    public static void clearPlayer(UUID playerUuid) {
        TRIGGERED.remove(playerUuid);
    }

    /**
     * 服务器重启/热重载时清理所有状态
     */
    public static void clearAll() {
        TRIGGERED.clear();
    }
}
