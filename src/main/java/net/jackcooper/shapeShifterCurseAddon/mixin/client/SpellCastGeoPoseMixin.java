package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FormPoseOverrides;
import net.jackcooper.shapeShifterCurseAddon.client.AxolotlTailFlintRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends arbitrary SSC/resource-pack models only while SSCA pose states are active. */
@Mixin(DefaultModelAnimationSystem.class)
public abstract class SpellCastGeoPoseMixin {
    @Inject(method = "afterRender", at = @At("TAIL"), remap = false)
    private void ssca$renderTailFlint(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers, int light,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress,
            float headYaw, float headPitch, CallbackInfo ci) {
        AxolotlTailFlintRenderer.render(model, player, matrices, consumers, light);
    }

    @Inject(method = "processAnimation", at = @At("TAIL"))
    private void ssca$poses(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        FormPoseOverrides.snowFox(model, player, tickDelta);
        FormPoseOverrides.spear(formRenderer, model, renderer, player, limbAngle, limbDistance,
                tickDelta, animationProgress, headYaw, headPitch);
        FormPoseOverrides.casting(formRenderer, model, renderer, player, limbAngle, limbDistance,
                tickDelta, animationProgress, headYaw, headPitch);
    }
}
