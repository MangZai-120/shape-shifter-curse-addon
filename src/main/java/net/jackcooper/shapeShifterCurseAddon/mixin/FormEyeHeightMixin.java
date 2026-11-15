package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.forms.FormEyeHeights;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before Pehkui's LivingEntity#getEyeHeight multiplier, on both logical sides. */
@Mixin(PlayerEntity.class)
public abstract class FormEyeHeightMixin {
    @Unique private boolean ssca$eyeProfileReady;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ssca$eyeProfileReady(CallbackInfo ci) { ssca$eyeProfileReady = true; }

    @Inject(method = "getActiveEyeHeight", at = @At("RETURN"), cancellable = true)
    private void ssca$alignFormEyes(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        // Entity's constructor computes eye height before effects and components exist.
        if (ssca$eyeProfileReady) cir.setReturnValue(FormEyeHeights.correct((PlayerEntity)(Object)this, pose, cir.getReturnValueF()));
    }
}
