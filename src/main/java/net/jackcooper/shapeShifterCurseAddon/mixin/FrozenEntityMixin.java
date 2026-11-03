package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FrozenEntityMixin {
    @WrapMethod(method = "damage")
    private boolean ssca$shatterOnEffectiveDamage(DamageSource source, float amount, Operation<Boolean> original) {
        LivingEntity target = (LivingEntity)(Object)this;
        boolean wasFrozen = !target.getWorld().isClient && FrozenControl.blocked(target);
        boolean damaged = original.call(source, amount);
        if (wasFrozen && damaged) FrozenControl.breakFromHit(target);
        if (damaged && !target.getWorld().isClient)
            net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyManager.damageObserved(target);
        net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffects.afterDamage(target, source, damaged);
        return damaged;
    }
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d ssca$freezeInput(Vec3d movementInput) {
        return FrozenControl.blocked((LivingEntity)(Object)this) ? Vec3d.ZERO : movementInput;
    }
    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void ssca$ignoreAttackKnockback(double strength,double x,double z,CallbackInfo ci) {
        if (FrozenControl.blocked((LivingEntity)(Object)this)) ci.cancel();
    }
    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void ssca$releaseRemovedFreeze(StatusEffectInstance effect, CallbackInfo ci) {
        LivingEntity target = (LivingEntity)(Object)this;
        if (effect.getEffectType() == SscAddon.FROZEN && !target.getWorld().isClient) FrozenControl.release(target);
    }
}
