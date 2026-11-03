package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenVanillaAnimations;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.AnimationState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SinglePartEntityModel.class)
public abstract class FrozenNativeAnimationMixin {
    @WrapMethod(method = "updateAnimation(Lnet/minecraft/entity/AnimationState;Lnet/minecraft/client/render/entity/animation/Animation;FF)V")
    private void ssca$resumeNativeClip(AnimationState state,Animation animation,float time,float speed,Operation<Void> original) {
        var sample=FrozenVanillaAnimations.select(state,animation,time);
        original.call(sample.state(),animation,sample.time(),speed);
    }
}
