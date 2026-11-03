package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(value = GeoModel.class,remap = false)
public abstract class FrozenGeoPlaybackMixin {
    @ModifyVariable(method = "applyMolangQueries",at = @At("HEAD"),argsOnly = true)
    private double ssca$resumeMolangTime(double time) { return FrozenAnimationDrivers.animationTime(time); }
    @SuppressWarnings("unchecked")
    @WrapMethod(method = "handleAnimations")
    private void ssca$pauseControllers(GeoAnimatable animatable,long id,AnimationState<GeoAnimatable> state,Operation<Void> original) {
        if (!FrozenAnimationDrivers.holdGeo((GeoModel<GeoAnimatable>)(Object)this,animatable,id,state))
            original.call(animatable,id,state);
    }
}
