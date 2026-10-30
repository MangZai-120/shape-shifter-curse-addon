package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.util.StableSscScaleWrites;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyStepHeightPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import virtuoel.pehkui.api.ScaleData;

/** Keep native condition evaluation and lifecycle callbacks; omit only stable tick writes. */
@Mixin(value = ModifyStepHeightPower.class, remap = false)
public abstract class StepHeightScaleWritesMixin {
    @WrapOperation(method = "tick", remap = false, require = 2,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lvirtuoel/pehkui/api/ScaleData;setScale(F)V"))
    private void ssca$skipStableScale(ScaleData data, float desired, Operation<Void> original) {
        if (!StableSscScaleWrites.skipScale(data, desired)) original.call(data, desired);
    }

    @WrapOperation(method = "tick", remap = false, require = 2,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lvirtuoel/pehkui/api/ScaleData;setPersistence(Ljava/lang/Boolean;)V"))
    private void ssca$skipStablePersistence(ScaleData data, Boolean desired, Operation<Void> original) {
        if (!StableSscScaleWrites.skipPersistence(data, desired)) original.call(data, desired);
    }
}
