package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class FrozenLimbAnimationMixin {
    @Inject(method = "updateLimbs(F)V",at = @At("HEAD"),cancellable = true)
    private void ssca$pauseLimbPhase(float movement,CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.getWorld().isClient && FrozenAnimationDrivers.paused(entity)) ci.cancel();
    }
}
