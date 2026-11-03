package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = AnimationStack.class,remap = false)
public abstract class FrozenPlayerAnimationStackMixin {
    @Inject(method = "tick",at = @At("HEAD"),cancellable = true)
    private void ssca$pausePlayback(CallbackInfo ci) {
        if (FrozenAnimationDrivers.paused((AnimationStack)(Object)this)) ci.cancel();
    }
    @ModifyVariable(method = "setupAnim",at = @At("HEAD"),argsOnly = true)
    private float ssca$pausePartialTick(float delta) {
        return FrozenAnimationDrivers.partial((AnimationStack)(Object)this,delta);
    }
}
