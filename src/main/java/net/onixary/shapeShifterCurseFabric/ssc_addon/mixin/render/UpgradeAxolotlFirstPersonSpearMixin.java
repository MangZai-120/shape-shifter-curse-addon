package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第一人称<b>纯渲染</b>把主手物品替换成 3D 水矛（不动玩家背包），
 * 并举成「过肩、准备投掷」的姿势（矩阵变换包裹渲染）。
 * 靠 {@code CustomModelData=1} 走 custom_model_data override 恒定 3D。
 */
@Mixin(HeldItemRenderer.class)
public class UpgradeAxolotlFirstPersonSpearMixin {

	// 蓄力期把主手渲染的物品替换成 3D 水矛（仅渲染层，不改背包）
	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack ssc_addon$swapFpSpear(ItemStack item, AbstractClientPlayerEntity player,
			float tickDelta, float pitch, Hand hand) {
		if (player != null && hand == Hand.MAIN_HAND
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
			spear.getOrCreateNbt().putInt("CustomModelData", 1);
			return spear;
		}
		return item;
	}

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void ssc_addon$raiseFpPush(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (hand == Hand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			matrices.push();
			// 举矛过肩、明显抡起蓄力（第一人称：举高 + 后仰；数值可实机微调）
			matrices.translate(0.05, 0.45, 0.05);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-58.0F));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(6.0F));
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void ssc_addon$raiseFpPop(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (hand == Hand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			matrices.pop();
		}
	}
}
