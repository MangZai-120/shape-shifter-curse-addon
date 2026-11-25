package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Target the shell at its visible size, even when an arrow misses the fox inside it. */
public final class SnowFoxShellEntity extends Entity {
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(SnowFoxShellEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(SnowFoxShellEntity.class,TrackedDataHandlerRegistry.INTEGER);

    public SnowFoxShellEntity(EntityType<? extends SnowFoxShellEntity> type,World world) {
        super(type,world);
        noClip=true;
        setNoGravity(true);
    }
    @Override protected void initDataTracker() {
        dataTracker.startTracking(RADIUS,.75f);
        dataTracker.startTracking(OWNER,-1);
    }
    public void align(ServerPlayerEntity player,Vec3d feet,double radius) {
        dataTracker.set(OWNER,player.getId());
        dataTracker.set(RADIUS,(float)radius);
        setPosition(feet);
    }
    @Override protected Box calculateBoundingBox() {
        if (dataTracker==null) return super.calculateBoundingBox();
        double r=dataTracker.get(RADIUS);
        return new Box(getX()-r,getY(),getZ()-r,getX()+r,getY()+2*r,getZ()+r);
    }
    @Override public void onTrackedDataSet(TrackedData<?> key) {
        super.onTrackedDataSet(key);
        if (RADIUS.equals(key)) setBoundingBox(calculateBoundingBox());
    }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && (!(getWorld().getEntityById(dataTracker.get(OWNER)) instanceof ServerPlayerEntity p)
                || !p.isAlive() || SnowFoxMotion.STATE.get(p).phase()!=SnowFoxState.Phase.SHELL)) discard();
    }
    @Override public boolean canHit() { return !isRemoved(); }
    @Override public boolean isAttackable() { return canHit(); }
    @Override public boolean damage(DamageSource source,float amount) {
        // Explosions and environmental damage already hit the owner through ALLOW_DAMAGE.
        if (getWorld().isClient || source.getSource()==null || source.isIn(DamageTypeTags.IS_EXPLOSION)
                || !(getWorld().getEntityById(dataTracker.get(OWNER)) instanceof ServerPlayerEntity p)
                || source.getAttacker()==p || p.isInvulnerableTo(source)) return false;
        if (source.getAttacker() instanceof ServerPlayerEntity other && (!p.getServer().isPvpEnabled()
                || !other.shouldDamagePlayer(p))) return false;
        return SnowFoxMotion.STATE.get(p).absorb(p,amount);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
