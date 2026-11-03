package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LivingEntityRenderer.class)
public abstract class FrozenVanillaTimelineMixin {
    @ModifyReturnValue(method = "getAnimationProgress",at = @At("RETURN"))
    private float ssca$resumeAnimationTime(float time,LivingEntity entity,float delta) {
        return FrozenAnimationDrivers.vanillaTime(entity,time);
    }
}
