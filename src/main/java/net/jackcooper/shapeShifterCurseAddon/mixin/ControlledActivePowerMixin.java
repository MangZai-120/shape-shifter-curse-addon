package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.Power;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Power.class)
public abstract class ControlledActivePowerMixin implements AbilityUseGuard.PowerOwner {
    @Shadow protected LivingEntity entity;

    @Override
    public LivingEntity ssca$abilityOwner() { return entity; }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$blockControlledActivePower(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Active && AbilityUseGuard.blocked(entity)) cir.setReturnValue(false);
    }
}
