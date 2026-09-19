package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 施法第一人称视觉（jackcooper）：施法读条时双手（含手持物品）向前抬起，
 * 与第三人称举手动画（{@code SpellCastPoseMixin}）配套，对齐铁魔法举手施法的观感。
 * <p>
 * 在逐手渲染入口独立压入矩阵，抬手并错相摆动，返回时恢复矩阵。
 * 第一人称只有本人可见，第三人称由两套模型姿态 mixin 对应处理。
 */
@Mixin(HeldItemRenderer.class)
public abstract class SpellCastFirstPersonMixin {

	@Inject(method = "renderFirstPersonItem",
			at = @At("HEAD"), require = 0)
	private void ssca$raiseFirstPersonHands(net.minecraft.client.network.AbstractClientPlayerEntity player,
			float tickDelta, float pitch, net.minecraft.util.Hand hand, float swingProgress,
			net.minecraft.item.ItemStack item, float equipProgress, MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		matrices.push();
		if (player == null || !CastingVisualState.localPlayerCasting()) return;
		// FERAL 兽形不播放举手动画（与第三人称 SpellCastPoseMixin 的排除一致）
		// 1.9.2 适配：组件在 player_form.ability 包，当前形态用 getCurrentForm()
		net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent component;
		try {
			component = net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent.PLAYER_FORM.get(player);
		} catch (Exception exception) {
			return;
		}
		if (component == null || component.getCurrentForm() == null
				|| component.getCurrentForm().getBodyType() != net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.NORMAL) return;
		float progress = CastingVisualState.raiseProgress(player.getUuid());
		if (progress <= 0.0F) return;
		// 双手整体向前推 + 轻微上抬 + 内收（铁魔法施法观感）；随抬手进度渐进
		float push = MathHelper.lerp(progress, 0.0F, -1.1F);
		float lift = MathHelper.lerp(progress, 0.0F, 0.45F);
		matrices.translate(0.0F, lift, push);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(progress, 0.0F, 25.0F)));
		boolean right = (hand == net.minecraft.util.Hand.MAIN_HAND)
				== (player.getMainArm() == net.minecraft.util.Arm.RIGHT);
		float sway = CastingVisualState.armSway(player.age + tickDelta, right);
		matrices.translate(0, 0.018F * sway * progress, 0.10F * sway * progress);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sway * progress * 4.0F));
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
	private void ssca$restoreFirstPersonHands(net.minecraft.client.network.AbstractClientPlayerEntity player,
			float tickDelta, float pitch, net.minecraft.util.Hand hand, float swingProgress,
			net.minecraft.item.ItemStack item, float equipProgress, MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		matrices.pop();
	}
}
