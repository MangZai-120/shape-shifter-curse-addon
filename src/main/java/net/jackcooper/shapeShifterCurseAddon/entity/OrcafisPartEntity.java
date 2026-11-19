package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Independently tracked collision and projectile target, with health and damage handled by its owner. */
public final class OrcafisPartEntity extends Entity {
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(OrcafisPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> WIDTH = DataTracker.registerData(OrcafisPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEIGHT = DataTracker.registerData(OrcafisPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DEPTH = DataTracker.registerData(OrcafisPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private OrcafisEntity owner;

    public OrcafisPartEntity(EntityType<? extends OrcafisPartEntity> type, World world) {
        super(type,world);
        setNoGravity(true);
    }

    @Override protected void initDataTracker() {
        dataTracker.startTracking(OWNER,-1);
        dataTracker.startTracking(WIDTH,.1f);
        dataTracker.startTracking(HEIGHT,.1f);
        dataTracker.startTracking(DEPTH,.1f);
    }

    void attach(OrcafisEntity owner) {
        this.owner = owner;
        dataTracker.set(OWNER,owner.getId());
    }

    void align(Box bounds) {
        dataTracker.set(WIDTH,quantize(bounds.maxX-bounds.minX));
        dataTracker.set(HEIGHT,quantize(bounds.maxY-bounds.minY));
        dataTracker.set(DEPTH,quantize(bounds.maxZ-bounds.minZ));
        setPosition(bounds.getCenter().x,bounds.minY,bounds.getCenter().z);
    }

    private static float quantize(double size) {
        return Math.max(.25f, Math.round(size*16)/16f);
    }

    @Override protected Box calculateBoundingBox() {
        if (dataTracker == null) return super.calculateBoundingBox();
        double x = dataTracker.get(WIDTH)*.5, z = dataTracker.get(DEPTH)*.5;
        return new Box(getX()-x,getY(),getZ()-z,getX()+x,getY()+dataTracker.get(HEIGHT),getZ()+z);
    }

    @Override public void onTrackedDataSet(TrackedData<?> key) {
        super.onTrackedDataSet(key);
        if (key.equals(WIDTH) || key.equals(HEIGHT) || key.equals(DEPTH)) setBoundingBox(calculateBoundingBox());
    }

    private OrcafisEntity owner() {
        if (owner != null) return owner;
        if (getWorld().getEntityById(dataTracker.get(OWNER)) instanceof OrcafisEntity boss) return boss;
        return null;
    }

    public boolean belongsTo(OrcafisEntity boss) { return owner() == boss; }

    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && !canHit()) discard();
    }

    @Override public boolean canHit() {
        OrcafisEntity boss = owner();
        return boss != null && boss.isAlive() && !boss.belowGround();
    }

    @Override public boolean isCollidable() { return canHit(); }
    @Override public boolean isPartOf(Entity entity) { return entity == this || entity == owner(); }
    @Override public boolean isAttackable() { return canHit(); }

    @Override public boolean damage(DamageSource source, float amount) {
        OrcafisEntity boss = owner();
        return !getWorld().isClient && canHit() && boss.damage(source,amount);
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
