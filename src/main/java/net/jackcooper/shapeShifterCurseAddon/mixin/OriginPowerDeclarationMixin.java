package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import io.github.apace100.apoli.power.PowerType;
import net.jackcooper.shapeShifterCurseAddon.compat.OriginPowerReloadGuard;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PowerHolderComponentImpl.class, remap = false)
public abstract class OriginPowerDeclarationMixin {
    @Inject(method = "addPower", at = @At("HEAD"))
    private void ssca$declareBeforeExistingPowerFastReturn(PowerType<?> power, Identifier source,
                                                         CallbackInfoReturnable<Boolean> cir) {
        OriginPowerReloadGuard.declare((PowerHolderComponent) (Object) this, power, source);
    }

    @Inject(method = "removePower", at = @At("HEAD"), cancellable = true)
    private void ssca$keepDeclaredChildDuringCleanup(PowerType<?> power, Identifier source, CallbackInfo ci) {
        if (OriginPowerReloadGuard.keepDuringCleanup((PowerHolderComponent) (Object) this, power, source)) ci.cancel();
    }
}
