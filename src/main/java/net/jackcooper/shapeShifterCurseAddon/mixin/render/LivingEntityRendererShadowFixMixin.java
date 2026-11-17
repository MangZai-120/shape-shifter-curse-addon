package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.util.IrisShadowPassDetector;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Bounds the Iris/Apoli workaround to the entity currently being rendered. */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererShadowFixMixin {
    @WrapMethod(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void ssca$shadowScope(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light, Operation<Void> original) {
        boolean shadow = IrisShadowPassDetector.isRenderingShadowPass();
        if (!shadow && !IrisShadowPassDetector.hasSuppressScope()) {
            original.call(entity, yaw, tickDelta, matrices, consumers, light);
            return;
        }
        try (var scope = IrisShadowPassDetector.suppressFor(shadow ? entity : null)) {
            original.call(entity, yaw, tickDelta, matrices, consumers, light);
        }
    }
}
