package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentProjectileEntity.class)
public abstract class FrozenProjectileKnockbackMixin {
    @WrapOperation(method = "onEntityHit",at = @At(value = "INVOKE",target = "Lnet/minecraft/entity/LivingEntity;addVelocity(DDD)V"))
    private void ssca$ignoreArrowKnockback(LivingEntity target,double x,double y,double z,Operation<Void> original) {
        if (!FrozenControl.blocked(target)) original.call(target,x,y,z);
    }
}
