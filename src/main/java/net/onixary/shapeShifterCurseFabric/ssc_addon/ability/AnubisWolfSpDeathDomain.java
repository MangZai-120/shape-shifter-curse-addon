package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SP阿努比斯之狼主要技能 - 死亡领域
 * 将周围24格半径、高低差9格的圆柱范围内的方块暂时变为灵魂沙。
 * 释放时2秒充能，充能期间减速70%。
 * 蓄力完成后以5格/秒速度延展灵魂沙。
 * 按白名单给予踩在灵魂沙上方3格内的生物减速I、凋零I并减少15%血量上限（离开时移除）。
 * 领域持续15秒，CD50秒。
 * 领域结束后从最远处开始以5格/秒速度回退并还原方块。
 */
public class AnubisWolfSpDeathDomain {

    private static final Logger LOGGER = LoggerFactory.getLogger("DeathDomain");

    private AnubisWolfSpDeathDomain() {
    }

    // ==================== 常量 ====================
    /** 领域最大半径（格） */
    private static final int DOMAIN_RADIUS = 24;
    /** 领域上下高度差（格）*/
    private static final int DOMAIN_HEIGHT = 9;
    /** 充能时间（tick） */
    private static final int CHARGE_TICKS = 40; // 2秒
    /** 充能期间减速比例 */
    private static final double CHARGE_SLOW_FACTOR = 0.3; // 保留30%速度=减速70%
    /** 延展速度：5格/秒 = 每tick 0.25格 */
    private static final double EXPAND_SPEED = 5.0 / 20.0; // 0.25格/tick
    /** 领域持续时间（tick） */
    private static final int DOMAIN_DURATION = 300; // 15秒
    /** debuff持续时间（tick） */
    private static final int DEBUFF_DURATION = 300; // 15秒
    /** 血量减少百分比 */
    private static final double HEALTH_REDUCTION = 0.15;
    /** CD时间（tick） */
    private static final int COOLDOWN_TICKS = 1000; // 50秒
    /** 惩罚性CD时间（tick） */
    private static final int PENALTY_COOLDOWN_TICKS = 200; // 10秒

    // ==================== 增强模式常量 ====================
    /** 增强领域最大半径（格） */
    private static final int ENHANCED_DOMAIN_RADIUS = 32;
    /** 增强充能时间（tick） */
    private static final int ENHANCED_CHARGE_TICKS = 20; // 1秒
    /** 增强领域持续时间（tick） */
    private static final int ENHANCED_DOMAIN_DURATION = 500; // 25秒
    /** 增强凋零等级（Wither II） */
    private static final int ENHANCED_WITHER_AMPLIFIER = 1;
    /** 增强模式自动召唤冥狼数量 */
    private static final int ENHANCED_AUTO_SUMMON_COUNT = 6;
    /** 血量减少修饰符UUID */
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a7b3c4d5-e6f7-4a8b-9c0d-1e2f3a4b5c6d");
    /** 充能减速修饰符UUID */
    private static final UUID CHARGE_SLOW_UUID = UUID.fromString("b8c4d5e6-f7a8-4b9c-0d1e-2f3a4b5c6d7e");

    // ==================== 状态追踪 ====================
    private static final ConcurrentHashMap<UUID, DomainData> ACTIVE_DOMAINS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>();

    // ==================== 阶段枚举 ====================
    private enum Phase {
        CHARGING,    // 充能中（2秒）
        EXPANDING,   // 延展中（灵魂沙从中心向外扩散）
        SUSTAINING,  // 领域维持中（15秒）
        RETRACTING,  // 回退中（从外向内还原）
        CLEANUP      // 等待服务器线程清理（玩家断线后）
    }

    // ==================== 数据类 ====================
    private static class DomainData {
        Phase phase;
        int ticksElapsed;
        double currentRadius;       // 当前延展/回退的半径
        BlockPos center;            // 领域中心
        int centerY;                // 施法者Y坐标
        ServerWorld world;          // 领域所在世界（用于断线/关服还原）
        // 按距离分层存储被改变的方块: 距离 -> (位置 -> 原始方块状态)
        TreeMap<Integer, Map<BlockPos, BlockState>> changedBlocksByDistance = new TreeMap<>();
        // 被削减血量的生物UUID集合
        Set<UUID> debuffedEntities = new HashSet<>();
        // 每个实体受到的凋零伤害次数追踪（每次凋零效果触发1HP，上限10次=10HP）
        Map<UUID, Integer> witherHitCount = new HashMap<>();
        // 增强模式标记（灵魂能量满时施放）
        boolean enhanced;

        DomainData(ServerWorld world, BlockPos center, int centerY) {
            this.phase = Phase.CHARGING;
            this.ticksElapsed = 0;
            this.currentRadius = 0;
            this.world = world;
            this.center = center;
            this.centerY = centerY;
        }
    }

    // ==================== 公开接口 ====================

