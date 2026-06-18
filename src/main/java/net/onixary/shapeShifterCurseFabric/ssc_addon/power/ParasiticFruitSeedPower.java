/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 寄生果蝠主动技能：灵果寄生。
 * 命中生物后在目标身上种下灵果种子，种子根据宿主的友敌关系与状态周期性结出自适应果实。
 * 全部判定与状态效果均在服务端执行，确保多人环境主客机一致。
 */
public class ParasiticFruitSeedPower extends ActiveCooldownPower {
    private static final int MAX_SEEDS = 3;
    private static final int ROOTING_TICKS = 20;
    private static final int FRUIT_INTERVAL_TICKS = 25;
    private static final int DEFAULT_LIFE_TICKS = 240;
    private static final int ENERGY_COST = 1;

    private static final DustParticleEffect FRIEND_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 0.30f), 1.1f);
    private static final DustParticleEffect ENEMY_DUST = new DustParticleEffect(new Vector3f(0.55f, 0.10f, 0.75f), 1.1f);
    private static final DustParticleEffect SEED_DUST = new DustParticleEffect(new Vector3f(0.95f, 0.72f, 0.24f), 1.0f);

    private final int cooldownTicks;
    private long internalCooldownEndTime = 0L;
    private final LinkedHashMap<UUID, SeedData> seeds = new LinkedHashMap<>();

    /** 腐殖之戒：当前结果的时长系数（bearFruit 临时设置，scaleDuration 读取，结束复位 1.0） */
    private transient float currentHumusFactor = 1.0f;

    /**
     * 全局表：当前被「敌方削弱果」寄生的宿主 UUID -> 失效世界 tick。
     * 由所有果蝠玩家的 tick() 实时维护（host 当前判定为敌方时写入，种子失效/转友方时清理）。
     * 供 LivingEntity.heal 拦截查询，实现「被削弱果寄生的敌人回血 -50%」被动。
     * 用绝对 endTick 兜底：即使某果蝠玩家下线导致 tick 停摆，过期后查询也会判定失效。
     */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> ENEMY_PARASITIZED_HOSTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 查询某生物当前是否被任意果蝠的敌方削弱果寄生（用于回血削减被动）。
     * 需传入世界当前 tick 以判定 endTick 是否已过期。
     */
    public static boolean isParasitizedByEnemyFruit(UUID hostUuid, long worldTime) {
        Long end = ENEMY_PARASITIZED_HOSTS.get(hostUuid);
        if (end == null) return false;
        if (worldTime >= end) {
            ENEMY_PARASITIZED_HOSTS.remove(hostUuid);
            return false;
        }
        return true;
    }

    public ParasiticFruitSeedPower(PowerType<?> type, LivingEntity entity, int cooldownTicks, int lifeTicks,
                                   HudRender hudRender, Active.Key key) {
        super(type, entity, cooldownTicks, hudRender, (e) -> {
        });
        this.cooldownTicks = cooldownTicks;
        this.setKey(key);
        this.setTicking(true);
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(new Identifier("my_addon", "parasitic_fruit_seed"),
                new SerializableData()
                        .add("cooldown", SerializableDataTypes.INT, 200)
                        .add("duration", SerializableDataTypes.INT, DEFAULT_LIFE_TICKS)
                        .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                        .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
                data ->
                        (type, player) -> new ParasiticFruitSeedPower(
                                type,
                                player,
                                data.getInt("cooldown"),
                                data.getInt("duration"),
                                data.get("hud_render"),
                                data.get("key")
                        )
        ).allowCondition();
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public void onUse() {
        if (!(entity instanceof ServerPlayerEntity caster)) return;
        if (entity.getWorld().isClient) return;
        if (entity.hasStatusEffect(SscAddon.PURIFIED)) return;
        if (!isInternalCooldownReady()) return;

        // 双生种荚：一次播种额外寄生最近的第二目标，但能量消耗翻倍、冷却 +1 秒
        boolean twinPod = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(caster)
                .map(c -> c.isEquipped(SscAddon.TWIN_POD))
                .orElse(false);
        int energyCost = twinPod ? ENERGY_COST * 2 : ENERGY_COST;

        // 能量检查：不足则释放失败
        if (!PowerUtils.hasResource(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, energyCost)) {
            playFailFeedback(caster);
            return;
        }

        // 改为抛物线投掷：发射灵果种子投掷物。命中生物由投掷物回调 plantSeed；未命中落地后续生成种子圈。
        PowerUtils.changeResourceValueAndSync(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, -energyCost);
        launchSeedProjectile(caster, twinPod);
        if (twinPod) {
            // 双生种荷：命中扩散（额外 1 人，无人叠 2 层），冷却 +1 秒
            applyCooldown(cooldownTicks + 20);
        } else {
            applyCooldown(cooldownTicks);
        }
    }

    /** 发射一颗灵果种子投掷物（抛物线，速度同孢子炸弹）。 */
    private void launchSeedProjectile(ServerPlayerEntity caster, boolean twinPod) {
        net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile seed =
                new net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile(caster.getWorld(), caster);
        seed.setItem(net.minecraft.item.Items.WHEAT_SEEDS.getDefaultStack());
        seed.setOwner(caster);
        seed.setTwinPod(twinPod);
        seed.setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        seed.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0.0f, 1.4f, 0.4f);
        Vec3d ownerVel = caster.getVelocity();
        seed.setVelocity(seed.getVelocity().add(ownerVel.x, caster.isOnGround() ? 0.0 : ownerVel.y, ownerVel.z));
        caster.getWorld().spawnEntity(seed);
        caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 1.5f);
    }


    @Override
    public void tick() {
        super.tick();
        if (!(entity instanceof ServerPlayerEntity caster)) return;
        if (entity.getWorld().isClient) return;
        if (seeds.isEmpty()) return;

        long now = entity.getWorld().getTime();
        Iterator<Map.Entry<UUID, SeedData>> iterator = seeds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SeedData> entry = iterator.next();
            SeedData seed = entry.getValue();
            LivingEntity host = findLiving(caster.getServer(), entry.getKey());
            // 跨维度宿主视为脱离作用范围，主动失效
            if (host == null || !host.isAlive() || now >= seed.endTick
                    || host.getWorld() != caster.getWorld()) {
                if (host != null) {
                    net.onixary.shapeShifterCurseFabric.ssc_addon.util.GlowMarker.unmark(host);
                }
                ENEMY_PARASITIZED_HOSTS.remove(entry.getKey());
                iterator.remove();
                continue;
            }
            // 维护全局敌方寄生表：宿主当前为敌方（非友方且非白名单保护）时登记其失效 tick，
            // 供 heal 拦截实现「被削弱果寄生的敌人回血 -50%」；转为友方/受保护则立即解除登记。
            if (!WhitelistUtils.isBuffTarget(caster, host) && !WhitelistUtils.isProtected(caster, host)) {
                ENEMY_PARASITIZED_HOSTS.put(entry.getKey(), seed.endTick);
            } else {
                ENEMY_PARASITIZED_HOSTS.remove(entry.getKey());
            }
            spawnAttachedSeedParticles(caster, host, seed, now);
            if (now >= seed.nextFruitTick) {
                seed.nextFruitTick = now + FRUIT_INTERVAL_TICKS;
                bearFruit(caster, host, seed);
            }
        }
    }

    @Override
    public void onLost() {
        super.onLost();
        // 清理本机制留下的 outline 高光
        if (entity instanceof ServerPlayerEntity caster) {
            for (UUID uuid : seeds.keySet()) {
                LivingEntity host = findLiving(caster.getServer(), uuid);
                if (host != null) {
                    net.onixary.shapeShifterCurseFabric.ssc_addon.util.GlowMarker.unmark(host);
                }
            }
        }
        // 一并解除本玩家种子覆盖的敌方寄生登记，避免换形态/掉线后残留导致目标永久 -50% 回血
        for (UUID uuid : seeds.keySet()) {
            ENEMY_PARASITIZED_HOSTS.remove(uuid);
        }
        seeds.clear();
    }

    private boolean isInternalCooldownReady() {
        return entity.getWorld().getTime() >= internalCooldownEndTime;
    }

    private void applyCooldown(int ticks) {
        internalCooldownEndTime = entity.getWorld().getTime() + ticks;
        // 同步给父类冷却体系，避免与 Apoli 自身的 isActive() 状态脱节
        this.use();
        if (entity instanceof ServerPlayerEntity caster) {
            PowerUtils.setResourceValueAndSync(caster, FormIdentifiers.SP_PRIMARY_CD, ticks);
        }
    }

    /**
     * 在宿主身上种下灵果种子（投掷物命中 / 种子圈拾取统一入口）。
     * public 供 ParasiticSeedProjectile 等外部回调。
     */
    public void plantSeed(ServerPlayerEntity caster, LivingEntity host) {
        // 种子寿命（=叮声/buff 总持续）：未交战 15s(300t) / 交战 5s(100t)
        plantSeed(caster, host,
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticCombatTracker.isInCombat(host) ? 100 : 300);
    }

    /** 带自定义基础时长的种植（种子圈拾取传固定时长）。 */
    public void plantSeed(ServerPlayerEntity caster, LivingEntity host, int baseLife) {
        long now = caster.getWorld().getTime();
        int adjustedLife = baseLife;
        SeedData seed = seeds.get(host.getUuid());
        if (seed != null) {
            // 同一宿主：堆叠 1 层（封顶 MAX_SEEDS=3）；重复命中在剩余时长基础上叠加（无论敌友）
            seed.stack = Math.min(MAX_SEEDS, seed.stack + 1);
            seed.endTick = Math.max(seed.endTick, now) + adjustedLife;
            seed.nextFruitTick = now + ROOTING_TICKS;
        } else {
            cleanupExpiredSeeds(caster, now);
            // 超过同时宿主上限时移除最早一个（FIFO）
            // 注：MAX_SEEDS 同时作为“同一宿主堆叠上限”与“独立宿主上限”，保持设计简化
            if (seeds.size() >= MAX_SEEDS) {
                Iterator<UUID> iterator = seeds.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            seeds.put(host.getUuid(), new SeedData(now + adjustedLife, now + ROOTING_TICKS));
        }

        if (host.getWorld() instanceof ServerWorld world) {
            int finalStack = seeds.get(host.getUuid()).stack;
            ParticleUtils.spawnParticles(world, SEED_DUST,
                    host.getX(), host.getY() + host.getHeight() * 0.65, host.getZ(),
                    18 + finalStack * 6, 0.25, 0.35, 0.25, 0.02);
            // 叮声仅施法者本人听见（不广播给其他玩家）
            net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager.playSoundToPlayer(
                    caster, SoundEvents.BLOCK_GRASS_PLACE, 0.9f, 1.5f);
        }
    }

    /**
     * 静态回调入口：让 caster 当前持有的灵果寄生 power 在 host 身上种下种子。
     * 供投掷物命中、种子圈拾取等外部场景调用（多人安全：仅服务端、按持有者实例分发）。
     */
    public static void plantSeedFrom(ServerPlayerEntity caster, LivingEntity host) {
        if (caster == null || host == null || !host.isAlive()) return;
        for (ParasiticFruitSeedPower power :
                io.github.apace100.apoli.component.PowerHolderComponent.getPowers(caster, ParasiticFruitSeedPower.class)) {
            power.plantSeed(caster, host);
        }
    }

    /**
     * 双生种荷扩散：命中宿主后，向其 4 格内额外寄生最近 1 个目标；
     * 若附近无其它可寄生目标，则给命中宿主叠 2 层。非双生种荷时退化为普通单层寄生。
     */
    public static void plantSeedSpread(ServerPlayerEntity caster, LivingEntity host, boolean twinPod) {
        if (caster == null || host == null || !host.isAlive()) return;
        if (!twinPod) {
            plantSeedFrom(caster, host);
            return;
        }
        LivingEntity extra = findNearbyOther(caster, host);
        if (extra != null) {
            plantSeedFrom(caster, host);
            plantSeedFrom(caster, extra);
        } else {
            // 附近无额外目标：给命中宿主叠 2 层
            plantSeedFrom(caster, host);
            plantSeedFrom(caster, host);
        }
    }

    /** 在 primary 周围 4 格内查找最近的另一个可寄生生物（排除施法者与 primary 本身）。 */
    private static LivingEntity findNearbyOther(ServerPlayerEntity caster, LivingEntity primary) {
        net.minecraft.util.math.Box box = primary.getBoundingBox().expand(4.0D);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity e : caster.getWorld().getEntitiesByClass(LivingEntity.class, box,
                living -> living.isAlive() && living != caster && living != primary)) {
            double d = e.squaredDistanceTo(primary);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = e;
            }
        }
        return best;
    }

    /** 带自定义时长的静态回调（种子圈拾取用，时长 = 剩余）。 */
    public static void plantSeedFrom(ServerPlayerEntity caster, LivingEntity host, int baseLife) {
        if (caster == null || host == null || !host.isAlive()) return;
        for (ParasiticFruitSeedPower power :
                io.github.apace100.apoli.component.PowerHolderComponent.getPowers(caster, ParasiticFruitSeedPower.class)) {
            power.plantSeed(caster, host, baseLife);
        }
    }

    private void cleanupExpiredSeeds(ServerPlayerEntity caster, long now) {
        Iterator<Map.Entry<UUID, SeedData>> iterator = seeds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SeedData> entry = iterator.next();
            LivingEntity host = findLiving(caster.getServer(), entry.getKey());
            if (host == null || !host.isAlive() || now >= entry.getValue().endTick) {
                iterator.remove();
            }
        }
    }


    private void bearFruit(ServerPlayerEntity caster, LivingEntity host, SeedData seed) {
        boolean friend = WhitelistUtils.isBuffTarget(caster, host);
        // 腐殖之戒：装备时敌方削弱果时长 ×1.5、友方增益果时长 ×0.7；未装备 ×1.0。
        boolean humus = dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(caster)
                .map(c -> c.isEquipped(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.HUMUS_RING))
                .orElse(false);
        if (friend) {
            this.currentHumusFactor = humus ? 0.7f : 1.0f;
            applyFriendBuff(host, host == caster, seed.stack);
            spawnFruitParticles(host, FRIEND_DUST, seed.stack);
        } else if (!WhitelistUtils.isProtected(caster, host)) {
            this.currentHumusFactor = humus ? 1.5f : 1.0f;
            EnemyFruit fruit = selectEnemyFruit(host);
            applyEnemyFruit(host, fruit, seed.stack);
            spawnFruitParticles(host, ENEMY_DUST, seed.stack);
        }
        this.currentHumusFactor = 1.0f;
    }

    private EnemyFruit selectEnemyFruit(LivingEntity host) {
        if (host.isSprinting() || host.getVelocity().horizontalLengthSquared() > 0.08) {
            return EnemyFruit.VINE;
        }
        if (host.handSwinging || (host instanceof MobEntity mob && mob.getTarget() != null)) {
            return EnemyFruit.BITTER;
        }
        if (host.getHealth() / host.getMaxHealth() > 0.7f || host.getArmor() >= 10) {
            return EnemyFruit.ROTTEN;
        }
        return EnemyFruit.SOUR;
    }

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit, int stack) {
        applyEnemyFruit(target, fruit, 1, stack);
    }

    /** 友方果实效果重写：交战/未交战 × 血量分级（替代旧 selectFriendFruit/applyFriendFruit）。 */
    private void applyFriendBuff(LivingEntity target, boolean self, int stack) {
        boolean inCombat = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticCombatTracker.isInCombat(target);
        float hpRatio = target.getHealth() / Math.max(1.0f, target.getMaxHealth());
        // buff 时长 = 叮声间隔 + 0.2s = 44t，再乘腐殖之戒系数（装备时友军增益 ×0.7）
        int buffDur = Math.max(10, Math.round((FRUIT_INTERVAL_TICKS + 4) * currentHumusFactor));
        if (!inCombat) {
            if (hpRatio > 0.7f) {
                // 未交战 + 高血：急迫 I
                addEffect(target, StatusEffects.HASTE, buffDur, 0);
            } else {
                // 未交战 + 低血：回血 1 心
                healWithFx(target, 1);
            }
        } else {
            if (hpRatio > 0.8f) {
                // 交战 + 高血：迅捷 II
                addEffect(target, StatusEffects.SPEED, buffDur, 1);
            } else if (hpRatio >= 0.5f) {
                // 交战 + 中血：伤害吸收 I（自定义累加黄心，持续受腐殖之戒系数）+ 生命恢复（1 心）
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticAbsorptionManager.addAbsorption(target, 0, currentHumusFactor);
                healWithFx(target, 1);
            } else {
                // 交战 + 低血：伤害吸收 II（自定义累加黄心，持续受腐殖之戒系数）+ 生命恢复（2 心）
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticAbsorptionManager.addAbsorption(target, 1, currentHumusFactor);
                healWithFx(target, 2);
            }
        }
    }

    /** 立即回血（触发血条回血动画）+ 生命恢复图标显示。 */
    private void healWithFx(LivingEntity target, int hearts) {
        target.heal(hearts * 2.0f);
        // 专属「生命恢复」buff：立即回血由上面 heal 完成（血条动画），此 buff 仅显示图标、不周期回血（只回一次）
        addEffect(target, SscAddon.BAT_REGEN, 60, hearts - 1);
    }

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit, int divisor, int stack) {
        int amp = MathHelper.clamp(stack - 1, 0, 2);
        switch (fruit) {
            case VINE -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(60, divisor, 1.0f), amp);
                if (!target.isOnGround()) {
                    Vec3d velocity = target.getVelocity();
                    target.setVelocity(velocity.x * 0.75, Math.min(velocity.y, -0.08), velocity.z * 0.75);
                    target.velocityModified = true;
                }
            }
            case BITTER -> {
                addEffect(target, StatusEffects.WEAKNESS, scaleDuration(80, divisor, 1.0f), amp);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(80, divisor, 1.0f), 0);
            }
            case ROTTEN -> {
                if (target.getGroup() == EntityGroup.UNDEAD) {
                    addEffect(target, StatusEffects.SLOWNESS, scaleDuration(70, divisor, 1.0f), amp);
                    addEffect(target, StatusEffects.WEAKNESS, scaleDuration(70, divisor, 1.0f), amp);
                } else {
                    addEffect(target, StatusEffects.POISON, scaleDuration(60, divisor, 1.0f), amp);
                }
            }
            case SOUR -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(40, divisor, 1.0f), amp);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(60, divisor, 1.0f), 0);
            }
        }
    }

    private void addEffect(LivingEntity target, StatusEffect effect, int duration, int amplifier) {
        if (duration <= 0) return;
        target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, false, true, true));
    }

    private int scaleDuration(int duration, int divisor, float multiplier) {
        // 腐殖之戒：敌方果时长 +50% / 友方果时长 -30%（由 bearFruit 在调用前设置本系数）
        float total = multiplier * currentHumusFactor;
        // 每次触发的药水效果统一额外延长 0.3 秒（+6 tick）
        return Math.max(10, Math.round(duration * total / Math.max(1, divisor)) + 6);
    }

    private LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            Entity found = world.getEntity(uuid);
            if (found instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private void spawnAttachedSeedParticles(ServerPlayerEntity caster, LivingEntity host, SeedData seed, long now) {
        if (!(host.getWorld() instanceof ServerWorld world)) return;
        boolean friend = WhitelistUtils.isBuffTarget(caster, host);
        // 满层 outline 高光：通过 scoreboard team 染色 + 短时长 GLOWING 状态实现客户端描边。
        // 优先级低于次要技能：被次要技能感染的目标让位（不画绿/红 outline）。
        boolean infected = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager.isInfected(host.getUuid());
        if (seed.stack >= MAX_SEEDS && !infected) {
            if (friend) {
                net.onixary.shapeShifterCurseFabric.ssc_addon.util.GlowMarker.markFriend(host);
            } else {
                net.onixary.shapeShifterCurseFabric.ssc_addon.util.GlowMarker.markEnemy(host);
            }
        } else {
            net.onixary.shapeShifterCurseFabric.ssc_addon.util.GlowMarker.unmark(host);
        }
        if (now % 10 != 0) return;
        ParticleEffect particle = friend ? FRIEND_DUST : ENEMY_DUST;
        // 粒子量随堆叠层数增加，仅作为附着指示，不再用于"满层光环"
        int count = 2 * seed.stack;
        ParticleUtils.spawnParticles(world, particle,
                host.getX(), host.getY() + host.getHeight() * 0.75, host.getZ(),
                count, 0.18, 0.25, 0.18, 0.0);
    }

    private void spawnFruitParticles(LivingEntity host, ParticleEffect particle, int stack) {
        if (!(host.getWorld() instanceof ServerWorld world)) return;
        double y = host.getY() + host.getHeight() * 0.75;
        int extra = stack * 4;
        ParticleUtils.spawnParticles(world, particle, host.getX(), y, host.getZ(),
                16 + extra, 0.35, 0.45, 0.35, 0.02);
        ParticleUtils.spawnParticles(world, SEED_DUST, host.getX(), y, host.getZ(),
                8 + extra / 2, 0.25, 0.35, 0.25, 0.01);
        world.playSound(null, host.getX(), host.getY(), host.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.35f, 1.8f);
    }

    private void playFailFeedback(ServerPlayerEntity caster) {
        ServerWorld world = caster.getServerWorld();
        world.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 1.7f);
    }

    private enum EnemyFruit {
        VINE,
        BITTER,
        ROTTEN,
        SOUR
    }

    private static class SeedData {
        private long endTick;
        private long nextFruitTick;
        /** 堆叠层数 1～3，决定资源加成与粒子密度 */
        private int stack = 1;

        private SeedData(long endTick, long nextFruitTick) {
            this.endTick = endTick;
            this.nextFruitTick = nextFruitTick;
        }
    }
}