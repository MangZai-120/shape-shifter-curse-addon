package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

@Mixin(value = AnimationController.class,remap = false)
public abstract class FrozenGeoSelectionMixin {
    @Shadow protected abstract double adjustTick(double time);
    @WrapMethod(method = "handleAnimationState")
    private PlayState ssca$finishResumedClip(AnimationState<?> state,Operation<PlayState> original) {
        return FrozenAnimationDrivers.continueGeo((AnimationController<?>)(Object)this,adjustTick(state.animationTick))
                ? PlayState.CONTINUE : original.call(state);
    }
}
