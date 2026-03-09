package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.minion.IPlayerEntityMinion;
import net.onixary.shapeShifterCurseFabric.minion.MinionRegister;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP阿努比斯之狼次要技能 - 冥狼裁庭
 * 召唤AnubisWolfMinionEntity（与受击/攻击时召唤的胡狼相同实体）协助战斗。
 * 嚎叫蓄力1.5秒后，交错出现2只冥狼（Death Domain激活时为4只，且增强属性）。
 * 冥狼持续30秒后消散。
 * 冥狼自带凋零攻击和治愈主人逻辑（level 3：攻击力4，生命20，治愈2HP/hit）。
 * 死亡领域联动时进一步增强属性并给予速度。
 * CD30秒，若已达最大召唤数（4只）则惩罚性CD5秒。
 */
public class AnubisWolfSpSummonWolves {

    private AnubisWolfSpSummonWolves() {
    }

    // ==================== 常量 ====================
    /** 嚎叫蓄力时间（tick） */
    private static final int HOWL_TICKS = 30; // 1.5秒
    /** 嚎叫减速比例（保留50%速度） */
    private static final double HOWL_SLOW_FACTOR = 0.5;
    /** 召唤间隔（tick），交错出现 */
    private static final int SUMMON_INTERVAL = 5;
    /** 冥狼存活时间（tick） */
    private static final int WOLF_DURATION = 600; // 30秒
    /** CD时间（tick） */
    private static final int COOLDOWN_TICKS = 600; // 30秒
    /** 惩罚CD时间（tick） */
    private static final int PENALTY_COOLDOWN_TICKS = 100; // 5秒
    /** 最大同时存在冥狼数 */
    private static final int MAX_WOLVES = 6;
    /** 基础召唤数量 */
    private static final int BASE_SUMMON_COUNT = 2;
    /** 领域增强召唤数量 */
    private static final int DOMAIN_SUMMON_COUNT = 4;
    /** 冥狼等级（与SSC原版level 3一致） */
    private static final int MINION_LEVEL = 3;
    /** 领域增强额外生命值 */
    private static final double DOMAIN_BONUS_HEALTH = 10.0;
    /** 领域增强额外攻击力 */
    private static final double DOMAIN_BONUS_ATTACK = 2.0;
    /** 嚎叫减速修饰符UUID */
    private static final UUID HOWL_SLOW_UUID = UUID.fromString("c9d5e6f7-a8b9-4c0d-1e2f-3a4b5c6d7e8f");
    /** 领域增强攻击力修饰符UUID */
    private static final UUID DOMAIN_ATTACK_UUID = UUID.fromString("d0e1f2a3-b4c5-4d6e-7f8a-9b0c1d2e3f4a");
    /** 领域增强生命值修饰符UUID */
    private static final UUID DOMAIN_HEALTH_UUID = UUID.fromString("e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b");

    // ==================== 状态追踪 ====================
    private static final ConcurrentHashMap<UUID, SummonData> ACTIVE_SUMMONS = new ConcurrentHashMap<>();

    // ==================== 阶段枚举 ====================
    private enum Phase {
        HOWLING,     // 嚎叫蓄力中（1.5秒）
        SUMMONING,   // 交错召唤中
        ACTIVE,      // 冥狼活动中（30秒）
    }

    // ==================== 数据类 ====================
    private static class SummonData {
        Phase phase;
        int ticksElapsed;
        int wolvesToSummon;     // 本次需要召唤的总数
        int wolvesSummoned;     // 已召唤的数量
        boolean domainActive;   // 召唤时是否有激活的死亡领域
        List<UUID> summonedWolfUuids = new ArrayList<>(); // 当前批次召唤的狼UUID

        SummonData(int wolvesToSummon, boolean domainActive) {
            this.phase = Phase.HOWLING;
            this.ticksElapsed = 0;
            this.wolvesToSummon = wolvesToSummon;
            this.wolvesSummoned = 0;
            this.domainActive = domainActive;
        }
    }

    // ==================== 公开接口 ====================

    /**
     * 玩家按下次要技能键触发
     */
    public static boolean execute(ServerPlayerEntity player) {
        // CD检查
        int cdRemaining = PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD);
        if (cdRemaining > 0) {
            return false;
        }

        // 重复释放检查（正在嚎叫/召唤中）
        if (ACTIVE_SUMMONS.containsKey(player.getUuid())) {
            return false;
        }

