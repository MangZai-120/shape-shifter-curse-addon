package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.ability.fruit.FruitStatusOverlay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ParasiticFruitStatusMixin {
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;canHaveStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z"))
    private void ssca$trackFruitMerge(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        FruitStatusOverlay.attempted((LivingEntity) (Object) this, effect, false);
    }
    @Inject(method = "setStatusEffect", at = @At("HEAD"))
    private void ssca$trackForcedExternalEffect(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        FruitStatusOverlay.attempted((LivingEntity) (Object) this, effect, true);
    }
    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void ssca$trackEffectRemoval(StatusEffectInstance effect, CallbackInfo ci) {
        FruitStatusOverlay.removed((LivingEntity) (Object) this, effect.getEffectType());
    }
}
