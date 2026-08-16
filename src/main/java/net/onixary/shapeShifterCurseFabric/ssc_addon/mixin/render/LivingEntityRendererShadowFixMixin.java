package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.IrisShadowPassDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Apoli LivingEntityRendererMixin.preventFeatureRendering 在 Iris 阴影
 * 渲染重入路径下触发的 NullPointerException 崩溃（sourceStage==null）。
 *
 * 原理：当 Iris 正在渲染阴影 pass 时，进入 LivingEntityRenderer.render 期间
 * 设置 ThreadLocal 标志，让 PowerHolderComponent.getPowers 暂时返回空列表，
 * 使 Apoli 的 anyMatch 判断短路为 false，从而跳过崩溃路径而不影响视觉
 * （阴影 pass 中玩家 feature 细节本就不可见）。
 *
 * 正常主渲染 pass 不受影响，Apoli 原功能完整保留。
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererShadowFixMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sscAddon$markShadowSuppressionStart(LivingEntity entity, float f, float g,
                                                     net.minecraft.client.util.math.MatrixStack matrixStack,
                                                     net.minecraft.client.render.VertexConsumerProvider vertexConsumerProvider,
                                                     int i, CallbackInfo ci) {
        if (IrisShadowPassDetector.isRenderingShadowPass()) {
            IrisShadowPassDetector.enterSuppressScope();
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void sscAddon$markShadowSuppressionEnd(LivingEntity entity, float f, float g,
                                                   net.minecraft.client.util.math.MatrixStack matrixStack,
                                                   net.minecraft.client.render.VertexConsumerProvider vertexConsumerProvider,
                                                   int i, CallbackInfo ci) {
        if (IrisShadowPassDetector.shouldSuppressPowerQuery()) {
            IrisShadowPassDetector.exitSuppressScope();
        }
    }
}
