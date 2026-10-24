package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.apace100.apoli.networking.ModPacketsC2S;
import io.github.apace100.apoli.power.Active;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModPacketsC2S.class)
public abstract class ControlledApoliUseMixin {
    /** Includes the queued server lambda, without depending on its compiler-generated name. */
    @WrapOperation(method = "*", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lio/github/apace100/apoli/power/Active;onUse()V"))
    private static void ssca$guardActiveDispatch(Active active, Operation<Void> original) {
        if (!(active instanceof AbilityUseGuard.PowerOwner owner) || !AbilityUseGuard.blocked(owner.ssca$abilityOwner())) {
            original.call(active);
        }
    }
}
