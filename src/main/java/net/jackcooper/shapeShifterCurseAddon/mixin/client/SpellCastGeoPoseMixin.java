package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 施法举手动画（GeckoLib 兽形模型链路，jackcooper）：对「bodyType=NORMAL 但带 GeckoLib 兽形
 * 渲染」的形态（如美西螈 SP / 进化美西螈），biped 手臂被隐藏且角度被 GeckoLib 动画覆盖，
 * {@code SpellCastPoseMixin}（BipedEntityModel）无效——须在
 * {@code DefaultModelAnimationSystem.processAnimation} TAIL 直接覆盖 GeoBone 旋转。
 * <p>
 * 与 {@code UpgradeAxolotlSpearChargeArmMixin}（投掷水矛蓄力举臂）同链路同先例：
 * 该 mixin 证实「playerModel.rightArm 角度 → 兽形 bipedRightArm GeoBone」对 FERAL 兽形实测不生效，
 * 直接覆盖 GeoBone 才有效。此处用同样的骨骼名（bipedRightArm / bipedLeftArm）。
 * <p>
 * FERAL 兽形不举手（用户要求）：按 bodyType 排除。
 */
@Mixin(DefaultModelAnimationSystem.class)
public abstract class SpellCastGeoPoseMixin {

	/** 举手目标角（弧度）：双臂向前高举，与 SpellCastPoseMixin 的 biped 版一致。 */
	private static final float CAST_ARM_PITCH = -2.6F;

	@Inject(method = "processAnimation", at = @At("TAIL"), require = 0)
	private void ssca$raiseGeoArmsWhileCasting(FormRenderer formRenderer, FormModel model,
			PlayerEntityRenderer renderer, PlayerEntity player, float limbAngle, float limbDistance,
			float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		if (player == null || player.getWorld() == null || !player.getWorld().isClient) return;
		if (!CastingVisualState.isCasting(player.getUuid())) return;
		// FERAL 排除（用户要求兽形不举手）；非玩家/组件异常安全降级为不举手
		PlayerFormComponent component;
		try {
			component = RegPlayerFormComponent.PLAYER_FORM.get(player);
		} catch (Exception exception) {
			return;
		}
		if (component == null || component.nowForm == null
				|| component.nowForm.getBodyType() != PlayerFormBodyType.NORMAL) return;
		float progress = CastingVisualState.raiseProgress(player.getUuid());
		if (progress <= 0.0F) return;
		// 直接覆盖 GeoBone 旋转（biped 双臂骨骼，与 UpgradeAxolotlSpearChargeArmMixin 同名）
		var rightArm = model.getCachedGeoBone("bipedRightArm");
		if (rightArm != null) {
			rightArm.setRotX(MathHelper.lerp(progress, rightArm.getRotX(),
					CAST_ARM_PITCH + CastingVisualState.armSway(player.age + tickDelta, true) * 0.12F));
			rightArm.setRotY(MathHelper.lerp(progress, rightArm.getRotY(), 0.0F));
		}
		var leftArm = model.getCachedGeoBone("bipedLeftArm");
		if (leftArm != null) {
			leftArm.setRotX(MathHelper.lerp(progress, leftArm.getRotX(),
					CAST_ARM_PITCH + CastingVisualState.armSway(player.age + tickDelta, false) * 0.12F));
			leftArm.setRotY(MathHelper.lerp(progress, leftArm.getRotY(), 0.0F));
		}
	}
}
