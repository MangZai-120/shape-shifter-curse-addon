package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FrozenSwingAnimationMixin {
    @Inject(method = "tickHandSwing",at = @At("HEAD"),cancellable = true)
    private void ssca$pauseSwingClock(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.getWorld().isClient && FrozenAnimationDrivers.paused(entity)) {
            entity.lastHandSwingProgress = entity.handSwingProgress;
            ci.cancel();
        }
    }
}
