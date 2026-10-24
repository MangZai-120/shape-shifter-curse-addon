package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.ControlledAbilityInterruption;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ControlledEffectInterruptionMixin {
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("RETURN"))
    private void ssca$interruptOnControl(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (effect.getEffectType() == SscAddon.STUN
                || effect.getEffectType() == SscAddon.PLAYING_DEAD
                || effect.getEffectType() == RegOtherStatusEffects.ENTANGLED_FULL_EFFECT)
                && (Object) this instanceof ServerPlayerEntity player) ControlledAbilityInterruption.interrupt(player);
    }
}
