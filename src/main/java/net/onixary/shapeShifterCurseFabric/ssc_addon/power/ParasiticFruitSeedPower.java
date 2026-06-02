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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄生果蝠主动技能：灵果寄生。
 * 命中生物后在目标身上种下灵果种子，种子根据宿主的友敌关系与状态周期性结出自适应果实。
 * 全部判定与状态效果均在服务端执行，确保多人环境主客机一致。
 */
public class ParasiticFruitSeedPower extends ActiveCooldownPower {
    private static final double RANGE = 20.0;
    private static final double SPREAD_RADIUS = 4.0;
    private static final double SPREAD_RADIUS_SQ = SPREAD_RADIUS * SPREAD_RADIUS;
    private static final int MAX_SEEDS = 3;
    private static final int ROOTING_TICKS = 20;
    private static final int FRUIT_INTERVAL_TICKS = 40;
    private static final int DEFAULT_LIFE_TICKS = 240;
    private static final int MIN_LIFE_TICKS = 80;
    private static final int FRIEND_SPREAD_DIVISOR = 2;
    private static final int ENEMY_SPREAD_DIVISOR = 2;
    private static final float SELF_DURATION_MULTIPLIER = 0.6f;

    private static final DustParticleEffect FRIEND_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 0.30f), 1.1f);
    private static final DustParticleEffect ENEMY_DUST = new DustParticleEffect(new Vector3f(0.55f, 0.10f, 0.75f), 1.1f);
    private static final DustParticleEffect SEED_DUST = new DustParticleEffect(new Vector3f(0.95f, 0.72f, 0.24f), 1.0f);

    private final int cooldownTicks;
    private final int lifeTicks;
    private long internalCooldownEndTime = 0L;
    private final LinkedHashMap<UUID, SeedData> seeds = new LinkedHashMap<>();

    public ParasiticFruitSeedPower(PowerType<?> type, LivingEntity entity, int cooldownTicks, int lifeTicks,
                                   HudRender hudRender, Active.Key key) {
        super(type, entity, cooldownTicks, hudRender, (e) -> {
        });
        this.cooldownTicks = cooldownTicks;
        this.lifeTicks = lifeTicks;
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

        LivingEntity target = raycastLivingTarget(caster);
        if (target == null || !target.isAlive()) {
            // 未命中生物时只进入半冷却，保留技能瞄准成本但不至于过度惩罚。
            applyCooldown(Math.max(20, cooldownTicks / 2));
            playFailFeedback(caster);
            return;
        }

        plantSeed(caster, target);
        applyCooldown(cooldownTicks);
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
            if (host == null || !host.isAlive() || now >= seed.endTick) {
                iterator.remove();
                continue;
            }
            spawnAttachedSeedParticles(host, seed, now);
            if (now >= seed.nextFruitTick) {
                seed.nextFruitTick = now + FRUIT_INTERVAL_TICKS;
                bearFruit(caster, host, seed);
            }
        }
    }

    @Override
    public void onLost() {
        super.onLost();
        seeds.clear();
    }

    private boolean isInternalCooldownReady() {
        return entity.getWorld().getTime() >= internalCooldownEndTime;
    }

    private void applyCooldown(int ticks) {
        internalCooldownEndTime = entity.getWorld().getTime() + ticks;
        if (entity instanceof ServerPlayerEntity caster) {
            PowerUtils.setResourceValueAndSync(caster, FormIdentifiers.SP_PRIMARY_CD, ticks);
        }
    }

    private LivingEntity raycastLivingTarget(ServerPlayerEntity caster) {
        Vec3d eye = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F).normalize();
        Vec3d end = eye.add(look.multiply(RANGE));
        Box box = caster.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.2D);
        EntityHitResult hit = ProjectileUtil.raycast(caster, eye, end, box,
                e -> e instanceof LivingEntity living && e != caster && living.isAlive(), RANGE * RANGE);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private void plantSeed(ServerPlayerEntity caster, LivingEntity host) {
        long now = caster.getWorld().getTime();
        int adjustedLife = getEnvironmentAdjustedLife(host, lifeTicks);
        SeedData seed = seeds.get(host.getUuid());
        if (seed != null) {
            seed.endTick = now + adjustedLife;
            seed.nextFruitTick = now + ROOTING_TICKS;
        } else {
            cleanupExpiredSeeds(caster, now);
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
            ParticleUtils.spawnParticles(world, SEED_DUST,
                    host.getX(), host.getY() + host.getHeight() * 0.65, host.getZ(),
                    18, 0.25, 0.35, 0.25, 0.02);
            world.playSound(null, host.getX(), host.getY(), host.getZ(),
                    SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.9f, 1.5f);
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

    private int getEnvironmentAdjustedLife(LivingEntity host, int baseLife) {
        if (!(host.getWorld() instanceof ServerWorld world)) return baseLife;
        boolean openSky = world.isSkyVisible(host.getBlockPos());
        boolean day = world.isDay();
        int blockLight = world.getLightLevel(host.getBlockPos());
        if (day && openSky) {
            return Math.max(MIN_LIFE_TICKS, Math.round(baseLife * 0.7f));
        }
        if (!day || blockLight <= 7) {
            return Math.round(baseLife * 1.2f);
        }
        return baseLife;
    }

    private void bearFruit(ServerPlayerEntity caster, LivingEntity host, SeedData seed) {
        boolean friend = WhitelistUtils.isBuffTarget(caster, host);
        if (friend) {
            FriendFruit fruit = selectFriendFruit(host);
            applyFriendFruit(host, fruit, host == caster);
            spreadFriendFruit(caster, host, fruit);
            seed.lastFruitName = fruit.name();
            spawnFruitParticles(host, FRIEND_DUST);
        } else if (!WhitelistUtils.isProtected(caster, host)) {
            EnemyFruit fruit = selectEnemyFruit(host);
            applyEnemyFruit(host, fruit);
            spreadEnemyFruit(caster, host, fruit);
            seed.lastFruitName = fruit.name();
            spawnFruitParticles(host, ENEMY_DUST);
        }
    }

    private FriendFruit selectFriendFruit(LivingEntity host) {
        if (host.getHealth() / host.getMaxHealth() < 0.4f) {
            return FriendFruit.HONEYDEW;
        }
        if (host.hurtTime > 0 || (host instanceof MobEntity mob && mob.getTarget() != null)) {
            return FriendFruit.PRICKLY_PEAR;
        }
        if (host.isSprinting() || !host.isOnGround() || host.getVelocity().horizontalLengthSquared() > 0.05) {
            return FriendFruit.WIND_BERRY;
        }
        return FriendFruit.FRAGRANT;
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

    private void spreadFriendFruit(ServerPlayerEntity caster, LivingEntity host, FriendFruit fruit) {
        ServerWorld world = (ServerWorld) host.getWorld();
        Box box = host.getBoundingBox().expand(SPREAD_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != host && e.isAlive() && host.squaredDistanceTo(e) <= SPREAD_RADIUS_SQ);
        for (LivingEntity target : targets) {
            if (WhitelistUtils.isBuffTarget(caster, target)) {
                applyFriendFruit(target, fruit, target == caster, FRIEND_SPREAD_DIVISOR);
            }
        }
    }

    private void spreadEnemyFruit(ServerPlayerEntity caster, LivingEntity host, EnemyFruit fruit) {
        ServerWorld world = (ServerWorld) host.getWorld();
        Box box = host.getBoundingBox().expand(SPREAD_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != host && e.isAlive() && host.squaredDistanceTo(e) <= SPREAD_RADIUS_SQ);
        for (LivingEntity target : targets) {
            if (!WhitelistUtils.isBuffTarget(caster, target) && !WhitelistUtils.isProtected(caster, target)) {
                applyEnemyFruit(target, fruit, ENEMY_SPREAD_DIVISOR);
            }
        }
    }

    private void applyFriendFruit(LivingEntity target, FriendFruit fruit, boolean self) {
        applyFriendFruit(target, fruit, self, 1);
    }

    private void applyFriendFruit(LivingEntity target, FriendFruit fruit, boolean self, int divisor) {
        float selfMultiplier = self ? SELF_DURATION_MULTIPLIER : 1.0f;
        switch (fruit) {
            case HONEYDEW -> {
                addEffect(target, StatusEffects.REGENERATION, scaleDuration(60, divisor, selfMultiplier), 0);
                addEffect(target, StatusEffects.ABSORPTION, scaleDuration(100, divisor, selfMultiplier), 0);
                removeOneNegativeEffect(target);
            }
            case PRICKLY_PEAR -> {
                addEffect(target, StatusEffects.RESISTANCE, scaleDuration(80, divisor, selfMultiplier), 0);
                addEffect(target, StatusEffects.STRENGTH, scaleDuration(80, divisor, selfMultiplier), 0);
            }
            case WIND_BERRY -> {
                addEffect(target, StatusEffects.SPEED, scaleDuration(100, divisor, selfMultiplier), 0);
                addEffect(target, StatusEffects.SLOW_FALLING, scaleDuration(80, divisor, selfMultiplier), 0);
            }
            case FRAGRANT -> {
                addEffect(target, StatusEffects.HASTE, scaleDuration(60, divisor, selfMultiplier), 0);
                target.heal(divisor == 1 ? 1.0f : 0.5f);
            }
        }
    }

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit) {
        applyEnemyFruit(target, fruit, 1);
    }

    private void applyEnemyFruit(LivingEntity target, EnemyFruit fruit, int divisor) {
        switch (fruit) {
            case VINE -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(60, divisor, 1.0f), 0);
                if (!target.isOnGround()) {
                    Vec3d velocity = target.getVelocity();
                    target.setVelocity(velocity.x * 0.75, Math.min(velocity.y, -0.08), velocity.z * 0.75);
                    target.velocityModified = true;
                }
            }
            case BITTER -> {
                addEffect(target, StatusEffects.WEAKNESS, scaleDuration(80, divisor, 1.0f), 0);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(80, divisor, 1.0f), 0);
            }
            case ROTTEN -> {
                if (target.getGroup() == EntityGroup.UNDEAD) {
                    addEffect(target, StatusEffects.SLOWNESS, scaleDuration(70, divisor, 1.0f), 0);
                    addEffect(target, StatusEffects.WEAKNESS, scaleDuration(70, divisor, 1.0f), 0);
                } else {
                    addEffect(target, StatusEffects.POISON, scaleDuration(60, divisor, 1.0f), 0);
                }
            }
            case SOUR -> {
                addEffect(target, StatusEffects.SLOWNESS, scaleDuration(40, divisor, 1.0f), 0);
                addEffect(target, StatusEffects.GLOWING, scaleDuration(60, divisor, 1.0f), 0);
            }
        }
    }

    private void addEffect(LivingEntity target, StatusEffect effect, int duration, int amplifier) {
        if (duration <= 0) return;
        target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, false, true, true));
    }

    private int scaleDuration(int duration, int divisor, float multiplier) {
        return Math.max(10, Math.round(duration * multiplier / Math.max(1, divisor)));
    }

    private void removeOneNegativeEffect(LivingEntity target) {
        List<StatusEffect> removable = new ArrayList<>();
        removable.add(StatusEffects.POISON);
        removable.add(StatusEffects.WITHER);
        removable.add(StatusEffects.SLOWNESS);
        removable.add(StatusEffects.WEAKNESS);
        removable.add(StatusEffects.HUNGER);
        removable.add(StatusEffects.NAUSEA);
        removable.add(StatusEffects.BLINDNESS);
        for (StatusEffect effect : removable) {
            if (target.hasStatusEffect(effect)) {
                target.removeStatusEffect(effect);
                return;
            }
        }
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

    private void spawnAttachedSeedParticles(LivingEntity host, SeedData seed, long now) {
        if (!(host.getWorld() instanceof ServerWorld world)) return;
        if (now % 10 != 0) return;
        ParticleEffect particle = WhitelistUtils.isBuffTarget((ServerPlayerEntity) entity, host) ? FRIEND_DUST : ENEMY_DUST;
        ParticleUtils.spawnParticles(world, particle,
                host.getX(), host.getY() + host.getHeight() * 0.75, host.getZ(),
                2, 0.18, 0.25, 0.18, 0.0);
    }

    private void spawnFruitParticles(LivingEntity host, ParticleEffect particle) {
        if (!(host.getWorld() instanceof ServerWorld world)) return;
        double y = host.getY() + host.getHeight() * 0.75;
        ParticleUtils.spawnParticles(world, particle, host.getX(), y, host.getZ(),
                16, 0.35, 0.45, 0.35, 0.02);
        ParticleUtils.spawnParticles(world, SEED_DUST, host.getX(), y, host.getZ(),
                8, 0.25, 0.35, 0.25, 0.01);
        world.playSound(null, host.getX(), host.getY(), host.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.35f, 1.8f);
    }

    private void playFailFeedback(ServerPlayerEntity caster) {
        ServerWorld world = caster.getServerWorld();
        world.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 1.7f);
    }

    private enum FriendFruit {
        HONEYDEW,
        PRICKLY_PEAR,
        WIND_BERRY,
        FRAGRANT
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
        private String lastFruitName = "ROOTING";

        private SeedData(long endTick, long nextFruitTick) {
            this.endTick = endTick;
            this.nextFruitTick = nextFruitTick;
        }
    }
}