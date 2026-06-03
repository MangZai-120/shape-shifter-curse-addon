package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderFeature;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.render.OutlinePassTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bug 2 修复：发光描边显示 vanilla 玩家模型而非形态模型。
 * 根因：FormRenderFeature 调 formRenderer.render(... null, light) 时第 5 参数 buffer 传 null，
 * AzureLib GeoObjectRenderer.render 内部会 if(buffer==null) bufferSource = mc.worldRenderer...getEntityVertexConsumers()
 * 直接覆写传入的 OutlineVertexConsumerProvider，导致 outline pass 中形态 vertex 写到了 main framebuffer 而不是 outline framebuffer。
 * 修复：拦截这两次调用，把 null 替换为 vertexConsumers.getBuffer(layer)，AzureLib 就不会再覆写 bufferSource。
 */
@Mixin(value = FormRenderFeature.class, remap = false)
public abstract class FormRenderFeatureOutlineMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/player/PlayerEntity;FFFFFF)V",
        at = @At("HEAD")
    )
    private void ssc_addon$markOutlinePassEnter(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                 PlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
                                                 float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (vertexConsumers instanceof OutlineVertexConsumerProvider) {
            OutlinePassTracker.enter();
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/player/PlayerEntity;FFFFFF)V",
        at = @At("RETURN")
    )
    private void ssc_addon$markOutlinePassExit(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                PlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
                                                float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (vertexConsumers instanceof OutlineVertexConsumerProvider) {
            OutlinePassTracker.exit();
        }
    }

    @Redirect(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/player/PlayerEntity;FFFFFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/onixary/shapeShifterCurseFabric/render/form_render/FormRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lmod/azure/azurelib/core/animatable/GeoAnimatable;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;I)V"
        )
    )
    private void ssc_addon$ensureBuffer(FormRenderer renderer,
                                        MatrixStack matrices,
                                        FormAnimatable formAnimatable,
                                        VertexConsumerProvider vertexConsumers,
                                        RenderLayer layer,
                                        VertexConsumer buffer,
                                        int light) {
        if (buffer == null && vertexConsumers != null && layer != null) {
            buffer = vertexConsumers.getBuffer(layer);
        }
        renderer.render(matrices, formAnimatable, vertexConsumers, layer, buffer, light);
    }
}
