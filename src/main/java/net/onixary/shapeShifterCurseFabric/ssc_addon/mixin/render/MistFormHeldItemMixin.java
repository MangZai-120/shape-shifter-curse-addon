package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 血雾 / 真隐身期间隐藏玩家手持物品（主手 + 副手）。
 * vanilla 隐身效果不会隐藏手持物，需要在 HeldItemFeatureRenderer.render 头部直接 cancel。
 */
@Mixin(HeldItemFeatureRenderer.class)
public class MistFormHeldItemMixin {

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void ssc_addon$hideHeldItemDuringMist(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
												 LivingEntity entity, float limbAngle, float limbDistance,
												 float tickDelta, float animationProgress, float headYaw, float headPitch,
												 CallbackInfo ci) {
		if (entity.hasStatusEffect(SscAddon.MIST_FORM)
				|| entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
			ci.cancel();
		}
	}
}
