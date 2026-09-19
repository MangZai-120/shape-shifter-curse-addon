package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 施法举手动画（jackcooper）：施法时双手前举（铁魔法式）。
 * <p>
 * 注入 {@code BipedEntityModel.setAngles} TAIL——原版 positionArms/animateArms/sneaking 分支
 * 全部执行完毕后再覆盖双臂 pitch，原版姿势与本 mixin 的关系是「后者最终生效」。
 * <p>
 * <b>两条渲染链路（按 bodyType + 是否有 GeckoLib 兽形渲染分流）：</b>
 * <ul>
 * <li><b>纯人形态（无兽形模型，如原版皮肤人形态）</b>：直接覆盖 biped 手臂角度（本 mixin 生效）；</li>
 * <li><b>带 GeckoLib 兽形模型的人形形态（如美西螈，bodyType=NORMAL 但有兽形渲染）</b>：biped 手臂
 * 被隐藏且角度被 GeckoLib 动画覆盖，本 mixin 无效——由配套的
 * {@code SpellCastGeoPoseMixin}（DefaultModelAnimationSystem.processAnimation TAIL）直接覆盖
 * GeoBone 旋转（与 {@code UpgradeAxolotlSpearChargeArmMixin} 同链路同先例）；</li>
 * <li><b>FERAL 兽形</b>：不举手（用户要求）。</li>
 * </ul>
 * <p>
 * 多人一致：数据源为服务端广播的 {@code spell_cast_visual}，所有观察者客户端同表同动画。
 */
@Mixin(BipedEntityModel.class)
public abstract class SpellCastPoseMixin {

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void sscAddon$raiseArmsWhileCasting(LivingEntity entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		if (entity == null || entity.getWorld() == null || !entity.getWorld().isClient) return;
		if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) return; // 人形怪物无形态组件
		if (!CastingVisualState.isCasting(entity.getUuid())) return;
		// 仅人形态（FERAL 兽形有自己的动画系统，biped 手臂角度会被复制进兽形骨骼，不能覆盖）
		PlayerFormComponent component;
		try {
			component = RegPlayerFormComponent.PLAYER_FORM.get(entity);
		} catch (Exception exception) {
			return; // 组件未就绪（理论上玩家必有；防御式降级 = 不举手）
		}
		if (component == null || component.nowForm == null
				|| component.nowForm.getBodyType() != PlayerFormBodyType.NORMAL) return;
		BipedEntityModel<?> self = (BipedEntityModel<?>) (Object) this;
		float progress = CastingVisualState.raiseProgress(entity.getUuid());
		if (progress <= 0.0F) return;
		// 铁魔法式：双臂向前上方举起（pitch 负值 = 向前抬）。从当前角度渐进插值到目标角。
		float targetPitch = -2.6F; // ≈149°，双臂高举过头偏前
		self.rightArm.pitch = net.minecraft.util.math.MathHelper.lerp(progress, self.rightArm.pitch,
				targetPitch + CastingVisualState.armSway(animationProgress, true) * 0.12F);
		self.rightArm.yaw = net.minecraft.util.math.MathHelper.lerp(progress, self.rightArm.yaw, 0.0F);
		self.leftArm.pitch = net.minecraft.util.math.MathHelper.lerp(progress, self.leftArm.pitch,
				targetPitch + CastingVisualState.armSway(animationProgress, false) * 0.12F);
		self.leftArm.yaw = net.minecraft.util.math.MathHelper.lerp(progress, self.leftArm.yaw, 0.0F);
	}
}