        // 通过IPlayerEntityMinion系统检查当前冥狼数量
        int aliveCount = getMinionCount(player);
        if (aliveCount >= MAX_WOLVES) {
            // 已达上限，给予惩罚CD，播放失败音效
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, PENALTY_COOLDOWN_TICKS);
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.5f);
            return false;
        }

        // 检查是否在死亡领域中
        boolean domainActive = AnubisWolfSpDeathDomain.hasActiveDomain(player.getUuid());

        // 计算可召唤数量（不超过上限）
        int targetCount = domainActive ? DOMAIN_SUMMON_COUNT : BASE_SUMMON_COUNT;
        int canSummon = Math.min(targetCount, MAX_WOLVES - aliveCount);

        // 创建召唤数据并进入嚎叫阶段
        SummonData data = new SummonData(canSummon, domainActive);
        ACTIVE_SUMMONS.put(player.getUuid(), data);

        // 播放狼嚎叫声
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1.5f, 0.6f);

        // 应用减速
        applyHowlSlow(player);

        return true;
    }

    /**
     * 每tick更新
     */
    public static void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        SummonData data = ACTIVE_SUMMONS.get(uuid);
        if (data == null) return;

        data.ticksElapsed++;

        switch (data.phase) {
            case HOWLING -> tickHowling(player, data);
            case SUMMONING -> tickSummoning(player, data);
            case ACTIVE -> tickActive(player, data);
        }
    }

    /**
     * 玩家断线/变形时清理
     */
    public static void clearPlayer(UUID uuid) {
        ACTIVE_SUMMONS.remove(uuid);
    }

    /**
     * 增强死亡领域自动召唤冥狼（跳过嚎叫阶段，直接生成）
     * 实际召唤数量 = min(requestCount, MAX_WOLVES - 已有数量)
     */
    public static void autoSummonForEnhancedDomain(ServerPlayerEntity player, int requestCount) {
        int aliveCount = getMinionCount(player);
        int canSummon = Math.min(requestCount, MAX_WOLVES - aliveCount);
        if (canSummon <= 0) return;

        // 创建一个领域增强的SummonData，直接进入SUMMONING阶段
        SummonData data = new SummonData(canSummon, true);
        data.phase = Phase.SUMMONING;
        data.ticksElapsed = 0;

        // 如果已有正在进行的召唤流程，则跳过（避免冲突）
        if (ACTIVE_SUMMONS.containsKey(player.getUuid())) return;
        ACTIVE_SUMMONS.put(player.getUuid(), data);
    }

    // ==================== 阶段处理 ====================

    private static void tickHowling(ServerPlayerEntity player, SummonData data) {
        ServerWorld world = player.getServerWorld();

        // 嚎叫期间粒子效果 - 灵魂粒子围绕
        if (data.ticksElapsed % 3 == 0) {
            double angle = (data.ticksElapsed * 12.0) * Math.PI / 180.0;
            double radius = 1.5;
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                    px, player.getY() + 0.5, pz, 2, 0.1, 0.3, 0.1, 0.02);
        }

        if (data.ticksElapsed >= HOWL_TICKS) {
            // 蓄力完成，移除减速
            removeHowlSlow(player);

            // 进入召唤阶段
            data.phase = Phase.SUMMONING;
            data.ticksElapsed = 0;

            // 播放召唤音效
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);
        }
    }

    private static void tickSummoning(ServerPlayerEntity player, SummonData data) {
        // 每SUMMON_INTERVAL tick召唤一只
        if (data.ticksElapsed % SUMMON_INTERVAL == 0 && data.wolvesSummoned < data.wolvesToSummon) {
            spawnMinionWolf(player, data);
            data.wolvesSummoned++;
        }

        // 全部召唤完毕则转入ACTIVE阶段
        if (data.wolvesSummoned >= data.wolvesToSummon) {
            data.phase = Phase.ACTIVE;
            data.ticksElapsed = 0;

            // 设置CD
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, COOLDOWN_TICKS);
        }
    }

    private static void tickActive(ServerPlayerEntity player, SummonData data) {
        ServerWorld world = player.getServerWorld();

        // 每20tick检查一次冥狼的灵魂沙免疫
        if (data.ticksElapsed % 20 == 0) {
            refreshWolfBuffs(world, data);
        }

        // 冥狼持续时间到期
        if (data.ticksElapsed >= WOLF_DURATION) {
            // 消散所有本批次的狼
            dissipateWolves(player, data, world);
            ACTIVE_SUMMONS.remove(player.getUuid());
        }
    }

    // ==================== 狼召唤与管理 ====================

    private static void spawnMinionWolf(ServerPlayerEntity player, SummonData data) {
        ServerWorld world = player.getServerWorld();

        // 使用MinionRegister寻找合适的生成位置（与原版胡狼召唤逻辑一致）
        BlockPos spawnPos = MinionRegister.getNearbyEmptySpace(
                world, player.getRandom(), player.getBlockPos(), 3, 1, 1, 4);
        if (spawnPos == null) {
            spawnPos = player.getBlockPos();
        }

        // 使用MinionRegister.SpawnMinion生成（自动调用InitMinion注册到IPlayerEntityMinion系统）
        AnubisWolfMinionEntity wolf = MinionRegister.SpawnMinion(
                MinionRegister.ANUBIS_WOLF_MINION, world, spawnPos, player);

        if (wolf == null) return;

        // 设置等级（level 3: HP=20, Attack=4, 治愈主人2HP/hit）
        wolf.setMinionLevel(MINION_LEVEL);

        // 死亡领域联动增强
        if (data.domainActive) {
            // 额外生命值
            EntityAttributeInstance healthAttr = wolf.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.addPersistentModifier(new EntityAttributeModifier(
                        DOMAIN_HEALTH_UUID, "domain_bonus_health",
                        DOMAIN_BONUS_HEALTH, EntityAttributeModifier.Operation.ADDITION));
                wolf.setHealth((float) healthAttr.getValue());
            }
            // 额外攻击力
            EntityAttributeInstance attackAttr = wolf.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.addPersistentModifier(new EntityAttributeModifier(
                        DOMAIN_ATTACK_UUID, "domain_bonus_attack",
                        DOMAIN_BONUS_ATTACK, EntityAttributeModifier.Operation.ADDITION));
            }
            // 速度I效果
            wolf.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, WOLF_DURATION, 0, false, false, true));
        }

        // 追踪此批次的狼UUID（用于定时消散）
        data.summonedWolfUuids.add(wolf.getUuid());

        // 生成粒子效果
        ParticleUtils.spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME,
                wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 15, 0.5, 0.5, 0.5, 0.05);
        ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                wolf.getX(), wolf.getY() + 0.3, wolf.getZ(), 8, 0.3, 0.5, 0.3, 0.02);

        // 播放出现音效
        world.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(),
                SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.NEUTRAL, 1.0f, 0.7f);
    }

    private static void refreshWolfBuffs(ServerWorld world, SummonData data) {
        for (UUID wolfUuid : data.summonedWolfUuids) {
            Entity entity = world.getEntity(wolfUuid);
            if (entity instanceof AnubisWolfMinionEntity wolf && wolf.isAlive()) {
                // 灵魂沙免疫：在灵魂沙上时给予速度III抵消减速
                BlockPos below = wolf.getBlockPos().down();
                if (world.getBlockState(below).isOf(net.minecraft.block.Blocks.SOUL_SAND)
                        || world.getBlockState(wolf.getBlockPos()).isOf(net.minecraft.block.Blocks.SOUL_SAND)) {
                    wolf.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SPEED, 25, 2, false, false, false));
                }
            }
        }
    }

    private static void dissipateWolves(ServerPlayerEntity player, SummonData data, ServerWorld world) {
        for (UUID wolfUuid : data.summonedWolfUuids) {
            Entity entity = world.getEntity(wolfUuid);
            if (entity instanceof AnubisWolfMinionEntity wolf && wolf.isAlive()) {
                // 消散粒子
                ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                        wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 12, 0.4, 0.6, 0.4, 0.03);
                ParticleUtils.spawnParticles(world, ParticleTypes.SMOKE,
                        wolf.getX(), wolf.getY() + 0.3, wolf.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                // 播放消散音效
                world.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(),
                        SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.NEUTRAL, 0.8f, 0.8f);
                // 从IPlayerEntityMinion系统移除并销毁
                if (player instanceof IPlayerEntityMinion minionPlayer) {
                    minionPlayer.shape_shifter_curse$removeMinion(AnubisWolfMinionEntity.MinionID, wolfUuid);
                }
                wolf.discard();
            }
        }
    }

    // ==================== 辅助方法 ====================

    private static int getMinionCount(ServerPlayerEntity player) {
        if (player instanceof IPlayerEntityMinion minionPlayer) {
            return minionPlayer.shape_shifter_curse$getMinionsCount(AnubisWolfMinionEntity.MinionID);
        }
        return 0;
    }

    private static void applyHowlSlow(ServerPlayerEntity player) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(HOWL_SLOW_UUID) == null) {
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    HOWL_SLOW_UUID, "howl_slow",
                    HOWL_SLOW_FACTOR - 1.0, // -0.5 = 保留50%
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeHowlSlow(ServerPlayerEntity player) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(HOWL_SLOW_UUID);
        }
    }
}
