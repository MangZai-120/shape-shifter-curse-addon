package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.AxolotlTailFlintRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class AxolotlTailFlintPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ssca$panicPose(LivingEntity entity, float limbAngle, float limbDistance, float age,
                                float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player) {
            AxolotlTailFlintRenderer.pose((PlayerEntityModel<?>)(Object)this, player,
                    MinecraftClient.getInstance().getTickDelta());
        }
    }
}
