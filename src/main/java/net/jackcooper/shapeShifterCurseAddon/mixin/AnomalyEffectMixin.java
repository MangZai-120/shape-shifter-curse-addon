package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffectAccess;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Status changes update a lazy cache; damage/heal never scan all status effects or world entities. */
@Mixin(LivingEntity.class)
public abstract class AnomalyEffectMixin implements AnomalyEffectAccess {
    @Unique private AnomalyEffects.Cache ssca$anomalyEffects;
    @Unique private long ssca$lastAnomalyZoneHitTick = Long.MIN_VALUE;
    @Override public AnomalyEffects.Cache ssca$anomalyEffects() { return ssca$anomalyEffects; }
    @Override public void ssca$anomalyEffects(AnomalyEffects.Cache cache) { ssca$anomalyEffects = cache; }
    @Override public long ssca$lastAnomalyZoneHitTick() { return ssca$lastAnomalyZoneHitTick; }
    @Override public void ssca$lastAnomalyZoneHitTick(long tick) { ssca$lastAnomalyZoneHitTick = tick; }

    @Inject(method = "onStatusEffectApplied", at = @At("TAIL"))
    private void ssca$anomalyApplied(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if (AnomalyEffects.isManaged(effect.getEffectType()))
            AnomalyEffects.refresh((LivingEntity)(Object)this);
    }

    @Inject(method = "onStatusEffectUpgraded", at = @At("TAIL"))
    private void ssca$anomalyUpgraded(StatusEffectInstance effect, boolean reapply, Entity source, CallbackInfo ci) {
        if (AnomalyEffects.isManaged(effect.getEffectType()))
            AnomalyEffects.refresh((LivingEntity)(Object)this);
    }

    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void ssca$anomalyRemoved(StatusEffectInstance effect, CallbackInfo ci) {
        if (AnomalyEffects.isManaged(effect.getEffectType()))
            AnomalyEffects.refresh((LivingEntity)(Object)this);
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float ssca$anomalyHealing(float amount) {
        return AnomalyEffects.modifyHealing((LivingEntity)(Object)this, amount);
    }

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void ssca$anomalySprint(boolean sprinting, CallbackInfo ci) {
        if (sprinting && AnomalyEffects.sprintBlocked((LivingEntity)(Object)this)) ci.cancel();
    }
}
