package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.Locale;

import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A coastal predator with authored locomotion and committed hunting attacks. */
public final class OrcafisEntity extends PathAwareEntity implements GeoEntity {
    private static final float MAX_HEALTH = 300;
    private static final float PREVIOUS_MAX_HEALTH = 500;
    private static final double ARMOR = 12, ARMOR_TOUGHNESS = 6;
    public enum Motion {
        WALK("walk", 60), BURROW("burrow_swim", 80), BREACH("dolphin_breach", 166),
        SWEEP("tail_sweep", 51), DASH("water_dash", 83), WAVE("tidal_wall", 100),
        SWIM("swim", 100), REST("idle", 25), DIVE("dive", 32),
        RETURN("swim_return", 50), SURFACE("surface", 32),
        POUNCE("water_pounce", 166), PLOW("shore_plow", 74), TIDE("tidal_pulse", 146), RISE("breach_rise", 14),
        WATER_LUNGE("water_lunge", 26), LAND_BITE("land_bite", 20), WATER_BITE("water_bite", 20),
        BURROW_ENTER("burrow_enter", 24);

        public final String clip;
        public final int ticks;
        final RawAnimation animation;

        Motion(String clip, int ticks) {
            this.clip = clip;
            this.ticks = ticks;
            this.animation = RawAnimation.begin().thenPlay("animation.orcafis." + clip);
        }

        boolean huntingAttack() {
            return this == POUNCE || this == PLOW || this == TIDE || this == WATER_LUNGE
                    || this == LAND_BITE || this == WATER_BITE;
        }

        static Motion fromName(String value) {
            if ("cruise".equalsIgnoreCase(value)) return SWIM;
            if ("bubble_hunt".equalsIgnoreCase(value) || "water_vortex".equalsIgnoreCase(value)) return TIDE;
            for (Motion motion : values()) {
                if (motion.clip.equals(value.toLowerCase(Locale.ROOT))) return motion;
            }
            return null;
        }
    }