    /**
     * 玩家按下技能键触发
     */
    public static boolean execute(ServerPlayerEntity player) {
        // CD检查
        Long cdEndTime = COOLDOWN_PLAYERS.get(player.getUuid());
        if (cdEndTime != null && System.currentTimeMillis() < cdEndTime) {
            return false;
        }

        // 重复释放检查
        if (ACTIVE_DOMAINS.containsKey(player.getUuid())) {
            return false;
        }

        // 直接开始蓄力，不做前置检查
        // 创建领域数据
        BlockPos center = player.getBlockPos();
        ServerWorld serverWorld = (ServerWorld) player.getWorld();
        DomainData data = new DomainData(serverWorld, center, (int) player.getY());

        // 检查灵魂能量是否满：满则进入增强模式并消耗能量
        if (AnubisWolfSpSoulEnergy.isFullEnergy(player.getUuid())) {
            data.enhanced = true;
            AnubisWolfSpSoulEnergy.consumeEnergy(player);
            LOGGER.info("[DeathDomain] ENHANCED mode activated for player={}", player.getName().getString());
        }

        ACTIVE_DOMAINS.put(player.getUuid(), data);

        LOGGER.info("[DeathDomain] execute: player={}, center={}, world={}, ACTIVE_DOMAINS size={}",
                player.getName().getString(), center, serverWorld.getRegistryKey().getValue(), ACTIVE_DOMAINS.size());

        // 应用充能减速效果
        applyChargeSlow(player);

        // 充能开始音效：灵魂沙谷吸引 + 幽灵低吟
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_SOUL_SAND_PLACE, SoundCategory.PLAYERS, 1.5f, 0.5f);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.6f);

        return true;
    }

    /**
     * 每tick更新，由SscAddon的tick处理器调用
     */
    public static void tick(ServerPlayerEntity player) {
        DomainData data = ACTIVE_DOMAINS.get(player.getUuid());
        if (data == null) return;

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

        data.ticksElapsed++;

        switch (data.phase) {
            case CHARGING -> tickCharging(player, serverWorld, data);
            case EXPANDING -> tickExpanding(player, serverWorld, data);
            case SUSTAINING -> tickSustaining(player, serverWorld, data);
            case RETRACTING -> tickRetracting(player, serverWorld, data);
        }
    }

    /**
     * 玩家断线/死亡时清理，还原所有已转化方块
     */
    /**
     * 玩家断线时清理玩家特有状态（CD、减速修饰符）
     * 不在此处做方块还原，因为DISCONNECT事件在Netty IO线程上触发，
     * world.setBlockState()不是线程安全的。
     * 方块还原由 tickCleanup()（服务器线程）或 forceRestoreAll()（SERVER_STOPPING）处理。
     */
    public static void clearPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        LOGGER.info("[DeathDomain] clearPlayer called: player={}, uuid={}, ACTIVE_DOMAINS size={}, containsKey={}",
                player.getName().getString(), uuid, ACTIVE_DOMAINS.size(), ACTIVE_DOMAINS.containsKey(uuid));
        DomainData data = ACTIVE_DOMAINS.get(uuid);
        if (data != null) {
            // 只清理玩家特有状态，不移除ACTIVE_DOMAINS中的数据
            if (data.phase == Phase.CHARGING) {
                removeChargeSlow(player);
            }
            // 标记为待清理，让tickCleanup在服务器线程上处理方块还原
            data.phase = Phase.CLEANUP;
            LOGGER.info("[DeathDomain] clearPlayer: marked for CLEANUP, totalBlocks={}",
                    data.changedBlocksByDistance.values().stream().mapToInt(Map::size).sum());
        } else {
            LOGGER.info("[DeathDomain] clearPlayer: no active domain found for player");
        }
        COOLDOWN_PLAYERS.remove(uuid);
    }

    /**
     * 在服务器线程的tick中检查并清理已断线玩家的领域（方块还原）
     * 由SscAddon的tick处理器调用，确保在Server thread上执行
     */
    public static void tickCleanup() {
        Iterator<Map.Entry<UUID, DomainData>> it = ACTIVE_DOMAINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DomainData> entry = it.next();
            DomainData data = entry.getValue();
            if (data.phase == Phase.CLEANUP && data.world != null) {
                int totalBlocks = data.changedBlocksByDistance.values().stream().mapToInt(Map::size).sum();
                LOGGER.info("[DeathDomain] tickCleanup: restoring uuid={}, totalBlocks={}, world={}",
                        entry.getKey(), totalBlocks, data.world.getRegistryKey().getValue());
                restoreAllBlocks(data.world, data);
                cleanupDebuffs(data.world, data);
                it.remove();
                LOGGER.info("[DeathDomain] tickCleanup: restoration completed");
            }
        }
    }

    /**
     * 检查玩家是否在充能中（用于外部减速检查等）
     */
    public static boolean isCharging(ServerPlayerEntity player) {
        DomainData data = ACTIVE_DOMAINS.get(player.getUuid());
        return data != null && data.phase == Phase.CHARGING;
    }

    /**
     * 检查玩家是否有激活的死亡领域（用于冥狼裁庭联动）
     * 处于EXPANDING、SUSTAINING阶段均视为激活状态
     */
    public static boolean hasActiveDomain(UUID playerUuid) {
        DomainData data = ACTIVE_DOMAINS.get(playerUuid);
        if (data == null) return false;
        return data.phase == Phase.EXPANDING || data.phase == Phase.SUSTAINING;
    }

    /**
     * 检查指定位置是否在玩家激活的死亡领域范围内
     */
    public static boolean isInActiveDomain(UUID playerUuid, BlockPos pos) {
        DomainData data = ACTIVE_DOMAINS.get(playerUuid);
        if (data == null) return false;
        if (data.phase != Phase.EXPANDING && data.phase != Phase.SUSTAINING) return false;
        double dx = pos.getX() - data.center.getX();
        double dz = pos.getZ() - data.center.getZ();
        double radius = data.enhanced ? ENHANCED_DOMAIN_RADIUS : DOMAIN_RADIUS;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * 检查玩家的死亡领域是否为增强模式
     */
    public static boolean isEnhancedDomain(UUID playerUuid) {
        DomainData data = ACTIVE_DOMAINS.get(playerUuid);
        return data != null && data.enhanced;
    }

    /**
     * 服务器关闭或玩家变更形态时，强制还原所有领域
     * 每个领域使用自身存储的世界引用，确保还原到正确的维度
     */
    public static void forceRestoreAll() {
        LOGGER.info("[DeathDomain] forceRestoreAll called, ACTIVE_DOMAINS size={}", ACTIVE_DOMAINS.size());
        for (Map.Entry<UUID, DomainData> entry : ACTIVE_DOMAINS.entrySet()) {
            DomainData data = entry.getValue();
            int totalBlocks = 0;
            for (Map<BlockPos, BlockState> m : data.changedBlocksByDistance.values()) {
                totalBlocks += m.size();
            }
            LOGGER.info("[DeathDomain] forceRestoreAll: uuid={}, phase={}, world={}, totalBlocks={}",
                    entry.getKey(), data.phase, data.world != null ? data.world.getRegistryKey().getValue() : "NULL", totalBlocks);
            if (data.world != null) {
                restoreAllBlocks(data.world, data);
                cleanupDebuffs(data.world, data);
            }
        }
        ACTIVE_DOMAINS.clear();
        LOGGER.info("[DeathDomain] forceRestoreAll completed");
    }

    // ==================== 阶段处理 ====================

    /**
     * 检查玩家脚下4格范围内是否有可转化为灵魂沙的方块，或已经是灵魂沙/灵魂土
     */
    private static boolean hasConvertibleBlocksBelow(ServerPlayerEntity player) {
        BlockPos feetPos = player.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16) continue; // 圆形范围
                for (int dy = -4; dy <= 0; dy++) {
                    BlockPos pos = feetPos.add(dx, dy, dz);
                    BlockState state = player.getWorld().getBlockState(pos);
                    Block block = state.getBlock();
                    if (shouldConvert(state) || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 充能阶段：普通2秒/增强1秒，期间减速70% */
    private static void tickCharging(ServerPlayerEntity player, ServerWorld world, DomainData data) {
        int chargeTicks = data.enhanced ? ENHANCED_CHARGE_TICKS : CHARGE_TICKS;

        // 充能粒子效果：灵魂粒子围绕玩家旋转上升
        double angle = (data.ticksElapsed * 0.2) % (Math.PI * 2);
        double radius = 1.0 + data.ticksElapsed * 0.03;
        int particleStreams = data.enhanced ? 5 : 3;
        for (int i = 0; i < particleStreams; i++) {
            double a = angle + i * (Math.PI * 2 / particleStreams);
            double px = player.getX() + Math.cos(a) * radius;
            double pz = player.getZ() + Math.sin(a) * radius;
            double py = player.getY() + 0.5 + data.ticksElapsed * 0.025;
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL, px, py, pz, 1, 0, 0.05, 0, 0.01);
            if (data.enhanced) {
                ParticleUtils.spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0.08, 0, 0.02);
            }
        }

        // 心跳音效（每10tick一次）
        if (data.ticksElapsed % 10 == 0) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.8f, 0.5f + data.ticksElapsed * 0.01f);
        }

        if (data.ticksElapsed >= chargeTicks) {
            // 充能完成，移除充能减速
            removeChargeSlow(player);

            // 检查脚下是否有可转化方块
            if (!hasConvertibleBlocksBelow(player)) {
                // 释放失败：播放失败音效，进入惩罚性CD
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
                COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + (long) PENALTY_COOLDOWN_TICKS * 50);
                PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, PENALTY_COOLDOWN_TICKS);
                ACTIVE_DOMAINS.remove(player.getUuid());
                return;
            }

            // 释放成功：设置正常CD，进入延展阶段
            COOLDOWN_PLAYERS.put(player.getUuid(), System.currentTimeMillis() + (long) COOLDOWN_TICKS * 50);
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, COOLDOWN_TICKS);

            // 更新领域中心为充能完成时的位置
            data.center = player.getBlockPos();
            data.centerY = (int) player.getY();
            data.phase = Phase.EXPANDING;
            data.ticksElapsed = 0;
            data.currentRadius = 0;

            // 释放音效：低沉爆发 + 灵魂回声
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.7f, 0.4f);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 0.5f, 0.5f);

            // 增强模式：自动召唤6只冥狼，并立即让召唤技能也进入CD
            if (data.enhanced) {
                AnubisWolfSpSummonWolves.autoSummonForEnhancedDomain(player, ENHANCED_AUTO_SUMMON_COUNT);
                PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD,
                        AnubisWolfSpSummonWolves.getCooldownTicks());
                // 增强模式额外音效
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
        }
    }

    /** 延展阶段：以5格/秒速度从中心向外扩散灵魂沙 */
    private static void tickExpanding(ServerPlayerEntity player, ServerWorld world, DomainData data) {
        int maxRadius = data.enhanced ? ENHANCED_DOMAIN_RADIUS : DOMAIN_RADIUS;
        double prevRadius = data.currentRadius;
        data.currentRadius += EXPAND_SPEED;

        if (data.currentRadius > maxRadius) {
            data.currentRadius = maxRadius;
        }

        // 转化半径range内的方块
        int prevR = (int) Math.floor(prevRadius);
        int currR = (int) Math.ceil(data.currentRadius);
        convertBlocksInRing(world, data, prevR, currR);

        // 对范围内踩在灵魂沙上的生物施加debuff（每tick检查）
        tickAreaDebuffs(player, world, data);

        // 延展粒子效果：边缘灵魂粒子环
        spawnEdgeParticles(world, data);

        // 延展音效（每5tick一次沙砾滑动声）
        if (data.ticksElapsed % 5 == 0) {
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.BLOCK_SOUL_SAND_STEP, SoundCategory.BLOCKS, 0.6f, 0.7f);
        }

        if (data.currentRadius >= maxRadius) {
            data.phase = Phase.SUSTAINING;
            data.ticksElapsed = 0;

            // 完全展开音效
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.5f, 0.3f);
        }

        data.ticksElapsed++;
    }

    /** 维持阶段：普通15秒/增强20秒 */
    private static void tickSustaining(ServerPlayerEntity player, ServerWorld world, DomainData data) {
        int duration = data.enhanced ? ENHANCED_DOMAIN_DURATION : DOMAIN_DURATION;
        int maxRadius = data.enhanced ? ENHANCED_DOMAIN_RADIUS : DOMAIN_RADIUS;

        // 持续检查范围内踩在灵魂沙上的生物，施加/移除debuff
        if (data.ticksElapsed % 10 == 0) {
            tickAreaDebuffs(player, world, data);
        }

        // 环境粒子效果（每5tick）
        if (data.ticksElapsed % 5 == 0) {
            spawnAmbientParticles(world, data);
        }

        // 幽灵低语音效（每60tick / 3秒一次）
        if (data.ticksElapsed % 60 == 0) {
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.AMBIENT, 1.0f, 0.5f);
        }

        if (data.ticksElapsed >= duration) {
            // 维持结束，进入回退阶段
            data.phase = Phase.RETRACTING;
            data.ticksElapsed = 0;
            data.currentRadius = maxRadius;

            // 回退开始音效
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4f, 0.5f);
        }

        data.ticksElapsed++;
    }

    /** 回退阶段：从最远处开始以5格/秒速度向内还原方块 */
    private static void tickRetracting(ServerPlayerEntity player, ServerWorld world, DomainData data) {
        double prevRadius = data.currentRadius;
        data.currentRadius -= EXPAND_SPEED;

        if (data.currentRadius < 0) {
            data.currentRadius = 0;
        }

        // 还原大于当前半径的方块
        int currR = (int) Math.floor(data.currentRadius);
        int prevR = (int) Math.ceil(prevRadius);
        restoreBlocksInRing(world, data, currR, prevR);

        // 修正施法者位置：仅在被还原方块（非灵魂沙）埋住时传送
        // 灵魂沙的下陷是原版行为，不做干预
        BlockPos casterFeet = player.getBlockPos();
        BlockState casterFeetState = world.getBlockState(casterFeet);
        if (casterFeetState.isSolidBlock(world, casterFeet) && !casterFeetState.isOf(Blocks.SOUL_SAND)) {
            player.requestTeleport(player.getX(), casterFeet.getY() + 1.0, player.getZ());
        }

        // 修正其它生物位置，防止还原方块后陷入地面
        Box ringBox = new Box(
                data.center.getX() - prevR - 1, data.centerY - DOMAIN_HEIGHT, data.center.getZ() - prevR - 1,
                data.center.getX() + prevR + 2, data.centerY + DOMAIN_HEIGHT + 2, data.center.getZ() + prevR + 2
        );
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, ringBox, e -> e != player && e.isAlive())) {
            BlockPos entityFeetPos = entity.getBlockPos();
            BlockState entityFeetState = world.getBlockState(entityFeetPos);
            if (entityFeetState.isSolidBlock(world, entityFeetPos) && !entityFeetState.isOf(Blocks.SOUL_SAND)) {
                if (entity instanceof ServerPlayerEntity sp) {
                    sp.requestTeleport(sp.getX(), entityFeetPos.getY() + 1.0, sp.getZ());
                } else {
                    entity.setPosition(entity.getX(), entityFeetPos.getY() + 1.0, entity.getZ());
                }
            }
        }

        // 回退粒子效果
        spawnEdgeParticles(world, data);

        // 回退音效
        if (data.ticksElapsed % 5 == 0) {
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.BLOCKS, 0.4f, 0.9f);
        }

        if (data.currentRadius <= 0) {
            // 回退完成，还原所有剩余方块（包括距离0的中心点）
            restoreAllBlocks(world, data);
            // 还原完成后修正施法者位置，防止被埋在还原的方块内
            BlockPos finalFeet = player.getBlockPos();
            if (world.getBlockState(finalFeet).isSolidBlock(world, finalFeet)) {
                player.requestTeleport(player.getX(), finalFeet.getY() + 1.0, player.getZ());
            }
            cleanupDebuffs(world, data);
            ACTIVE_DOMAINS.remove(player.getUuid());

            // 结束音效
            player.getWorld().playSound(null, data.center.getX() + 0.5, (double) data.centerY, data.center.getZ() + 0.5,
                    SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.6f, 1.2f);
        }

        data.ticksElapsed++;
    }

    // ==================== 方块转化逻辑 ====================

    /**
     * 将指定环形区域内的方块转为灵魂沙
     */
    private static void convertBlocksInRing(ServerWorld world, DomainData data, int innerR, int outerR) {
        BlockPos center = data.center;
        int cy = data.centerY;
        int halfHeight = DOMAIN_HEIGHT;

        for (int dx = -outerR; dx <= outerR; dx++) {
            for (int dz = -outerR; dz <= outerR; dz++) {
                double distSq = dx * dx + dz * dz;
                int dist = (int) Math.ceil(Math.sqrt(distSq));

                // 只处理这一环的方块
                if (dist < innerR || dist > outerR || dist > DOMAIN_RADIUS) continue;

                for (int dy = -halfHeight; dy <= halfHeight; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);

                    if (shouldConvert(state)) {
                        // 用dist作为距离键
                        data.changedBlocksByDistance
                                .computeIfAbsent(dist, k -> new LinkedHashMap<>())
                                .putIfAbsent(pos, state);
                        world.setBlockState(pos, Blocks.SOUL_SAND.getDefaultState(), Block.NOTIFY_ALL);
                    } else if (shouldRemoveTemporarily(state)) {
                        // 非完整方块暂时消除
                        data.changedBlocksByDistance
                                .computeIfAbsent(dist, k -> new LinkedHashMap<>())
                                .putIfAbsent(pos, state);
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    /**
     * 还原实体周围的已转化方块，防止灵魂沙减速/陷入效果
     */
    private static void restoreBlocksAroundEntity(ServerWorld world, DomainData data, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    for (Map<BlockPos, BlockState> blocks : data.changedBlocksByDistance.values()) {
                        BlockState original = blocks.get(pos);
                        if (original != null) {
                            if (isStillConverted(world, pos, original)) {
                                world.setBlockState(pos, original, Block.NOTIFY_ALL);
                            }
                            blocks.remove(pos);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 还原指定环形区域内的方块
     */
    private static void restoreBlocksInRing(ServerWorld world, DomainData data, int innerR, int outerR) {
        // 从最外层到最内层还原
        for (int dist = outerR; dist > innerR; dist--) {
            Map<BlockPos, BlockState> blocks = data.changedBlocksByDistance.get(dist);
            if (blocks == null) continue;

            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                // 如果方块已被挖掉（不再是灵魂沙/空气），跳过还原
                if (isStillConverted(world, entry.getKey(), entry.getValue())) {
                    world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
                }
            }
            data.changedBlocksByDistance.remove(dist);
        }
    }

    /**
     * 强制还原所有方块
     */
    private static void restoreAllBlocks(ServerWorld world, DomainData data) {
        int restored = 0;
        int skipped = 0;
        for (Map<BlockPos, BlockState> blocks : data.changedBlocksByDistance.values()) {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (isStillConverted(world, entry.getKey(), entry.getValue())) {
                    world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
                    restored++;
                } else {
                    skipped++;
                }
            }
        }
        LOGGER.info("[DeathDomain] restoreAllBlocks: restored={}, skipped={}, world={}",
                restored, skipped, world.getRegistryKey().getValue());
        data.changedBlocksByDistance.clear();
    }

    /**
     * 检查该位置的方块是否仍为领域转化后的状态。
     * 转化为灵魂沙的方块→当前应该是灵魂沙；暂时消除的方块→当前应该是空气。
     * 如果被玩家挖掉或其他方式改变了，则返回false，不再还原。
     */
    private static boolean isStillConverted(ServerWorld world, BlockPos pos, BlockState originalState) {
        Block currentBlock = world.getBlockState(pos).getBlock();
        if (isFullBlock(originalState)) {
            // 原本是完整方块→被转化为灵魂沙，现在应当仍是灵魂沙
            return currentBlock == Blocks.SOUL_SAND;
        } else {
            // 原本是非完整方块→被暂时消除为空气，现在应当仍是空气
            return world.getBlockState(pos).isAir();
        }
    }

    /**
     * 判断方块是否应转化为灵魂沙（完整方块，非功能方块）
     */
    private static boolean shouldConvert(BlockState state) {
        Block block = state.getBlock();

        // 空气/液体不转化
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;

        // 基岩、刷怪笼等不可破坏方块不转化
        if (state.getHardness(null, null) < 0) return false;

        // 灵魂沙本身不转化
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) return false;

        // 有功能的方块不转化（存储/交互类）
        if (isFunctionalBlock(block)) return false;

        // 红石相关方块不转化
        if (isRedstoneComponent(block)) return false;

        // 有方向性/连接材质的方块不转化
        if (isDirectionalOrConnectedBlock(block)) return false;

        // 非完整方块走shouldRemoveTemporarily逻辑
        if (isNonFullButSolidBlock(state)) return false; // 类似农田的视作完整
        if (!isFullBlock(state)) return false;

        return true;
    }

    /**
     * 判断方块是否需要暂时消除（非完整方块，但不是功能方块，也不是"类完整"方块）
     */
    private static boolean shouldRemoveTemporarily(BlockState state) {
        Block block = state.getBlock();

        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getHardness(null, null) < 0) return false;
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) return false;
        if (isFunctionalBlock(block)) return false;

        // 红石相关方块不消除
        if (isRedstoneComponent(block)) return false;

        // 有方向性/连接材质的方块不消除
        if (isDirectionalOrConnectedBlock(block)) return false;

        // 类完整方块（农田、草径之类）不消除，走转化逻辑的shouldConvert已处理了类完整
        if (isNonFullButSolidBlock(state)) return false;

        // 非完整方块（花、灯笼等）暂时消除
        return !isFullBlock(state);
    }

    /**
     * 判断是否为有功能的方块（有容器、交互功能）
     */
    private static boolean isFunctionalBlock(Block block) {
        return block instanceof BlockEntityProvider ||  // 箱子、漏斗、熔炉、酿造台等所有有方块实体的
                block instanceof CraftingTableBlock ||   // 工作台
                block instanceof AnvilBlock ||           // 铁砧
                block instanceof GrindstoneBlock ||      // 砂轮
                block instanceof StonecutterBlock ||     // 切石机
                block instanceof CartographyTableBlock ||// 制图台
                block instanceof LoomBlock ||            // 织布机
                block instanceof SmithingTableBlock ||   // 锻造台
                block instanceof FletchingTableBlock ||  // 制箭台
                block instanceof BedBlock ||             // 床
                block instanceof DoorBlock ||            // 门
                block instanceof TrapdoorBlock ||        // 活板门
                block instanceof FenceGateBlock ||       // 栅栏门
                block instanceof LecternBlock ||         // 讲台
                block instanceof ComposterBlock ||       // 堆肥桶
                block instanceof BeehiveBlock ||         // 蜂巢
                block instanceof RespawnAnchorBlock ||   // 重生锚
                block instanceof CommandBlock ||         // 命令方块
                block instanceof StructureBlock ||       // 结构方块
                block instanceof JigsawBlock;            // 拼图方块
    }

    /**
     * 判断是否为红石相关方块（不应转化或消除）
     */
    private static boolean isRedstoneComponent(Block block) {
        return block instanceof PistonBlock ||              // 活塞/粘性活塞
                block instanceof PistonHeadBlock ||          // 活塞头
                block instanceof PistonExtensionBlock ||     // 移动中的活塞
                block instanceof RedstoneBlock ||            // 红石块
                block instanceof RedstoneWireBlock ||        // 红石粉
                block instanceof RedstoneTorchBlock ||       // 红石火把（站立+墙上）
                block instanceof AbstractRedstoneGateBlock ||// 中继器/比较器
                block instanceof ObserverBlock ||            // 侦测器
                block instanceof LeverBlock ||               // 拉杆
                block instanceof ButtonBlock ||              // 按钮
                block instanceof AbstractPressurePlateBlock ||// 压力板
                block instanceof TripwireBlock ||            // 绊线
                block instanceof TripwireHookBlock ||        // 绊线钩
                block instanceof TargetBlock ||              // 标靶
                block instanceof NoteBlock;                  // 音符盒
    }

    /**
     * 判断是否为有方向性或特殊连接材质的方块（不应转化或消除）
     */
    private static boolean isDirectionalOrConnectedBlock(Block block) {
        return block instanceof SlabBlock ||            // 半砖
                block instanceof StairsBlock ||          // 楼梯
                block instanceof FenceBlock ||           // 栅栏
                block instanceof WallBlock ||            // 墙
                block instanceof ChainBlock ||           // 锁链
                block instanceof PaneBlock ||            // 玻璃板、铁栏杆
                block instanceof AbstractRailBlock;       // 铁轨
    }

    /**
     * 判断是否为完整方块（碰撞箱填满整格）
     */
    private static boolean isFullBlock(BlockState state) {
        try {
            return state.isFullCube(null, BlockPos.ORIGIN);
        } catch (Exception e) {
            // 某些方块在null world下可能报错，用形状检查兜底
            return state.isOpaque();
        }
    }

    /**
     * 判断是否为"非完整但应视作完整"的方块（农田、草径等）
     * 这些方块碰撞箱稍小于1格但应被转化为灵魂沙而非消除
     */
    private static boolean isNonFullButSolidBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FarmlandBlock ||     // 农田 (15/16高)
                block instanceof DirtPathBlock ||    // 草径 (15/16高)
                block instanceof SlabBlock && state.get(Properties.SLAB_TYPE) == net.minecraft.block.enums.SlabType.DOUBLE || // 双台阶
                block instanceof SnowBlock && state.get(Properties.LAYERS) == 8; // 8层雪
    }

    // ==================== Debuff逻辑 ====================

    /**
     * 对领域范围内踩在灵魂沙上方3格内的生物施加debuff，离开时自动移除
     */
    private static void tickAreaDebuffs(ServerPlayerEntity player, ServerWorld world, DomainData data) {
        Box box = new Box(
                data.center.getX() - data.currentRadius, data.centerY - DOMAIN_HEIGHT, data.center.getZ() - data.currentRadius,
                data.center.getX() + data.currentRadius + 1, data.centerY + DOMAIN_HEIGHT + 1, data.center.getZ() + data.currentRadius + 1
        );

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive());

        Set<UUID> currentlyAffected = new HashSet<>();

        for (LivingEntity entity : entities) {
            // 检查是否在圆柱范围内
            double dx = entity.getX() - data.center.getX() - 0.5;
            double dz = entity.getZ() - data.center.getZ() - 0.5;
            if (dx * dx + dz * dz > data.currentRadius * data.currentRadius) continue;

            // 白名单检查
            if (WhitelistUtils.isProtected(player, entity)) continue;

            // 检查是否踩在灵魂沙上方3格以内
            if (!isAboveSoulSand(world, entity, 3)) continue;

            currentlyAffected.add(entity.getUuid());

            // 对受debuff个体施加鬼魂粒子效果
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                    entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
                    2, 0.2, 0.3, 0.2, 0.02);

            // 施加/刷新短时debuff
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, true, true));
            // 凋零仅在实体没有该效果且未达到伤害上限时施加；每次42tick周期造成1HP，上限10HP
            // 增强模式使用凋零II（amplifier=1）
            int witherAmplifier = data.enhanced ? ENHANCED_WITHER_AMPLIFIER : 0;
            int witherCount = data.witherHitCount.getOrDefault(entity.getUuid(), 0);
            if (witherCount < 10 && !entity.hasStatusEffect(StatusEffects.WITHER)) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 42, witherAmplifier, false, true, true));
                data.witherHitCount.put(entity.getUuid(), witherCount + 1);
            }

            // 首次进入领域灵魂沙区域时施加血量削减
            if (!data.debuffedEntities.contains(entity.getUuid())) {
                data.debuffedEntities.add(entity.getUuid());
                applyHealthReduction(entity);
            }
        }

        // 对离开灵魂沙区域的生物移除血量削减
        Iterator<UUID> it = data.debuffedEntities.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            if (!currentlyAffected.contains(uuid)) {
                // 在更大范围内查找该实体并移除血量修饰符
                for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                        box.expand(32), e -> e.getUuid().equals(uuid))) {
                    removeHealthReduction(entity);
                }
                it.remove();
            }
        }
    }

    /**
     * 检查实体是否站在灵魂沙上方指定格数以内
     */
    private static boolean isAboveSoulSand(ServerWorld world, LivingEntity entity, int maxDistance) {
        // 使用实体实际坐标计算脚下方块，兼容非整数Y坐标
        BlockPos feetPos = entity.getBlockPos();
        // 额外检查实体脚下一格（灵魂沙碰撞箱为14/16高，实体可能站在其上方同一格内）
        BlockPos belowFeet = BlockPos.ofFloored(entity.getX(), entity.getY() - 0.01, entity.getZ());
        for (int dy = 0; dy <= maxDistance; dy++) {
            // 同时从feetPos和belowFeet向下检查
            for (BlockPos base : new BlockPos[]{feetPos, belowFeet}) {
                BlockPos checkPos = base.down(dy);
                BlockState state = world.getBlockState(checkPos);
                if (state.isOf(Blocks.SOUL_SAND) || state.isOf(Blocks.SOUL_SOIL)) {
                    return true;
                }
            }
            // 脚下有非灵魂沙实心方块则停止向下检查
            if (dy > 0) {
                BlockState belowState = world.getBlockState(feetPos.down(dy));
                if (!belowState.isAir() && belowState.isSolidBlock(world, feetPos.down(dy))
                        && !belowState.isOf(Blocks.SOUL_SAND) && !belowState.isOf(Blocks.SOUL_SOIL)) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 对生物施加15%血量上限削减
     */
    private static void applyHealthReduction(LivingEntity entity) {
        EntityAttributeInstance healthAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr == null) return;

        // 移除旧的修饰符（防止叠加）
        healthAttr.removeModifier(HEALTH_MODIFIER_UUID);

        double maxHealth = healthAttr.getBaseValue();
        double reduction = -maxHealth * HEALTH_REDUCTION;

        EntityAttributeModifier modifier = new EntityAttributeModifier(
                HEALTH_MODIFIER_UUID,
                "Death Domain Health Reduction",
                reduction,
                EntityAttributeModifier.Operation.ADDITION
        );
        healthAttr.addPersistentModifier(modifier);

        // 如果当前血量超过新的上限，降至上限
        double newMax = entity.getMaxHealth();
        if (entity.getHealth() > newMax) {
            entity.setHealth((float) newMax);
        }
    }

    /**
     * 移除生物的血量上限削减
     */
    private static void removeHealthReduction(LivingEntity entity) {
        EntityAttributeInstance healthAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
        }
    }

    /**
     * 清理所有debuff（领域结束后）
     */
    private static void cleanupDebuffs(ServerWorld world, DomainData data) {
        for (UUID entityUuid : data.debuffedEntities) {
            // 尝试找到实体并移除血量修饰符
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                    new Box(data.center.getX() - DOMAIN_RADIUS - 32, data.centerY - DOMAIN_HEIGHT - 32,
                            data.center.getZ() - DOMAIN_RADIUS - 32,
                            data.center.getX() + DOMAIN_RADIUS + 32, data.centerY + DOMAIN_HEIGHT + 32,
                            data.center.getZ() + DOMAIN_RADIUS + 32),
                    e -> e.getUuid().equals(entityUuid))) {
                removeHealthReduction(entity);
            }
        }
        data.debuffedEntities.clear();
    }

    // ==================== 充能减速 ====================

    private static void applyChargeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;
        speedAttr.removeModifier(CHARGE_SLOW_UUID);
        EntityAttributeModifier modifier = new EntityAttributeModifier(
                CHARGE_SLOW_UUID,
                "Death Domain Charge Slow",
                CHARGE_SLOW_FACTOR - 1.0, // -0.7 = 减速70%
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        speedAttr.addTemporaryModifier(modifier);
    }

    private static void removeChargeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CHARGE_SLOW_UUID);
        }
    }

    // ==================== 粒子效果 ====================

    /** 在领域边缘生成灵魂粒子 */
    private static void spawnEdgeParticles(ServerWorld world, DomainData data) {
        double r = data.currentRadius;
        if (r < 1) return;
        // 蓝火粒子（减少）
        int flameCount = Math.min((int) (r * 2), 32);
        for (int i = 0; i < flameCount; i++) {
            double angle = (Math.PI * 2 * i) / flameCount;
            double px = data.center.getX() + 0.5 + Math.cos(angle) * r;
            double pz = data.center.getZ() + 0.5 + Math.sin(angle) * r;
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL_FIRE_FLAME,
                    px, data.centerY + 1, pz, 1, 0, 0.3, 0, 0.01);
        }
        // 鬼魂粒子
        int soulCount = Math.min((int) (r * 1.5), 24);
        for (int i = 0; i < soulCount; i++) {
            double angle = (Math.PI * 2 * i) / soulCount;
            double px = data.center.getX() + 0.5 + Math.cos(angle) * r;
            double pz = data.center.getZ() + 0.5 + Math.sin(angle) * r;
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                    px, data.centerY + 0.5, pz, 1, 0.2, 0.4, 0.2, 0.02);
        }
    }

    /** 领域维持时的环境粒子 */
    private static void spawnAmbientParticles(ServerWorld world, DomainData data) {
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * DOMAIN_RADIUS;
            double px = data.center.getX() + 0.5 + Math.cos(angle) * dist;
            double pz = data.center.getZ() + 0.5 + Math.sin(angle) * dist;
            double py = data.centerY + random.nextDouble() * 3;
            ParticleUtils.spawnParticles(world, ParticleTypes.SOUL,
                    px, py, pz, 1, 0.5, 0.5, 0.5, 0.02);
        }
    }
}
