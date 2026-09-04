package net.jackcooper.shapeShifterCurseAddon.mixin.client;

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
 * 变身成「附属(my_addon)的四足(FERAL)形态」时的变身动画选择：
 * <ul>
 *   <li>变身<b>前</b>是直立(NORMAL)形态 → 播主包原版「直立→四足」动画（人类趴下变四足）；</li>
 *   <li>变身<b>前</b>已是四足形态（月髓环/进化石从基础四足进化 SP 的常态）→ 播附属自带的
 *       「四足→四足」动画 {@code player_on_transform_feral_reverse}：前半段 = 完成变身动画的
 *       <b>倒放</b>（从四足站姿趴下蓄力），黑屏保持后正放起身完成，全段四足语义、无人类动作。</li>
 * </ul>
 *
 * <p>背景：主包 {@code TransformingController#getAnimation} 按「变身前→变身后」的 bodyType 选动画——
 * 只有 {@code NORMAL→FERAL} 才播四足动画。而玩家用月髓环/进化石进化 SP 形态时，变身前往往已经是
 * 基础四足(FERAL)形态，于是命中 {@code FERAL→FERAL} 分支、播了直立动画。这就是「附属四足形态变身全是
 * 直立→直立」的根因。</p>
 *
 * <p>本 mixin 仅在「变身目标是 my_addon 命名空间、且 bodyType 为 FERAL」时介入；主包自身形态以及
 * 附属的直立(NORMAL)形态完全不受影响，从而把影响面严格限制在附属四足形态上。</p>
 *
 * <p>纯客户端渲染逻辑，按观察者各自计算，天然适配多人环境（每个客户端为正在渲染的玩家选动画）。
 * 这里以与主包 {@code TransformingController.registerAnim} 一致的方式重建 AnimationHolder
 * （MOD_ID + 动画名固定），避免 @Shadow 主包私有静态字段带来的混淆映射告警与潜在解析风险。</p>
 */
@Mixin(TransformingController.class)
public class TransformingControllerFeralAnimMixin {

	/** 主包「直立→四足」变身动画 id（与 TransformingController.registerAnim 中一致）。 */
	private static final Identifier SSC_ADDON_NORMAL_TO_FERAL_ANIM =
			new Identifier("shape-shifter-curse", "player_on_transform_normal_to_feral");

	/** 附属「四足→四足」变身动画 id（完成动画倒放蓄力 + 正放起身，assets/my_addon/player_animation/）。 */
	private static final Identifier SSC_ADDON_FERAL_REVERSE_ANIM =
			new Identifier("my_addon", "player_on_transform_feral_reverse");

	@Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true, require = 0)
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
		// 变身前已是四足形态时（月髓环/进化石进化的常态），改播「完成动画倒放蓄力 + 正放起身」的
		// 四足专属动画；变身前是直立形态（如指令直接切形态）仍播主包原版「人类趴下」动画。
		Identifier animId = SSC_ADDON_NORMAL_TO_FERAL_ANIM;
		if (isFromFeralForm(player)) {
			animId = SSC_ADDON_FERAL_REVERSE_ANIM;
		}
		cir.setReturnValue(new AnimationHolder(animId, true));
	}

	/**
	 * 判断玩家变身<b>前</b>的形态是否为四足。from 信息缺失（null）时保守返回 false
	 * （回退原版「人类趴下」动画，与旧行为一致）。
	 */
	private static boolean isFromFeralForm(PlayerEntity player) {
		String fromFormName = ShapeShifterCurseFabricClient.getClientTransformFromForm(player.getUuid());
		if (fromFormName == null) {
			return false;
		}
		IForm fromForm;
		try {
			fromForm = RegPlayerForms.getPlayerForm(fromFormName);
		} catch (IllegalArgumentException e) {
			return false; // 形态名解析失败，按非四足处理
		}
		if (fromForm == null || fromForm.getFormID() == null) {
			return false;
		}
		return fromForm.getBodyType() == PlayerFormBodyType.FERAL
				|| "familiar_fox_mancianima".equals(fromForm.getFormID().getPath());
	}
}
