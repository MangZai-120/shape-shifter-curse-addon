package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import mod.azure.azurelib.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.core.animation.Animation;
import mod.azure.azurelib.core.animation.AnimationController;
import mod.azure.azurelib.core.animation.RawAnimation;
import mod.azure.azurelib.core.object.PlayState;
import mod.azure.azurelib.util.AzureLibUtil;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.TntEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 女巫使魔 - 一种伴随女巫自然生成的敌对生物
 * 使用原版使魔外观，拥有火环技能，属于劫掠阵营
 */
public class WitchFamiliarEntity extends HostileEntity implements GeoEntity {

    private final AnimatableInstanceCache cache = AzureLibUtil.createInstanceCache(this);

    // 火环参数
    private static final int FIRE_RING_COOLDOWN_MAX = 240;  // 12秒冷却
    private static final int FIRE_RING_POWER = 3;           // 爆炸威力（原版explosion_damage_entity power=3）
    private static final float FIRE_RING_EFFECT_RADIUS = FIRE_RING_POWER * 2.0f; // 实际影响半径=6.0格
    private static final float FIRE_RING_DAMAGE = 6.0f;     // on_fire伤害（原版8降低2点）
    private static final int FIRE_RING_IGNITE_SECONDS = 10; // 着火10秒
    private static final float PARTICLE_OUTER_RADIUS = 4.0f; // 粒子外圈半径4格
    private static final float PARTICLE_INNER_RADIUS = 1.5f; // 粒子内圈半径1.5格
    private int fireRingCooldown = 0;

    // 原版使魔形态ID（用于友军判定）
    private static final Identifier VANILLA_FAMILIAR_FOX_3 = new Identifier("shape-shifter-curse", "form_familiar_fox_3");

    // 主人（女巫）UUID，用于跟随和攻击同步
    private UUID ownerUuid;

