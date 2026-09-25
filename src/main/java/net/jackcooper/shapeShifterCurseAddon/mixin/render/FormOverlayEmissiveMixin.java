package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.FormOverlayRenderLayers;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderFeature;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixes SSC and addon forms through their shared renderer, without changing SSC itself. */
@Mixin(value = FormRenderFeature.class, remap = false)
public abstract class FormOverlayEmissiveMixin {
    @Inject(method = "rM_PartB", at = @At("HEAD"))
    private static void ssca$bindOverlayPlayer(PlayerEntityRenderer renderer, AbstractClientPlayerEntity player,
                                               float yaw, float tickDelta, MatrixStack matrices,
                                               VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        // This pass runs before the Geo feature binds its player. Shared renderers must not
        // resolve a previous player's custom skin or palette for the overlay/emissive pair.
        if (player.isSpectator()) return;
        boolean slim = ((PlayerEntityModelAccessor) renderer.getModel()).ssca$hasThinArms();
        for (FormRenderer formRenderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
            if (formRenderer != null) formRenderer.setPlayer(player, slim);
        }
    }

    @WrapOperation(method = "rM_PartB", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/render/RenderLayer;getEntityTranslucentEmissive(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
    private static RenderLayer ssca$alignEmissiveLayer(Identifier texture, Operation<RenderLayer> original) {
        return FormOverlayRenderLayers.usesViewOffset()
                ? FormOverlayRenderLayers.emissive(texture) : original.call(texture);
    }

    @WrapOperation(method = "rM_PartB", at = @At(value = "INVOKE", remap = true, ordinal = 2,
            target = "Lnet/minecraft/client/render/entity/model/PlayerEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private static void ssca$alignEmissiveGeometry(PlayerEntityModel<?> model, MatrixStack matrices,
                                                  VertexConsumer vertices, int light, int overlay,
                                                  float red, float green, float blue, float alpha,
                                                  Operation<Void> original) {
        // The first two calls are SSC's normal overlay branches; only the third is emissive.
        matrices.push();
        try {
            if (FormOverlayRenderLayers.scaleInventoryOverlay()) matrices.scale(1.02F, 1.02F, 1.02F);
            original.call(model, matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    overlay, red, green, blue, alpha);
        } finally {
            matrices.pop();
        }
    }

    @Inject(method = "rFPM_PartB", at = @At("TAIL"))
    private static void ssca$renderFirstPersonEmissive(PlayerEntityRenderer renderer, MatrixStack matrices,
                                                      VertexConsumerProvider consumers, int light,
                                                      AbstractClientPlayerEntity player, ModelPart arm,
                                                      ModelPart sleeve, CallbackInfo ci) {
        if (player.isSpectator() || player.isInvisible()) return;
        boolean slim = ((PlayerEntityModelAccessor) renderer.getModel()).ssca$hasThinArms();
        for (FormRenderer formRenderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
            if (formRenderer == null) continue;
            formRenderer.setPlayer(player, slim);
            FormModel model = (FormModel) formRenderer.getGeoModel();
            Identifier texture = model.getEmissiveTextureResource(slim);
            if (texture == null) continue;
            // Vanilla skin arms need this pass even when there is no GeckoLib arm bone.
            // ModelPart.render retains SSC's part visibility and the player's sleeve setting.
            VertexConsumer vertices = consumers.getBuffer(FormOverlayRenderLayers.emissive(texture));
            arm.render(matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV);
            sleeve.render(matrices, vertices, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV);
        }
    }
}
