package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import mod.azure.azurelib.core.animatable.GeoAnimatable;
import com.llamalad7.mixinextras.sugar.Local;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
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

    @Inject(method = "render", at = @At("HEAD"))
    private void ssc_addon$markOutlinePassEnter(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                 PlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
                                                 float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (vertexConsumers instanceof OutlineVertexConsumerProvider) {
            OutlinePassTracker.enter();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ssc_addon$markOutlinePassExit(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                                PlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
                                                float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (vertexConsumers instanceof OutlineVertexConsumerProvider) {
            OutlinePassTracker.exit();
        }
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/onixary/shapeShifterCurseFabric/render/form_render/FormRenderer;render(Lnet/minecraft/class_4587;Lmod/azure/azurelib/core/animatable/GeoAnimatable;Lnet/minecraft/class_4597;Lnet/minecraft/class_1921;Lnet/minecraft/class_4588;I)V"
        )
    )
    private void ssc_addon$ensureBuffer(FormRenderer renderer,
                                        MatrixStack matrices,
                                        GeoAnimatable formAnimatable,
                                        VertexConsumerProvider vertexConsumers,
                                        RenderLayer layer,
                                        VertexConsumer buffer,
                                        int light) {
        if (buffer == null && vertexConsumers != null && layer != null) {
            buffer = vertexConsumers.getBuffer(layer);
        }
        renderer.render(matrices, (net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable) formAnimatable, vertexConsumers, layer, buffer, light);
    }

    /**
     * Bug 2 修复（第二部分）：发光描边显示的是形态模型的「直立 bind pose + 无动画」版本。
     * 根因：FormModel 未重写 AzureLib 的 setCustomAnimations，形态骨骼姿态完全由
     *      DefaultModelAnimationSystem.processAnimation 在 render 前手动设置；而 AzureLib 在
     *      前面 normal/emissive 两次 formRenderer.render 渲染后会把 GeoBone 复位到 bind pose，
     *      于是第三次（outline 描边）渲染时骨骼已是直立静止姿态。
     * 方案：在 outline 那次 formRenderer.render（ordinal=2）之前，重新跑一次 processAnimation
     *      重设骨骼姿态。复用同一 tailDrag 状态（不重复 beforeRender），对尾巴累计零影响。
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/onixary/shapeShifterCurseFabric/render/form_render/FormRenderer;render(Lnet/minecraft/class_4587;Lmod/azure/azurelib/core/animatable/GeoAnimatable;Lnet/minecraft/class_4597;Lnet/minecraft/class_1921;Lnet/minecraft/class_4588;I)V",
            ordinal = 2
        )
    )
    private void ssc_addon$reapplyPoseBeforeOutline(
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            PlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci,
            @Local FormRenderer formRenderer, @Local FormModel formModel,
            @Local PlayerEntityRenderer playerEntityRenderer) {
        // 仅在 outline 描边渲染前重设骨骼姿态，确保发光描边跟随当前动画而非直立 bind pose
        if (formModel != null && formModel.AnimationSystem != null) {
            formModel.AnimationSystem.processAnimation(formRenderer, formModel, playerEntityRenderer,
                    entity, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }
    }
}


