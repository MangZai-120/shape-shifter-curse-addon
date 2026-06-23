package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateController.TransformingController;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 变身成「附属(my_addon)的四足(FERAL)形态」时，强制播放主包的「直立→四足」变身动画（趴下变四足），
 * 而不是默认的直立→直立动画。
 *
 * <p>背景：主包 {@code TransformingController#getAnimation} 按「变身前→变身后」的 bodyType 选动画——
 * 只有 {@code NORMAL→FERAL} 才播四足动画。而玩家用月髓环/进化石进化 SP 形态时，变身前往往已经是
 * 基础四足(FERAL)形态，于是命中 {@code FERAL→FERAL} 分支、播了直立动画。这就是「附属四足形态变身全是
 * 直立→直立」的根因。
 *
 * <p>本 mixin 仅在「变身目标是 my_addon 命名空间、且 bodyType 为 FERAL」时介入、改播四足动画；
 * 主包自身形态以及附属的直立(NORMAL)形态完全不受影响，从而把影响面严格限制在附属四足形态上。
 *
 * <p>纯客户端渲染逻辑，按观察者各自计算，天然适配多人环境（每个客户端为正在渲染的玩家选动画）。
 * 这里以与主包 {@code TransformingController.registerAnim} 一致的方式重建 AnimationHolder
 * （MOD_ID + 动画名固定），避免 @Shadow 主包私有静态字段带来的混淆映射告警与潜在解析风险。
 */
@Mixin(TransformingController.class)
public class TransformingControllerFeralAnimMixin {

	/** 主包「直立→四足」变身动画 id（与 TransformingController.registerAnim 中一致）。 */
	private static final Identifier SSC_ADDON_NORMAL_TO_FERAL_ANIM =
			new Identifier("shape-shifter-curse", "player_on_transform_normal_to_feral");

	@Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true)
	private void sscAddon$forceFeralTransformAnim(PlayerEntity player, AnimSystem.AnimSystemData data,
												  CallbackInfoReturnable<AnimationHolder> cir) {
		String toFormName = ShapeShifterCurseFabricClient.getClientTransformToForm(player.getUuid());
		if (toFormName == null) {
			return;
		}
		IForm toForm;
		try {
			toForm = RegPlayerForms.getPlayerForm(toFormName);
		} catch (IllegalArgumentException e) {
			return; // 形态名解析失败，交回原逻辑
		}
		if (toForm == null || toForm.getFormID() == null) {
			return;
		}
		// 仅作用于附属(my_addon)的四足形态，主包形态走原逻辑、不受影响
		if (!"my_addon".equals(toForm.getFormID().getNamespace())) {
			return;
		}
		// 四足判定：bodyType 为 FERAL；或显式纳入的四足形态——契灵(mancianima)是数据驱动形态、
		// bodyType 非 FERAL，但视觉上是四足狐（以使魔为胚体），故按形态 ID 单独纳入，仅改变身动画、不动其渲染。
		boolean isFeralForm = toForm.getBodyType() == PlayerFormBodyType.FERAL
				|| "familiar_fox_mancianima".equals(toForm.getFormID().getPath());
		if (!isFeralForm) {
			return;
		}
		// 无论变身前是什么形态，变成附属四足形态时都播放「直立→四足」变身动画
		cir.setReturnValue(new AnimationHolder(SSC_ADDON_NORMAL_TO_FERAL_ANIM, true));
	}
}
