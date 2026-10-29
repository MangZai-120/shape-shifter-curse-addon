package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

/** 目标ID由原版DataTracker同步；双端转向，不额外每tick发包、不扫描实体。 */
@Mixin(ProjectileEntity.class)
public abstract class SpellHomingProjectileMixin implements FormSpellStyle.HomingProjectile {
    @Unique private static final TrackedData<Integer> SSCA_TARGET=
            DataTracker.registerData(ProjectileEntity.class,TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Float> SSCA_FLIGHT_SPEED=
            DataTracker.registerData(ProjectileEntity.class,TrackedDataHandlerRegistry.FLOAT);
    @Unique private static final TrackedData<Float> SSCA_LAUNCH_SPEED=
            DataTracker.registerData(ProjectileEntity.class,TrackedDataHandlerRegistry.FLOAT);
    @Unique private UUID ssca$target;
    @Unique private Vec3d ssca$previous;
    @Unique private double ssca$remaining;

    @Inject(method="<init>",at=@At("TAIL"))
    private void ssca$init(EntityType<? extends ProjectileEntity> type,World world,CallbackInfo ci) {
        var data=((ProjectileEntity)(Object)this).getDataTracker();
        data.startTracking(SSCA_TARGET,-1);data.startTracking(SSCA_FLIGHT_SPEED,1f);data.startTracking(SSCA_LAUNCH_SPEED,0f);
    }
    @Override public void ssca$flight(double speedMultiplier,double maximumRange) {
        var self=(ProjectileEntity)(Object)this;
        if(self.getWorld().isClient||ssca$previous!=null)return;
        self.getDataTracker().set(SSCA_FLIGHT_SPEED,(float)speedMultiplier);
        self.setVelocity(self.getVelocity().multiply(speedMultiplier));
        self.getDataTracker().set(SSCA_LAUNCH_SPEED,(float)self.getVelocity().length());
        ssca$remaining=maximumRange;ssca$previous=self.getPos();
    }
    @Override public double ssca$flightSpeedMultiplier() {
        return ((ProjectileEntity)(Object)this).getDataTracker().get(SSCA_FLIGHT_SPEED);
    }
    @Override public void ssca$syncFlightVelocity() {
        var self=(ProjectileEntity)(Object)this;
        if(ssca$flightSpeedMultiplier()==1||!ssca$linear(self))return;
        double speed=self.getDataTracker().get(SSCA_LAUNCH_SPEED);
        if(speed>0&&self.getVelocity().lengthSquared()>1e-12)self.setVelocity(self.getVelocity().normalize().multiply(speed));
    }
    @Override public void ssca$home(LivingEntity target,double range) {
        var self=(ProjectileEntity)(Object)this;
        if(ssca$previous==null){ssca$remaining=range;ssca$previous=self.getPos();}
        ssca$target=target.getUuid();
        self.getDataTracker().set(SSCA_TARGET,target.getId());
    }
    @Override public boolean ssca$isHoming() {
        return ((ProjectileEntity)(Object)this).getDataTracker().get(SSCA_TARGET)>=0;
    }
    @Override public boolean ssca$hasRangeLimit() {return ssca$previous!=null;}
    @Override public Vec3d ssca$guidedVelocity(Vec3d ordinary) {
        var self=(ProjectileEntity)(Object)this;
        var entity=self.getWorld().getEntityById(self.getDataTracker().get(SSCA_TARGET));
        if(entity instanceof LivingEntity target&&target.isAlive()) {
            Vec3d delta=target.getBoundingBox().getCenter().subtract(self.getPos());
            if(delta.lengthSquared()>=1e-8)ordinary=delta.normalize().multiply(ordinary.length());
        }
        if(!self.getWorld().isClient&&ssca$previous!=null&&ordinary.length()>ssca$remaining)
            ordinary=ordinary.normalize().multiply(Math.max(0,ssca$remaining));
        return ordinary;
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void ssca$tick(CallbackInfo ci) {
        var self=(ProjectileEntity)(Object)this;
        // 速度包按原版量化且限幅；匀速法术从同步快照恢复真实速度，不让高速配置在客机变慢。
        if(self.getWorld().isClient)ssca$syncFlightVelocity();
        if(!self.getWorld().isClient&&ssca$previous!=null) {
            ssca$remaining-=self.getPos().distanceTo(ssca$previous);ssca$previous=self.getPos();
            if(ssca$remaining<=1e-7){self.discard();return;}
            var entity=ssca$target==null?null:((ServerWorld)self.getWorld()).getEntity(ssca$target);
            if(entity instanceof LivingEntity target&&self.getOwner() instanceof ServerPlayerEntity owner
                    &&FormSpellStyle.legalTarget(owner,target))self.getDataTracker().set(SSCA_TARGET,target.getId());
            else {ssca$target=null;self.getDataTracker().set(SSCA_TARGET,-1);}
        }
        if(!ssca$isHoming()&&ssca$previous==null)return;
        Vec3d velocity=ssca$isHoming()?ssca$guidedVelocity(self.getVelocity()):self.getVelocity();
        // 临近射程终点只走剩余距离，避免高速弹体多飞一整tick。
        if(!self.getWorld().isClient&&ssca$previous!=null&&velocity.length()>ssca$remaining)
            velocity=velocity.normalize().multiply(ssca$remaining);
        self.setVelocity(velocity);
        double horizontal=Math.sqrt(velocity.x*velocity.x+velocity.z*velocity.z);
        self.setYaw((float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)));
        self.setPitch((float)Math.toDegrees(Math.atan2(-velocity.y,horizontal)));
    }
    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void ssca$write(NbtCompound nbt,CallbackInfo ci) {
        if(ssca$previous==null)return;
        var self=(ProjectileEntity)(Object)this;
        // 本tick移动在super.tick之后发生，存档时也要结清，防反复卸载区块延长射程。
        nbt.putDouble("SscaHomingRemaining",Math.max(0,ssca$remaining-self.getPos().distanceTo(ssca$previous)));
        nbt.putFloat("SscaFlightSpeed",(float)ssca$flightSpeedMultiplier());
        nbt.putFloat("SscaLaunchSpeed",self.getDataTracker().get(SSCA_LAUNCH_SPEED));
        if(ssca$target!=null)nbt.putUuid("SscaHomingTarget",ssca$target);
    }
    @Inject(method="onEntityHit",at=@At("HEAD"))
    private void ssca$hit(EntityHitResult hit,CallbackInfo ci) {
        var self=(ProjectileEntity)(Object)this;
        if(!self.getWorld().isClient&&hit.getEntity().getUuid().equals(ssca$target)) {
            // 穿刺弹命中锁定目标后继续向前，不能回头追逐已命中的同一个目标。
            ssca$target=null;self.getDataTracker().set(SSCA_TARGET,-1);
        }
    }
    @Inject(method="readCustomDataFromNbt",at=@At("TAIL"))
    private void ssca$read(NbtCompound nbt,CallbackInfo ci) {
        if(!nbt.contains("SscaHomingRemaining"))return;
        var self=(ProjectileEntity)(Object)this;
        double remaining=nbt.getDouble("SscaHomingRemaining");
        ssca$remaining=Double.isFinite(remaining)?Math.max(0,remaining):0;ssca$previous=self.getPos();
        ssca$target=nbt.containsUuid("SscaHomingTarget")?nbt.getUuid("SscaHomingTarget"):null;
        float factor=nbt.contains("SscaFlightSpeed")?nbt.getFloat("SscaFlightSpeed"):1;
        float launch=nbt.getFloat("SscaLaunchSpeed");
        self.getDataTracker().set(SSCA_FLIGHT_SPEED,Float.isFinite(factor)&&factor>=1&&factor<=5?factor:1);
        self.getDataTracker().set(SSCA_LAUNCH_SPEED,Float.isFinite(launch)&&launch>=0?launch:0);
    }
    @Unique private static boolean ssca$linear(ProjectileEntity entity) {
        return entity instanceof net.jackcooper.shapeShifterCurseAddon.entity.SpellFireBoltEntity
                ||entity instanceof net.jackcooper.shapeShifterCurseAddon.entity.SpellFrostSpikeEntity
                ||entity instanceof net.jackcooper.shapeShifterCurseAddon.entity.SpellMoonlightArrowEntity
                ||entity instanceof net.jackcooper.shapeShifterCurseAddon.entity.SpellCurseMarkEntity;
    }
}
