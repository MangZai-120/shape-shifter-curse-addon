package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.util.StableSscScaleWrites;
import net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import virtuoel.pehkui.api.ScaleData;

/** Both pose branches keep native instant resizing, including interrupted interpolation. */
@Mixin(value = CrawlingPower.class, remap = false)
public abstract class CrawlingScaleWritesMixin {
    @WrapOperation(method = "tick", remap = false, require = 4,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lvirtuoel/pehkui/api/ScaleData;setScale(F)V"))
    private void ssca$skipStableScale(ScaleData data, float desired, Operation<Void> original) {
        if (!StableSscScaleWrites.skipScale(data, desired)) original.call(data, desired);
    }
}
