package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
@Mixin(value = AnimationProcessor.class,remap = false)
public abstract class FrozenGeoTimelineMixin {
    @WrapMethod(method = "tickAnimation")
    private void ssca$resumePlayhead(GeoAnimatable animatable,CoreGeoModel<GeoAnimatable> model,
            AnimatableManager<GeoAnimatable> manager,double time,AnimationState<GeoAnimatable> state,
            boolean crash,Operation<Void> original) {
        double playhead = FrozenAnimationDrivers.geoTime(manager,time);
        state.animationTick = playhead;
        original.call(animatable,model,manager,playhead,state,crash);
    }
}