    private static final TrackedData<Integer> ACTION = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> START = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> DURATION = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> PAUSED_TICK = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> BURROW_STATE = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Float> BURROW_YAW = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final BlockStateParticleEffect BLACK_SOIL = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE_POWDER.getDefaultState());
    private static final BlockStateParticleEffect MUD = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MUD.getDefaultState());
    private static final TrackedData<Float> WATERLINE = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public float tideWaterline() { return dataTracker.get(WATERLINE); }
    private static final TrackedData<NbtCompound> SPLASH_POINTS = DataTracker.registerData(OrcafisEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private final Vec3d[] wakePositions = new Vec3d[17];
    private int wakeHead, wakeCount;
    private float previousBurrowYaw, lastBurrowYaw;

    public Vec3d splashPoint(boolean landing) {
        NbtCompound points = dataTracker.get(SPLASH_POINTS);
        if (!points.contains("Y")) return null;
        return new Vec3d(points.getDouble(landing ? "EndX" : "StartX"), points.getDouble("Y"),
                points.getDouble(landing ? "EndZ" : "StartZ"));
    }

    /** Interpolated world-space points along the trail drawn by this particular animal. */
    public Vec3d wakePoint(int ticksAgo, float partial) {
        if (ticksAgo + 1 >= wakeCount) return null;
        int index = Math.floorMod(wakeHead - ticksAgo, wakePositions.length);
        return wakePositions[Math.floorMod(index - 1, wakePositions.length)].lerp(wakePositions[index], partial);
    }

    private void recordWake() {
        if (motion() != Motion.DASH && motion() != Motion.RETURN && motion() != Motion.WATER_LUNGE) { wakeCount = 0; return; }
        Vec3d pos = getPos();
        if (wakeCount > 0 && pos.squaredDistanceTo(wakePositions[wakeHead]) > 36) wakeCount = 0;
        wakeHead = (wakeHead + 1) % wakePositions.length;
        wakePositions[wakeHead] = pos;
        wakeCount = Math.min(wakeCount + 1, wakePositions.length);
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossBar bossBar = new ServerBossBar(getDisplayName(), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
    private Motion solo;
    private final OrcafisEncounter encounter = new OrcafisEncounter(this);
    private final OrcafisParts parts = new OrcafisParts();
    private double startY, targetY;
    private OrcafisHuntingAttack hunt;
    private long nextStrikeTick;

    static boolean isHuntTarget(PlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    PlayerEntity nearbyTarget() {
        PlayerEntity closest = null;
        double range = 24 * 24;
        for (PlayerEntity p : getWorld().getPlayers()) {
            double d = squaredDistanceTo(p);
            if (d <= range && isHuntTarget(p)) {
                range = d;
                closest = p;
            }
        }
        return closest;
    }

    boolean mayStartStrike() { return getWorld().getTime() >= nextStrikeTick; }
    void markStrike() { nextStrikeTick = getWorld().getTime() + 40; }

    public OrcafisEntity(EntityType<? extends OrcafisEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96);
    }

    @Override protected EntityNavigation createNavigation(World world) {
        return new AmphibiousSwimNavigation(this, world);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.setPercent(getHealth() / getMaxHealth());
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!getWorld().isClient) bossBar.setVisible(false);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ACTION, Motion.WALK.ordinal());
        dataTracker.startTracking(START, -1L);
        dataTracker.startTracking(WATERLINE, 0f);
        dataTracker.startTracking(SPLASH_POINTS, new NbtCompound());
        dataTracker.startTracking(DURATION, Motion.WALK.ticks);
        dataTracker.startTracking(PAUSED_TICK, -1);
        dataTracker.startTracking(BURROW_STATE, (byte)0);
        dataTracker.startTracking(BURROW_YAW, 0f);
    }

    public Motion motion() {
        return Motion.values()[dataTracker.get(ACTION)];
    }

    public float motionTick(float partialTick) {
        int paused = dataTracker.get(PAUSED_TICK);
        if (paused >= 0) return paused;
        long start = dataTracker.get(START);
        return start < 0 ? 0 : Math.max(0, getWorld().getTime() - start + partialTick);
    }

    public boolean isMotionPaused() {
        return dataTracker.get(PAUSED_TICK) >= 0;
    }

    private boolean immobilizedByIce() {
        return isAlive() && (FrozenControl.blocked(this) || isFrozen());
    }

    private void updateMotionClock(boolean frozen) {
        long now = getWorld().getTime();
        long start = dataTracker.get(START);
        int paused = dataTracker.get(PAUSED_TICK);
        if (frozen && paused < 0) {
            // The current frame has not played yet. Remember the previous action frame.
            dataTracker.set(PAUSED_TICK, start < 0 ? 0 : (int)Math.max(0, now - start - 1));
        } else if (!frozen && paused >= 0) {
            if (start >= 0) dataTracker.set(START, now - paused - 1);
            dataTracker.set(PAUSED_TICK, -1);
        }
    }

    public void playStudy(Motion motion) {
        solo = motion;
        if (motion == null) begin(Motion.REST);
        else startSequence();
    }

    boolean begin(Motion motion) {
        if (motion == Motion.BURROW) {
            if (motion() == Motion.BURROW_ENTER) {
                if (motionTick(0) < Motion.BURROW_ENTER.ticks) return true;
            } else if (!belowGround() && !waterAtFeet()) {
                motion = Motion.BURROW_ENTER;
            }
        }
        PlayerEntity target = getTarget() instanceof PlayerEntity p ? p : solo == null ? null : nearbyTarget();
        if (motion == Motion.WAVE && target != null) face(target.getPos().subtract(getPos()), 180);
        OrcafisHuntingAttack next = motion == Motion.RETURN && hunt != null ? hunt
                : motion.huntingAttack() || motion == Motion.DASH || motion == Motion.RETURN
                    || motion == Motion.BREACH || motion == Motion.SWEEP || motion == Motion.WAVE
                ? OrcafisHuntingAttack.lock(this, target, motion) : null;
        if (solo == null && motion == Motion.DASH && !next.hasDashRoute()) {
            if (target == null || squaredDistanceTo(target) > 64) return false;
            motion = Motion.WATER_LUNGE;
            next = OrcafisHuntingAttack.lock(this, target, motion);
        }
        hunt = next;
        if (motion == Motion.TIDE) dataTracker.set(WATERLINE, hunt.waterline());
        if (motion == Motion.POUNCE) dataTracker.set(SPLASH_POINTS, hunt.splashPoints());
        startY = targetY = getY();
        int duration = solo == null && motion == Motion.BURROW ? 120 : motion.ticks;
        if (motion == Motion.DIVE || motion == Motion.SURFACE) {
            targetY = waterDestination(motion);
            duration = Math.max(duration, MathHelper.ceil(Math.abs(targetY - startY) * 4));
        }
        dataTracker.set(DURATION, duration);
        dataTracker.set(ACTION, motion.ordinal());
        dataTracker.set(START, getWorld().getTime());
        return true;
    }

    boolean waterAtFeet() {
        BlockPos feet = getBlockPos();
        return waterAt(feet) || waterAt(BlockPos.ofFloored(getX(), getY() - .1, getZ()));
    }

    double swimmingSurface() {
        return waterDestination(Motion.SURFACE);
    }

    private boolean waterAt(BlockPos pos) {
        return getWorld().getFluidState(pos).isIn(FluidTags.WATER);
    }

    boolean approachingBank(Vec3d goal) {
        Vec3d toward = goal.subtract(getPos()).multiply(1,0,1).normalize();
        BlockPos ahead = BlockPos.ofFloored(getPos().add(toward.multiply(getWidth()*.5 + .7)));
        int top = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,ahead.getX(),ahead.getZ());
        return !waterAt(ahead.withY(top-1));
    }

    private double waterDestination(Motion motion) {
        BlockPos pos = getBlockPos();
        if (!waterAt(pos)) pos = BlockPos.ofFloored(getX(), getY() - .1, getZ());
        if (!waterAt(pos)) return getY();
        if (motion == Motion.SURFACE) {
            while (pos.getY() + 1 < getWorld().getTopY() && waterAt(pos.up())) pos = pos.up();
            return pos.getY() + 1;
        }
        while (pos.getY() > getWorld().getBottomY() && waterAt(pos.down())
                && getWorld().getBlockState(pos.down()).getCollisionShape(getWorld(), pos.down()).isEmpty()) {
            pos = pos.down();
        }
        double available = Math.max(0, getY() - pos.getY());
        double nearFloor = pos.getY() + Math.min(2.0, available * .35);
        // The dash sweeps twenty-four blocks forward and ten to the side.
        // Leave room for the body when the seabed rises inside the turn.
        Vec3d forward = Vec3d.fromPolar(0, getYaw());
        double cruiseY = nearFloor;
        for (int along = -6; along <= 24; along += 3) {
            for (int side = 0; side <= 10; side += 2) {
                int x = MathHelper.floor(getX() + along * forward.x + side * forward.z);
                int z = MathHelper.floor(getZ() + along * forward.z - side * forward.x);
                int floor = getWorld().getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
                if (floor + 2 > cruiseY && floor < getY() && waterAt(new BlockPos(x, floor, z))) {
                    cruiseY = floor + 2;
                }
            }
        }
        return Math.max(nearFloor, Math.min(getY() - 6, cruiseY));
    }

    private void startSequence() {
        if (solo == null) {
            begin(waterAtFeet() ? Motion.SWIM : Motion.WALK);
        } else if (solo == Motion.WAVE && waterAtFeet()) {
            begin(waterDestination(Motion.SURFACE) - getY() > .15 ? Motion.SURFACE : Motion.WAVE);
        } else {
            begin(solo);
        }
    }

    private Motion after(Motion motion) {
        if (motion == Motion.BURROW_ENTER) return Motion.BURROW;
        if (solo == null) return encounter.after(motion);
        return switch (motion) {
            case BREACH -> Motion.RISE;
            case RISE -> Motion.REST;
            case POUNCE -> solo == Motion.POUNCE ? Motion.POUNCE : Motion.DASH;
            case WAVE -> Motion.DIVE;
            case DIVE -> Motion.SWIM;
            case SWIM -> solo == Motion.SWIM ? Motion.SWIM : Motion.DASH;
            case DASH -> Motion.RETURN;
            case RETURN -> solo == Motion.DASH ? Motion.SWIM : Motion.SURFACE;
            case SURFACE -> Motion.WAVE;
            case REST -> solo;
            default -> Motion.REST;
        };
    }

    @Override
    public void tick() {
        boolean frozen = immobilizedByIce();
        if (frozen) setVelocity(Vec3d.ZERO);
        super.tick();
        if (getWorld().isClient) {
            previousBurrowYaw = lastBurrowYaw;
            lastBurrowYaw = dataTracker.get(BURROW_YAW);
            if (!isMotionPaused()) {
                recordWake();
                if (dataTracker.get(BURROW_STATE) == 2 && age % 2 == 0) scatterSoil();
            }
            if (hasBurrowMound()) setBoundingBox(calculateBoundingBox());
            return;
        }
        bossBar.setPercent(getHealth() / getMaxHealth());
        setVelocity(0, 0, 0);
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
        if (!isAlive()) { parts.remove(); return; }
        frozen |= immobilizedByIce();
        updateMotionClock(frozen);
        Vec3d beforeStep = getPos();
        if (!isAiDisabled() || solo != null) tickBehavior(frozen, beforeStep);
        updateBurrow(beforeStep);
        parts.tick(this);
    }

    private void tickBehavior(boolean frozen, Vec3d beforeStep) {
        if (solo == null && encounter.tick(frozen)) return;
        if (frozen) return;
        if (dataTracker.get(START) < 0) startSequence();
        Motion motion = motion();
        int elapsed = (int)motionTick(0);
        int duration = dataTracker.get(DURATION);
        if (hunt != null && (elapsed < duration || motion == Motion.RETURN)) {
            hunt.tick((ServerWorld)getWorld(), this, motion, Math.min(elapsed, duration));
        }
        if (motion == Motion.DIVE || motion == Motion.SURFACE) {
            double t = MathHelper.clamp((double)elapsed / duration, 0, 1);
            setPosition(getX(), MathHelper.lerp(t*t*(3-2*t), startY, targetY), getZ());
        }
        if (elapsed >= duration) {
            begin(after(motion));
            return;
        }
        OrcafisSounds.tick(this, motion, elapsed, solo == Motion.WALK || beforeStep.squaredDistanceTo(getPos()) > .0001);
    }

    boolean belowGround() {
        float elapsed = motionTick(0);
        return motion() == Motion.BURROW || (motion() == Motion.PLOW && elapsed < 62)
                || (motion() == Motion.BREACH && elapsed < OrcafisLandAttacks.LIFT);
    }

    public boolean hasBurrowMound() {
        return dataTracker.get(BURROW_STATE) != 0 && moundStrength(motionTick(0)) > 0 && isAlive();
    }

    public float moundStrength(float elapsed) {
        if (motion() == Motion.BURROW) return 1;
        if (motion() == Motion.BURROW_ENTER) return MathHelper.clamp((elapsed - 16) / 8, 0, 1);
        if (motion() != Motion.PLOW || elapsed < 34 || elapsed >= 58) return 0;
        return Math.min(1, Math.min((elapsed-33)/4, (58-elapsed)/6));
    }

    public float burrowYaw(float partial) {
        float current = dataTracker.get(BURROW_YAW);
        return getWorld().isClient ? MathHelper.lerpAngleDegrees(partial, previousBurrowYaw, current) : current;
    }

    private void updateBurrow(Vec3d beforeStep) {
        byte previous = dataTracker.get(BURROW_STATE);
        byte state = 0;
        if (moundStrength(motionTick(0)) > 0 && !waterAtFeet()
                && OrcafisBurrowProfile.surface(getWorld(), getX(), getZ(), getY()) != null) {
            Vec3d delta = getPos().subtract(beforeStep);
            boolean moving = delta.horizontalLengthSquared() > .0004;
            state = (byte)(moving ? 2 : 1);
            if (moving) dataTracker.set(BURROW_YAW,
                    (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            else if (previous == 0) dataTracker.set(BURROW_YAW, getYaw());
        }
        dataTracker.set(BURROW_STATE, state);
        if (state != 0 || previous != 0) setBoundingBox(calculateBoundingBox());
    }

    private void scatterSoil() {
        float yaw = dataTracker.get(BURROW_YAW);
        Vec3d forward = Vec3d.fromPolar(0,yaw);
        Vec3d right = new Vec3d(forward.z,0,-forward.x);
        float elapsed = motionTick(0);
        float strength = moundStrength(elapsed);
        for (int i = 0; i < 8; i++) {
            int side = i % 2 == 0 ? 1 : -1;
            float sideways = side*(.35f + random.nextFloat()*1.13f);
            float along = .35f + random.nextFloat()*2.25f;
            float lift = OrcafisBurrowProfile.lift(sideways,along,elapsed,strength);
            if (lift < .06f) continue;
            Vec3d sample = getPos().add(right.multiply(sideways)).add(forward.multiply(along));
            var surface = OrcafisBurrowProfile.surface(getWorld(),sample.x,sample.z,sample.y);
            if (surface == null) continue;
            Vec3d velocity = forward.multiply(.09 + random.nextDouble()*.17)
                    .add(right.multiply(side*(.035 + random.nextDouble()*.09)))
                    .add(0,.09 + random.nextDouble()*.24,0);
            getWorld().addParticle(i % 4 == 0 ? MUD : BLACK_SOIL,
                    sample.x,surface.y()+lift+.04,sample.z,velocity.x,velocity.y,velocity.z);
        }
    }

    @Override protected Box calculateBoundingBox() {
        if (dataTracker != null && hasBurrowMound())
            return OrcafisBurrowProfile.collision(getPos(),dataTracker.get(BURROW_YAW),motionTick(0),moundStrength(motionTick(0)));
        return super.calculateBoundingBox();
    }

    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data.equals(BURROW_STATE) || data.equals(BURROW_YAW)) setBoundingBox(calculateBoundingBox());
    }

    @Override public boolean isCollidable() { return hasBurrowMound(); }

    @Override public boolean collidesWith(Entity other) {
        return !(other instanceof OrcafisPartEntity part && part.belongsTo(this)) && super.collidesWith(other);
    }

    @Override public void remove(RemovalReason reason) {
        if (parts != null && !getWorld().isClient) parts.remove();
        super.remove(reason);
    }

    @Override public boolean isPushable() { return false; }
    void face(Vec3d direction, float limit) {
        if (direction.horizontalLengthSquared() < .0001) return;
        float yaw = (float)Math.toDegrees(Math.atan2(-direction.x, direction.z));
        setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, limit));
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
    }

    void burrowTowards(Vec3d goal, double speed) {
        Vec3d delta = goal.subtract(getPos()).multiply(1, 0, 1);
        face(delta, 14);
        Vec3d step = getPos().add(delta.normalize().multiply(Math.min(delta.length(), speed)));
        BlockPos column = BlockPos.ofFloored(step);
        if (!getWorld().isChunkLoaded(column.getX() >> 4, column.getZ() >> 4)) return;
        int surface = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        double y = OrcafisLandAttacks.groundY(getWorld(), step.x, step.z, getY());
        if (waterAt(new BlockPos(column.getX(),surface-1,column.getZ()))) {
            y = MathHelper.clamp(goal.y,y,Math.max(y,surface-.25));
        }
        setPosition(step.x,getY()+MathHelper.clamp(y-getY(),-1,1),step.z);
    }

    void swimTowards(Vec3d goal, double speed) {
        Vec3d delta = goal.subtract(getPos());
        face(delta, 10);
        if (delta.lengthSquared() < .001) return;
        Vec3d step = delta.normalize().multiply(Math.min(speed, delta.length()));
        if (waterAt(BlockPos.ofFloored(getPos().add(step)))) move(MovementType.SELF,step);
        else move(MovementType.SELF, new Vec3d(step.x,Math.min(0,step.y),step.z));
    }

    void idleMotion(Motion desired) {
        if (dataTracker.get(START) < 0 || motion() != desired || motionTick(0) >= dataTracker.get(DURATION)) begin(desired);
    }

    @Override public void takeKnockback(double strength, double x, double z) {
        if (immobilizedByIce() || motion() == Motion.WAVE || motion() == Motion.TIDE ||
                (motion() == Motion.BREACH && motionTick(0) >= 66 && motionTick(0) < 166)) return;
        super.takeKnockback(strength,x,z);
    }
    @Override public boolean damage(DamageSource source, float amount) {
        if ((encounter.returning() || motion() == Motion.BURROW_ENTER || motion() == Motion.BURROW || (motion() == Motion.PLOW && motionTick(0) < 62) || (motion() == Motion.BREACH && motionTick(0)<19))
                && !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)) return false;
        boolean hit = super.damage(source, amount);
        if (hit && isAlive() && !getWorld().isClient && solo == null && source.getAttacker() instanceof PlayerEntity p) encounter.retaliate(p);
        return hit;
    }
    @Override public boolean isPushedByFluids() { return false; }
    @Override public boolean canBreatheInWater() { return true; }
    @Override public net.minecraft.util.math.Box getVisibilityBoundingBox() {
        return super.getVisibilityBoundingBox().expand(26, 11, 26);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("OrcafisBossAttributes", true);
        nbt.putBoolean("OrcafisHealth300", true);
        if (solo != null) nbt.putString("OrcafisAction", solo.clip);
        else nbt.put("OrcafisEncounter", encounter.save());
        if (dataTracker.get(START) >= 0) {
            NbtCompound playback = new NbtCompound();
            playback.putString("Motion", motion().clip);
            playback.putInt("Elapsed", (int) motionTick(0));
            playback.putInt("Duration", dataTracker.get(DURATION));
            playback.putDouble("StartY", startY);
            playback.putDouble("TargetY", targetY);
            playback.putInt("StrikeCooldown", (int)MathHelper.clamp(nextStrikeTick - getWorld().getTime(), 0L, 40L));
            if (hunt != null) playback.put("Hunt", hunt.save());
            nbt.put("OrcafisPlayback", playback);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (!nbt.getBoolean("OrcafisBossAttributes") && (nbt.contains("OrcafisEncounter") || nbt.contains("OrcafisPlayback"))) {
            migrateBossAttributes(nbt);
        } else if (nbt.getBoolean("OrcafisBossAttributes") && !nbt.getBoolean("OrcafisHealth300")) {
            restore300Health(nbt);
        }
        solo = Motion.fromName(nbt.getString("OrcafisAction"));
        encounter.load(nbt.getCompound("OrcafisEncounter"));
        dataTracker.set(START, -1L);
        NbtCompound playback = nbt.getCompound("OrcafisPlayback");
        Motion saved = Motion.fromName(playback.getString("Motion"));
        if (saved != null && playback.getInt("Duration") > 0) {
            nextStrikeTick = getWorld().getTime() + MathHelper.clamp(playback.getInt("StrikeCooldown"), 0, 40);
            hunt = playback.contains("Hunt") ? OrcafisHuntingAttack.load(playback.getCompound("Hunt")) : null;
            int duration = playback.getInt("Duration");
            int elapsed = playback.getInt("Elapsed");
            if (saved == Motion.POUNCE && duration == 134) {
                elapsed = elapsed < 34 ? Math.round(elapsed * 66f / 34) : elapsed + 32;
                duration = Motion.POUNCE.ticks;
            }
            if (!getWorld().isClient && ((saved == Motion.TIDE && !saved.clip.equals(playback.getString("Motion")))
                    || (hunt == null && (saved == Motion.DASH || saved == Motion.RETURN || saved == Motion.BREACH || saved == Motion.SWEEP || saved == Motion.WAVE)))) {
                hunt = OrcafisHuntingAttack.lock(this, solo == null ? null : nearbyTarget(), saved);
                if (saved == Motion.TIDE) { elapsed = 0; duration = saved.ticks; }
                if (saved == Motion.BREACH) { elapsed = 0; duration = saved.ticks; }
            }
            if (saved == Motion.TIDE && hunt != null) dataTracker.set(WATERLINE, hunt.waterline());
            if (saved == Motion.POUNCE && hunt != null) dataTracker.set(SPLASH_POINTS, hunt.splashPoints());
            startY = playback.getDouble("StartY");
            targetY = playback.getDouble("TargetY");
            dataTracker.set(ACTION, saved.ordinal());
            dataTracker.set(DURATION, duration);
            dataTracker.set(START, getWorld().getTime() - MathHelper.clamp(elapsed, 0, duration));
        }
    }

    private void migrateBossAttributes(NbtCompound nbt) {
        var armor = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor.getBaseValue() == 0) armor.setBaseValue(ARMOR);
        var toughness = getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        if (toughness.getBaseValue() == 0) toughness.setBaseValue(ARMOR_TOUGHNESS);
    }

    private void restore300Health(NbtCompound nbt) {
        var health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health.getBaseValue() != PREVIOUS_MAX_HEALTH && health.getBaseValue() != MAX_HEALTH) return;
        health.setBaseValue(PREVIOUS_MAX_HEALTH);
        float oldMax = getMaxHealth();
        health.setBaseValue(MAX_HEALTH);
        if (nbt.contains("Health")) setHealth(nbt.getFloat("Health") * getMaxHealth() / oldMax);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(motionController(false), motionController(true));
    }

    private AnimationController<OrcafisEntity> motionController(boolean turnLayer) {
        return new AnimationController<OrcafisEntity>(this, turnLayer ? "water_turn" : "body", 0, state -> {
            Motion motion = motion();
            if (motion == Motion.DASH || motion == Motion.RETURN || motion == Motion.WATER_LUNGE) {
                RawAnimation turn = motion == Motion.WATER_LUNGE ? Motion.DASH.animation : motion.animation;
                return state.setAndContinue(turnLayer ? turn : Motion.SWIM.animation);
            }
            return turnLayer ? PlayState.STOP : state.setAndContinue(motion.animation);
        }) {
            @Override protected double adjustTick(double tick) {
                double local = super.adjustTick(tick);
                if (getAnimationState() != State.RUNNING) return local;
                double elapsed = motionTick((float)(tick - Math.floor(tick)));
                return animationTick(elapsed, turnLayer);
            }
        };
    }

    double animationTick(double elapsed, boolean turnLayer) {
        Motion motion = motion();
        if (turnLayer) return motion == Motion.WATER_LUNGE ? elapsed * 1.5 : elapsed;
        return switch (motion) {
            case BURROW -> elapsed % 40;
            case WALK -> elapsed % 60;
            case DIVE, SURFACE -> Math.min(motion.ticks, elapsed * motion.ticks / dataTracker.get(DURATION));
            case DASH, RETURN -> (elapsed + (motion == Motion.RETURN ? Motion.DASH.ticks : 0))
                    * Motion.SWIM.ticks / (Motion.DASH.ticks + Motion.RETURN.ticks);
            case WATER_LUNGE -> elapsed * 1.8;
            default -> elapsed;
        };
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