    public WitchFamiliarEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    /**
     * 从世界中获取主人女巫实体
     */
    public WitchEntity getOwnerWitch() {
        if (this.ownerUuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return null;
        var entity = serverWorld.getEntity(this.ownerUuid);
        if (entity instanceof WitchEntity witch && witch.isAlive()) return witch;
        return null;
    }

    public static DefaultAttributeContainer.Builder createWitchFamiliarAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0)       // 7颗心（与使魔形态一致）
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)     // 2颗心近战伤害
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)   // 与狼相近的移速
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);    // 追踪范围
    }

    @Override
    protected void initGoals() {
        // 移动/战斗目标
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(2, new FollowOwnerWitchGoal(this, 1.0, 10.0f, 3.0f, 16.0f));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));

        // 索敌目标（排除友军）
        this.targetSelector.add(1, new CopyOwnerTargetGoal(this));
        this.targetSelector.add(2, new RevengeGoal(this, RaiderEntity.class, WitchFamiliarEntity.class));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, true, this::shouldAttackPlayer));
    }

    /**
     * 判断是否应该攻击该玩家
     * 不攻击：原版使魔、堕落悦灵形态的玩家（劫掠阵营友军）
     */
    private boolean shouldAttackPlayer(LivingEntity target) {
        if (!(target instanceof PlayerEntity player)) return false;
        return !FormUtils.isAnyForm(player,
                FormIdentifiers.FALLEN_ALLAY_SP,
                VANILLA_FAMILIAR_FOX_3
        );
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        // 不攻击劫掠阵营、恼鬼、女巫、其他女巫使魔
        if (target instanceof RaiderEntity) return false;
        if (target instanceof VexEntity) return false;
        if (target instanceof WitchEntity) return false;
        if (target instanceof WitchFamiliarEntity) return false;
        return super.canTarget(target);
    }

    @Override
    public void mobTick() {
        super.mobTick();

        if (fireRingCooldown > 0) fireRingCooldown--;

        // 灵魂火焰粒子效果（与原版使魔形态一致，每10tick产生一次）
        if (!this.getWorld().isClient() && this.age % 10 == 0 && this.getWorld() instanceof ServerWorld sw) {
            ParticleUtils.spawnParticles(sw, ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
        }

        // 仅在有攻击目标且目标在火环范围内时释放火环
        if (!this.getWorld().isClient() && fireRingCooldown <= 0 && this.getTarget() != null
                && this.squaredDistanceTo(this.getTarget()) <= FIRE_RING_EFFECT_RADIUS * FIRE_RING_EFFECT_RADIUS) {
            useFireRing();
            fireRingCooldown = FIRE_RING_COOLDOWN_MAX;
        }
    }

    /**
     * 释放火环技能（完全复刻原版SSC ExplosionDamageEntityAction逻辑）
     * 原版参数：power=3, explosion_damage_entity=false, entity_action={damage 8 on_fire + set_on_fire 10}
     */
    private void useFireRing() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;

        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        // 音效
        serverWorld.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 0.5f, 1.0f);
        serverWorld.playSound(null, x, y, z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 0.5f, 1.0f);
        serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 0.5f, 1.0f);

        // 粒子效果 - 外圈火焰（半径4格，64个采样点）
        for (int i = 0; i < 64; i++) {
            double angle = 2 * Math.PI * i / 64;
            double px = x + Math.cos(angle) * PARTICLE_OUTER_RADIUS;
            double pz = z + Math.sin(angle) * PARTICLE_OUTER_RADIUS;
            ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME, px, y + 0.5, pz, 1, 0.4, 0.6, 0.4, 0.01);
        }
        // 内圈火焰粒子
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            double px = x + Math.cos(angle) * PARTICLE_INNER_RADIUS;
            double pz = z + Math.sin(angle) * PARTICLE_INNER_RADIUS;
            ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME, px, y + 0.5, pz, 1, 0.2, 0.3, 0.2, 0.04);
        }
        // 灵魂火焰粒子
        ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.5, z, 8, 2.0, 1.0, 2.0, 0.0);

        // 爆炸游戏事件（原版会触发）
        serverWorld.emitGameEvent(this, GameEvent.EXPLODE, this.getPos());

        // === 复刻原版 ExplosionDamageEntityAction 逻辑 ===
        Vec3d explosionPos = this.getPos();
        float q = FIRE_RING_EFFECT_RADIUS; // power * 2.0 = 6.0

        int k = MathHelper.floor(explosionPos.getX() - q - 1.0);
        int l = MathHelper.floor(explosionPos.getX() + q + 1.0);
        int r = MathHelper.floor(explosionPos.getY() - q - 1.0);
        int s = MathHelper.floor(explosionPos.getY() + q + 1.0);
        int t = MathHelper.floor(explosionPos.getZ() - q - 1.0);
        int u = MathHelper.floor(explosionPos.getZ() + q + 1.0);

        // 获取范围内所有实体（排除自身）
        List<net.minecraft.entity.Entity> entityList = serverWorld.getOtherEntities(
                this, new net.minecraft.util.math.Box(k, r, t, l, s, u));

        RegistryKey<DamageType> onFireKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "on_fire"));

        for (net.minecraft.entity.Entity targetEntity : entityList) {
            // 跳过爆炸免疫实体
            if (targetEntity.isImmuneToExplosion()) continue;
            // 跳过非生物实体或不应受火环影响的实体
            if (!(targetEntity instanceof LivingEntity living)) continue;
            if (!shouldFireRingAffect(living)) continue;

            // 计算归一化距离
            double w = Math.sqrt(targetEntity.squaredDistanceTo(explosionPos)) / (double) q;
            if (w > 1.0) continue;

            double dx = targetEntity.getX() - explosionPos.getX();
            double dy = (targetEntity instanceof TntEntity ? targetEntity.getY() : targetEntity.getEyeY()) - explosionPos.getY();
            double dz = targetEntity.getZ() - explosionPos.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist == 0.0) continue;

            dx /= dist;
            dy /= dist;
            dz /= dist;

            // 暴露度计算（视线检查）
            double exposure = Explosion.getExposure(explosionPos, targetEntity);
            double intensity = (1.0 - w) * exposure;

            // explosion_damage_entity = false，不造成爆炸伤害
            // 但应用爆炸击退
            double knockbackIntensity;
            if (targetEntity instanceof LivingEntity le) {
                knockbackIntensity = ProtectionEnchantment.transformExplosionKnockback(le, intensity);
            } else {
                knockbackIntensity = intensity;
            }
            targetEntity.setVelocity(targetEntity.getVelocity().add(
                    dx * knockbackIntensity, dy * knockbackIntensity, dz * knockbackIntensity));

            // entity_action: damage 8 on_fire + set_on_fire 10秒
            living.damage(living.getDamageSources().create(onFireKey, this), FIRE_RING_DAMAGE);
            living.setOnFireFor(FIRE_RING_IGNITE_SECONDS);
        }
    }

    /**
     * 火环伤害的目标筛选
     */
    private boolean shouldFireRingAffect(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        // 不影响劫掠阵营
        if (entity instanceof RaiderEntity) return false;
        if (entity instanceof VexEntity) return false;
        if (entity instanceof WitchEntity) return false;
        if (entity instanceof WitchFamiliarEntity) return false;
        // 不影响原版使魔/堕落悦灵形态的玩家（劫掠阵营友军）
        if (entity instanceof PlayerEntity player) {
            if (FormUtils.isAnyForm(player,
                    FormIdentifiers.FALLEN_ALLAY_SP,
                    VANILLA_FAMILIAR_FOX_3)) {
                return false;
            }
        }
        return true;
    }

    // ========== 持久化 ==========

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("FireRingCooldown", fireRingCooldown);
        if (this.ownerUuid != null) {
            nbt.put("OwnerUUID", NbtHelper.fromUuid(this.ownerUuid));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        fireRingCooldown = nbt.getInt("FireRingCooldown");
        if (nbt.containsUuid("OwnerUUID")) {
            this.ownerUuid = nbt.getUuid("OwnerUUID");
        }
    }

    // ========== 音效 ==========

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_FOX_DEATH;
    }

    // ========== GeoEntity 实现 ==========

    @Override
    public boolean isFireImmune() {
        return true;
    }

    /**
     * 归属劫掠阵营（ILLAGER组）
     * 使 SSC 的 MobEntityTeamMixin 能正确识别女巫使魔为劫掠阵营成员，
     * 从而让拥有 PillagerFriendlyPower 的使魔形态玩家被视为队友
     */
    @Override
    public EntityGroup getGroup() {
        return EntityGroup.ILLAGER;
    }

    // ========== 免疫系统（与SP使魔形态一致） ==========

    /**
     * 免疫与SSC原版使魔形态相同的14种药水效果
     * （对应 form_familiar_fox_3_no_buff_effect.json）
     */
    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        StatusEffect type = effect.getEffectType();
        if (type == StatusEffects.POISON
                || type == StatusEffects.HUNGER
                || type == StatusEffects.SPEED
                || type == StatusEffects.HASTE
                || type == StatusEffects.STRENGTH
                || type == StatusEffects.REGENERATION
                || type == StatusEffects.FIRE_RESISTANCE
                || type == StatusEffects.WATER_BREATHING
                || type == StatusEffects.NIGHT_VISION
                || type == StatusEffects.RESISTANCE
                || type == StatusEffects.INVISIBILITY
                || type == StatusEffects.HEALTH_BOOST
                || type == StatusEffects.WITHER
                || type == StatusEffects.ABSORPTION) {
            return false;
        }
        return super.canHaveStatusEffect(effect);
    }

    /**
     * 免疫浆果丛减速效果
     */
    @Override
    public void slowMovement(BlockState state, Vec3d multiplier) {
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return; // 浆果丛不对女巫使魔产生减速
        }
        super.slowMovement(state, multiplier);
    }

    /**
     * 免疫浆果丛伤害 + 使魔玩家攻击时复刻SSC原版劫掠阵营交互效果
     * （对应 form_familiar_fox_3_no_attack_witch.json + hurt_when_attack_witch.json）
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        // 浆果丛免疫
        if ("sweetBerryBush".equals(source.getName())) {
            return false;
        }

        // 原版使魔/堕落悦灵形态玩家攻击女巫使魔 → 和SSC原版攻击劫掠阵营效果一致
        if (source.getAttacker() instanceof PlayerEntity player) {
            if (FormUtils.isAnyForm(player,
                    FormIdentifiers.FALLEN_ALLAY_SP,
                    VANILLA_FAMILIAR_FOX_3)) {
                handleFamiliarPlayerAttack(player);
                return false; // 不受伤害
            }
        }

        return super.damage(source, amount);
    }

    /**
     * 处理使魔玩家攻击女巫使魔的效果（复刻SSC原版机制）
     * - 治疗女巫使魔20HP（no_attack_witch）
     * - 玩家自伤1HP + 击退（hurt_when_attack_witch）
     * - 播放末影之眼消散音效
     */
    private void handleFamiliarPlayerAttack(PlayerEntity player) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;

        // 治疗女巫使魔20HP
        this.heal(20.0f);

        // 播放音效
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.NEUTRAL, 1.0f, 1.0f);

        // 玩家自伤1HP
        player.damage(player.getDamageSources().generic(), 1.0f);

        // 玩家击退（向后 z:-0.5，向上 y:0.5，local_horizontal_normalized空间）
        Vec3d lookDir = player.getRotationVector();
        double horizontalLen = Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z);
        if (horizontalLen > 0.001) {
            // 归一化水平方向后施加击退
            player.addVelocity(
                    -lookDir.x / horizontalLen * 0.5,
                    0.5,
                    -lookDir.z / horizontalLen * 0.5
            );
        } else {
            player.addVelocity(0, 0.5, 0);
        }
        player.velocityModified = true;
    }

    /**
     * 水中移动不减速（消除水中阻力）
     */
    @Override
    public void travel(Vec3d movementInput) {
        if (this.isLogicalSideForUpdatingMovement() && this.isTouchingWater()) {
            // 水中使用更高的移动系数和更低的阻力，接近陆地速度
            this.updateVelocity(0.04f, movementInput); // 原版水中为0.02f
            this.move(MovementType.SELF, this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.9)); // 原版水中为0.8
            if (!this.hasNoGravity()) {
                this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0));
            }
            this.updateLimbs(false);
        } else {
            super.travel(movementInput);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 移动动画（走路/待机）
        controllers.add(new AnimationController<>(this, "movement", 3, state -> {
            if (state.isMoving()) {
                state.setAnimation(RawAnimation.begin().then("walk", Animation.LoopType.LOOP));
            } else {
                state.setAnimation(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
            }
            return PlayState.CONTINUE;
        }));
        // 攻击动画
        controllers.add(new AnimationController<>(this, "attack", 0, state -> {
            if (this.handSwinging) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
            }
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ========== 自定义AI目标 ==========

    /**
     * 跟随主人女巫目标（类似狼的跟随主人逻辑）
     * 当距离超过 maxDistance 时传送到主人身边
     * 在 minDistance 以内时停止移动
     */
    static class FollowOwnerWitchGoal extends Goal {
        private final WitchFamiliarEntity familiar;
        private final double speed;
        private final float maxDistance;      // 超过此距离传送
        private final float minDistance;      // 靠近到此距离停止
        private final float startDistance;    // 距离超过此值开始跟随
        private WitchEntity owner;
        private int updateCountdown;

        public FollowOwnerWitchGoal(WitchFamiliarEntity familiar, double speed, float maxDistance, float minDistance, float startDistance) {
            this.familiar = familiar;
            this.speed = speed;
            this.maxDistance = maxDistance;
            this.minDistance = minDistance;
            this.startDistance = startDistance;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            WitchEntity witch = this.familiar.getOwnerWitch();
            if (witch == null) return false;
            // 正在攻击时不跟随（让战斗目标优先）
            if (this.familiar.getTarget() != null) return false;
            if (this.familiar.squaredDistanceTo(witch) < (double) this.startDistance * this.startDistance) return false;
            this.owner = witch;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (this.familiar.getNavigation().isIdle()) return false;
            if (this.familiar.getTarget() != null) return false;
            return this.familiar.squaredDistanceTo(this.owner) > (double) this.minDistance * this.minDistance;
        }

        @Override
        public void start() {
            this.updateCountdown = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.familiar.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.familiar.getLookControl().lookAt(this.owner, 10.0f, (float) this.familiar.getMaxLookPitchChange());

            if (--this.updateCountdown <= 0) {
                this.updateCountdown = 10;  // 每10 tick更新一次路径

                double distSq = this.familiar.squaredDistanceTo(this.owner);

                // 距离过远：传送到主人身边
                if (distSq >= (double) this.maxDistance * this.maxDistance) {
                    tryTeleportToOwner();
                } else {
                    this.familiar.getNavigation().startMovingTo(this.owner, this.speed);
                }
            }
        }

        /**
         * 传送到主人身边（类似狼传送逻辑）
         */
        private void tryTeleportToOwner() {
            BlockPos ownerPos = this.owner.getBlockPos();
            for (int i = 0; i < 10; i++) {
                int dx = this.familiar.getRandom().nextBetween(-3, 3);
                int dz = this.familiar.getRandom().nextBetween(-3, 3);
                BlockPos target = ownerPos.add(dx, 0, dz);
                // 简单检查：非固体方块（脚下）且站立位也非固体
                if (this.familiar.getWorld().getBlockState(target).isAir()
                        || !this.familiar.getWorld().getBlockState(target).isSolidBlock(this.familiar.getWorld(), target)) {
                    this.familiar.refreshPositionAndAngles(
                            target.getX() + 0.5, ownerPos.getY(), target.getZ() + 0.5,
                            this.familiar.getYaw(), this.familiar.getPitch());
                    this.familiar.getNavigation().stop();
                    return;
                }
            }
        }
    }

    /**
     * 复制主人女巫的攻击目标（类似狼跟随主人攻击逻辑）
     */
    static class CopyOwnerTargetGoal extends TrackTargetGoal {
        private final WitchFamiliarEntity familiar;
        private LivingEntity ownerTarget;
        private int lastCheckTime;

        public CopyOwnerTargetGoal(WitchFamiliarEntity familiar) {
            super(familiar, false);
            this.familiar = familiar;
        }

        @Override
        public boolean canStart() {
            WitchEntity owner = this.familiar.getOwnerWitch();
            if (owner == null) return false;
            this.ownerTarget = owner.getTarget();
            if (this.ownerTarget == null) return false;
            // 不能攻击友军
            return this.familiar.canTarget(this.ownerTarget);
        }

        @Override
        public void start() {
            this.familiar.setTarget(this.ownerTarget);
            super.start();
            this.lastCheckTime = this.familiar.age;
        }

        @Override
        public boolean shouldContinue() {
            // 每40 tick检查一次主人的目标是否变化
            if (this.familiar.age - this.lastCheckTime > 40) {
                WitchEntity owner = this.familiar.getOwnerWitch();
                if (owner != null) {
                    LivingEntity newTarget = owner.getTarget();
                    if (newTarget != null && newTarget != this.familiar.getTarget() && this.familiar.canTarget(newTarget)) {
                        this.familiar.setTarget(newTarget);
                    }
                }
                this.lastCheckTime = this.familiar.age;
            }
            return super.shouldContinue();
        }
    }
}
