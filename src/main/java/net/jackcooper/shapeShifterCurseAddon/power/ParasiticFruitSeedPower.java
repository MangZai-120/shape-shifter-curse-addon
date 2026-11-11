/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.UUID;

import net.jackcooper.shapeShifterCurseAddon.ability.fruit.*;
import net.jackcooper.shapeShifterCurseAddon.state.RegParasiticFruitStateComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;

/** Fruit effects and throw settlement; shared layers and input live in ability.fruit. */
public class ParasiticFruitSeedPower extends ActiveCooldownPower implements net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder {
    private static final int MAX_SEEDS = 3;
    private static final int ROOTING_TICKS = 20;
    private static final int FRUIT_INTERVAL_TICKS = 25;
    private static final int DEFAULT_LIFE_TICKS = 240;
    private static final int ENERGY_COST = 1;

    /** balance 快照读取（快照未初始化回退默认常量） */
    private static final BalanceReader BAL = new BalanceReader("abilities.parasitic_seed_power");

    private static final DustParticleEffect FRIEND_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 0.30f), 1.1f);
    private static final DustParticleEffect ENEMY_DUST = new DustParticleEffect(new Vector3f(0.55f, 0.10f, 0.75f), 1.1f);
    private static final DustParticleEffect SEED_DUST = new DustParticleEffect(new Vector3f(0.95f, 0.72f, 0.24f), 1.0f);

    private final net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec;
    private final int cooldownTicks;
    private long internalCooldownEndTime = 0L;
    private transient float currentHumusFactor = 1;
    public static boolean isParasitizedByEnemyFruit(UUID hostUuid, long worldTime) {
        return ParasiticFruitLayers.isEnemyParasitized(hostUuid, worldTime);
    }
    public ParasiticFruitSeedPower(PowerType<?> type, LivingEntity entity,
                                   net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec, int lifeTicks,
                                   HudRender hudRender, Active.Key key) {
        super(type, entity, Math.max(1, spec.cooldown()), hudRender, (e) -> {
        });
        this.spec = spec;
        this.cooldownTicks = spec.cooldown();
        this.setKey(key);
        this.setTicking(true);
    }

    @Override
    public net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec cooldownSpec() {
        return spec;
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(new Identifier("my_addon", "parasitic_fruit_seed"),
                net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.addFields(new SerializableData()
                        .add("duration", SerializableDataTypes.INT, DEFAULT_LIFE_TICKS)
                        .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                        .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
                        200, SkillCastManager.START_ON_RELEASE),
                data -> {
                    var spec = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.read(data);
                    return (type, player) -> new ParasiticFruitSeedPower(type, player, spec,
                            data.getInt("duration"), data.get("hud_render"), data.get("key"));
                }
        ).allowCondition();
    }

    @Override
    public boolean canUse() {
        return true;
    }

    // The custom server-owned gesture handles presses. Apoli must never throw on key-down.
    @Override public void onUse() { }
    public boolean throwChargedSeed(ServerPlayerEntity caster, int ticks, FruitSelection selection) {
        if (!FruitSelection.canRelease(ticks) || !isActive() || !isInternalCooldownReady()
                || AbilityUseGuard.blocked(caster) || caster.hasStatusEffect(SscAddon.PURIFIED)
                || !FormUtils.isForm(caster, FormIdentifiers.BAT_PARASITIC_FRUIT)) return false;
        boolean twinPod = TrinketUtils.isWearing(caster, SscAddon.TWIN_POD);
        int cost = BAL.i("energy_cost", ENERGY_COST) * (twinPod ? 2 : 1);
        if (!PowerUtils.hasResource(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, cost)) {
            playFailFeedback(caster); return false;
        }
        var seed = new net.jackcooper.shapeShifterCurseAddon.entity.ParasiticSeedProjectile(caster.getWorld(), caster);
        seed.setItem(net.minecraft.item.Items.WHEAT_SEEDS.getDefaultStack());
        seed.setTwinPod(twinPod); seed.setSelection(selection);
        seed.setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        // Vanilla already adds caster velocity once.
        seed.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0, FruitSelection.speed(ticks), 0.4f);
        boolean spawned = false;
        PowerUtils.changeResourceValueAndSync(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, -cost);
        try { spawned = caster.getWorld().spawnEntity(seed); }
        finally {
            if (!spawned) PowerUtils.changeResourceValueAndSync(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, cost);
        }
        if (!spawned) return false;
        applyCooldown(twinPod ? spec.extra("twin_pod") : cooldownTicks);
        caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 1.5f);
        return true;
    }
    @Override public void onLost() {
        super.onLost();
        if (entity instanceof ServerPlayerEntity caster) {
            ParasiticFruitAbility.cancel(caster); ParasiticFruitLayers.removeOwner(caster);
        }
    }
    private boolean isInternalCooldownReady() {
        if (entity instanceof ServerPlayerEntity sp) {
            return net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(sp, powerIdentifier());
        }
        return entity.getWorld().getTime() >= internalCooldownEndTime;
    }

    private void applyCooldown(int ticks) {
        internalCooldownEndTime = entity.getWorld().getTime() + ticks;
        // 同步给父类冷却体系，避免与 Apoli 自身的 isActive() 状态脱节
        this.use();
        if (entity instanceof ServerPlayerEntity caster) {
            net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.force(caster, powerIdentifier(), ticks);
        }
    }

    /** 本 power 的稳定技能 ID（= power 注册路径）。 */
    public String powerIdentifier() {
        return type != null && type.getIdentifier() != null
                ? type.getIdentifier().toString() : "my_addon:parasitic_fruit_seed";
    }

    public int fruitInterval() { return Math.max(1, BAL.i("fruit_interval_ticks", FRUIT_INTERVAL_TICKS)); }
    public void plantSeed(ServerPlayerEntity caster, LivingEntity host) { plantSeed(caster, host, selection(caster)); }
    public void plantSeed(ServerPlayerEntity caster, LivingEntity host, FruitSelection selected) {
        plantSeed(caster, host, net.jackcooper.shapeShifterCurseAddon.ability.ParasiticCombatTracker.isInCombat(host)
                ? BAL.i("life_combat", 100) : BAL.i("life_peace", 300), selected);
    }
    public void plantSeed(ServerPlayerEntity caster, LivingEntity host, int life) {
        plantSeed(caster, host, life, selection(caster));
    }
    private void plantSeed(ServerPlayerEntity caster, LivingEntity host, int life, FruitSelection selected) {
        if (!isValidSeedHost(caster, host)) return;
        FruitSelection fruit = selected == FruitSelection.AUTO
                ? FruitSelection.valueOf(selectEnemyFruit(host).name()) : selected;
        int total = ParasiticFruitLayers.plant(caster, host, fruit, life,
                BAL.i("rooting_ticks", ROOTING_TICKS), BAL.i("max_seeds", MAX_SEEDS));
        if (host.getWorld() instanceof ServerWorld world) {
            ParticleUtils.spawnParticles(world, SEED_DUST, host.getX(), host.getY() + host.getHeight() * 0.65,
                    host.getZ(), 18 + total * 6, 0.25, 0.35, 0.25, 0.02);
            net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkManager.playSoundToPlayer(
                    caster, SoundEvents.BLOCK_GRASS_PLACE, 0.9f, 1.5f);
        }
    }
    public static FruitSelection selection(ServerPlayerEntity caster) {
        return RegParasiticFruitStateComponent.STATE.maybeGet(caster)
                .map(c -> c.selection()).orElse(FruitSelection.AUTO);
    }
    public static ParasiticFruitSeedPower get(ServerPlayerEntity caster) {
        return io.github.apace100.apoli.component.PowerHolderComponent.getPowers(caster, ParasiticFruitSeedPower.class)
                .stream().findFirst().orElse(null);
    }
    public static void plantSeedFrom(ServerPlayerEntity caster, LivingEntity host) { plantSeedFrom(caster, host, selection(caster)); }
    public static void plantSeedFrom(ServerPlayerEntity caster, LivingEntity host, int life) {
        var power = get(caster); if (power != null) power.plantSeed(caster, host, life);
    }
    public static void plantSeedFrom(ServerPlayerEntity caster, LivingEntity host, FruitSelection selected) {
        var power = get(caster); if (power != null) power.plantSeed(caster, host, selected);
    }
    public static void plantSeedSpread(ServerPlayerEntity caster, LivingEntity host, boolean twinPod) {
        plantSeedSpread(caster, host, twinPod, selection(caster));
    }
    public static void plantSeedSpread(ServerPlayerEntity caster, LivingEntity host, boolean twinPod, FruitSelection selected) {
        if (!isValidSeedHost(caster, host)) return;
        plantSeedFrom(caster, host, selected);
        if (twinPod) {
            LivingEntity extra = findNearbyOther(caster, host);
            plantSeedFrom(caster, extra == null ? host : extra, selected);
        }
    }
    private static LivingEntity findNearbyOther(ServerPlayerEntity caster, LivingEntity primary) {
        net.minecraft.util.math.Box box = primary.getBoundingBox().expand(4.0D);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity e : caster.getWorld().getEntitiesByClass(LivingEntity.class, box,
                living -> living != caster && living != primary && isValidSeedHost(caster, living))) {
            double d = e.squaredDistanceTo(primary);
            if (d <= 16.0 && d < bestDistSq) {
                bestDistSq = d;
                best = e;
            }
        }
        return best;
    }

    /** Visual marker stands, spectators and targets in another world/domain cannot consume a seed. */
    public static boolean isValidSeedHost(ServerPlayerEntity caster, LivingEntity host) {
        return caster != null && host != null && host.isAlive() && !host.isSpectator()
                && !(host instanceof net.minecraft.entity.decoration.ArmorStandEntity)
                && caster.getWorld() == host.getWorld()
                && !net.jackcooper.shapeShifterCurseAddon.spell.DomainManager.blocksTargeting(caster, host);
    }

    public void harvest(ServerPlayerEntity caster, LivingEntity host, FruitSelection kind, int stack, boolean friend) {
        boolean humus = TrinketUtils.isWearing(caster, SscAddon.HUMUS_RING);
        currentHumusFactor = humus ? (friend ? 0.7f : 1.5f) : 1.0f;
        try {
            if (friend) applyFriendBuff(host, caster, 1);
            else applyEnemyFruit(host, EnemyFruit.valueOf(kind.name()), stack, caster);
            spawnFruitParticles(host, friend ? FRIEND_DUST : ENEMY_DUST, stack);
        } finally { currentHumusFactor = 1.0f; }
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

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit, int stack, ServerPlayerEntity caster) {
        applyEnemyFruit(target, fruit, 1, stack, caster);
    }

    /** 友方果实效果重写：交战/未交战 × 血量分级（替代旧 selectFriendFruit/applyFriendFruit）。 */
    private void applyFriendBuff(LivingEntity target, ServerPlayerEntity caster, int stack) {
        boolean inCombat = net.jackcooper.shapeShifterCurseAddon.ability.ParasiticCombatTracker.isInCombat(target);
        float hpRatio = target.getHealth() / Math.max(1.0f, target.getMaxHealth());
        // buff 时长 = 叮声间隔 + 0.2s = 44t（默认），再乘腐殖之戒系数（装备时友军增益 ×0.7）
        int buffDur = Math.max(10, Math.round((BAL.i("fruit_interval_ticks", FRUIT_INTERVAL_TICKS) + 4) * currentHumusFactor));
        if (!inCombat) {
            if (hpRatio > 0.7f) {
                // 未交战 + 高血：急迫 I
                addEffect(target, StatusEffects.HASTE, buffDur, 0, caster);
            } else {
                // 未交战 + 低血：回血 1 心
                healWithFx(target, caster, 1);
            }
        } else {
            if (hpRatio > 0.8f) {
                // 交战 + 高血：迅捷 II
                addEffect(target, StatusEffects.SPEED, buffDur, 1, caster);
            } else if (hpRatio >= 0.5f) {
                // 交战 + 中血：伤害吸收 I（自定义累加黄心，持续受腐殖之戒系数）+ 生命恢复（1 心）
                net.jackcooper.shapeShifterCurseAddon.ability.ParasiticAbsorptionManager.addAbsorption(target, 0, currentHumusFactor);
                healWithFx(target, caster, 1);
            } else {
                // 交战 + 低血：伤害吸收 II（自定义累加黄心，持续受腐殖之戒系数）+ 生命恢复（2 心）
                net.jackcooper.shapeShifterCurseAddon.ability.ParasiticAbsorptionManager.addAbsorption(target, 1, currentHumusFactor);
                healWithFx(target, caster, 2);
            }
        }
    }

    /** 立即回血（触发血条回血动画）+ 生命恢复图标显示。 */
    private void healWithFx(LivingEntity target, ServerPlayerEntity caster, int hearts) {
        target.heal(hearts * 2.0f);
        // 专属「生命恢复」buff：立即回血由上面 heal 完成（血条动画），此 buff 仅显示图标、不周期回血（只回一次）
        addEffect(target, SscAddon.BAT_REGEN, 60, hearts - 1, caster);
    }

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit, int divisor, int stack, ServerPlayerEntity caster) {
        int amp = MathHelper.clamp(stack - 1, 0, 2);
        switch (fruit) {
            case VINE -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(60, divisor, 1.0f), amp, caster);
                if (!target.isOnGround()) {
                    Vec3d velocity = target.getVelocity();
                    target.setVelocity(velocity.x * 0.75, Math.min(velocity.y, -0.08), velocity.z * 0.75);
                    target.velocityModified = true;
                }
            }
            case BITTER -> {
                addEffect(target, StatusEffects.WEAKNESS, scaleDuration(80, divisor, 1.0f), amp, caster);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(80, divisor, 1.0f), 0, caster);
            }
            case ROTTEN -> {
                if (target.getGroup() == EntityGroup.UNDEAD) {
                    addEffect(target, StatusEffects.SLOWNESS, scaleDuration(70, divisor, 1.0f), amp, caster);
                    addEffect(target, StatusEffects.WEAKNESS, scaleDuration(70, divisor, 1.0f), amp, caster);
                } else {
                    addEffect(target, StatusEffects.POISON, scaleDuration(60, divisor, 1.0f), amp, caster);
                }
            }
            case SOUR -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(40, divisor, 1.0f), amp, caster);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(60, divisor, 1.0f), 0, caster);
            }
        }
    }

    private void addEffect(LivingEntity target, StatusEffect effect, int duration, int amplifier, ServerPlayerEntity caster) {
        if (duration <= 0) return;
        // 带 caster source：供食梦魔「入梦」debuff/高光拦截归因（友方目标 caster 也是合法 source，不影响判定）
        target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, false, true, true), caster);
    }

    private int scaleDuration(int duration, int divisor, float multiplier) {
        // 腐殖之戒：敌方果时长 +50% / 友方果时长 -30%（由 bearFruit 在调用前设置本系数）
        float total = multiplier * currentHumusFactor;
        // 每次触发的药水效果统一额外延长 0.3 秒（+6 tick）
        return Math.max(10, Math.round(duration * total / Math.max(1, divisor)) + 6);
    }

    public void drawAttached(ServerPlayerEntity caster, LivingEntity host, int total) {
        if (!(host.getWorld() instanceof ServerWorld world)) return;
        boolean friend = WhitelistUtils.isBuffTarget(caster, host);
        if (total >= 3 && !net.jackcooper.shapeShifterCurseAddon.ability.InfectionSporeManager.isInfected(host.getUuid())) {
            FruitStatusOverlay.pulse(host, "outline", 3, () -> {
                if (friend) net.jackcooper.shapeShifterCurseAddon.util.GlowMarker.markFriend(host, caster);
                else net.jackcooper.shapeShifterCurseAddon.util.GlowMarker.markEnemy(host, caster);
            });
        } else net.jackcooper.shapeShifterCurseAddon.util.GlowMarker.unmark(host);
        if (world.getTime() % 10 != 0) return;
        ParticleUtils.spawnParticles(world, friend ? FRIEND_DUST : ENEMY_DUST,
                host.getX(), host.getY() + host.getHeight() * 0.75, host.getZ(),
                2 * total, 0.18, 0.25, 0.18, 0.0);
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
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.0875f, 1.8f);
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

}
