package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第三人称<b>纯渲染</b>把主手渲染的物品替换成 3D 水矛（不动玩家背包）。
 *
 * <p>重定向 {@code HeldItemFeatureRenderer.render} 里的 {@code entity.getMainHandStack()}：
 * 蓄力中返回带 {@code CustomModelData=1} 的水矛（恒定 3D）。覆盖左/右利手（该调用在两种利手分支各出现一次，
 * 只有实际利手那支执行）与<b>空手</b>情况（返回非空水矛使渲染分支不被 isEmpty 跳过）。
 * 举矛过肩姿势由 {@code UpgradeAxolotlSpearArmPoseMixin}(getArmPose→THROW_SPEAR) 提供。多人下对被追踪的
 * 蓄力玩家同样生效（蓄力状态已广播给追踪端）。</p>
 */
@Mixin(HeldItemFeatureRenderer.class)
public class UpgradeAxolotlThirdPersonSpearMixin {

	@Redirect(
			method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;")
	)
	private ItemStack ssc_addon$swapTpSpear(LivingEntity entity) {
		if (entity instanceof AbstractClientPlayerEntity
				&& UpgradeAxolotlSpearRenderState.isCharging(entity.getUuid())) {
			ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
			spear.getOrCreateNbt().putInt("CustomModelData", 1);
			return spear;
		}
		return entity.getMainHandStack();
	}

	// 蓄力期把主手渲染的水矛「举起过肩」（第三人称纯渲染抬矛蓄力效果；数值可实机微调）
	@Inject(method = "renderItem", at = @At("HEAD"))
	private void ssc_addon$raiseTpPush(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (arm == entity.getMainArm() && UpgradeAxolotlSpearRenderState.isCharging(entity.getUuid())) {
			matrices.push();
			// 举矛过肩、接近三叉戟蓄力（第三人称：举更高、更竖直、轻微后仰；数值可实机微调）
			matrices.translate(0.0, 0.6, -0.1);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20.0F));
		}
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void ssc_addon$raiseTpPop(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (arm == entity.getMainArm() && UpgradeAxolotlSpearRenderState.isCharging(entity.getUuid())) {
			matrices.pop();
		}
	}
}
