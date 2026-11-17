package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.jackcooper.shapeShifterCurseAddon.client.UpgradeAxolotlSpearRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;

/** Existing spear and casting poses, with one explicit application order. */
public final class FormPoseOverrides {
    private FormPoseOverrides() {}


    private static final float THROW_SPEAR_RIGHT_PITCH = -(float) Math.PI;
    private static final float THROW_SPEAR_RIGHT_YAW = 0.0F;

    private static final float LEFT_ARM_FORWARD_PITCH = -1.4F;   // 手臂近乎水平指向前方
    private static final float LEFT_ARM_FORWARD_YAW = -0.3F;     // 略内收

    public static void spear(FormRenderer formRenderer, FormModel model,
            PlayerEntityRenderer renderer, PlayerEntity player, float limbAngle, float limbDistance,
            float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (player == null || !UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
            return;
        }
        boolean rightHanded = player.getMainArm() == Arm.RIGHT;
        float rightPitch = rightHanded ? THROW_SPEAR_RIGHT_PITCH : LEFT_ARM_FORWARD_PITCH;
        float rightYaw = rightHanded ? THROW_SPEAR_RIGHT_YAW : -LEFT_ARM_FORWARD_YAW;
        float leftPitch = rightHanded ? LEFT_ARM_FORWARD_PITCH : THROW_SPEAR_RIGHT_PITCH;
        float leftYaw = rightHanded ? LEFT_ARM_FORWARD_YAW : -THROW_SPEAR_RIGHT_YAW;

        var vanillaModel = renderer.getModel();
        vanillaModel.rightArm.pitch = rightPitch;
        vanillaModel.rightArm.yaw = rightYaw;
        vanillaModel.rightArm.roll = 0.0F;
        vanillaModel.leftArm.pitch = leftPitch;
        vanillaModel.leftArm.yaw = leftYaw;
        vanillaModel.leftArm.roll = 0.0F;

        var rightArm = model.getCachedGeoBone("bipedRightArm");
        if (rightArm != null) {
            rightArm.setRotX(rightPitch);
            rightArm.setRotY(rightYaw);
            rightArm.setRotZ(0.0F);
        }
        var leftArm = model.getCachedGeoBone("bipedLeftArm");
        if (leftArm != null) {
            leftArm.setRotX(leftPitch);
            leftArm.setRotY(leftYaw);
            leftArm.setRotZ(0.0F);
        }
    }

	
	private static final float CAST_ARM_PITCH = -2.6F;

	public static void casting(FormRenderer formRenderer, FormModel model,
			PlayerEntityRenderer renderer, PlayerEntity player, float limbAngle, float limbDistance,
			float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player == null || player.getWorld() == null || !player.getWorld().isClient) return;
		if (!CastingVisualState.isCasting(player.getUuid())) return;

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




		var vanillaModel = renderer.getModel();
		float swayR = CastingVisualState.armSway(player.age + tickDelta, true) * 0.12F;
		float swayL = CastingVisualState.armSway(player.age + tickDelta, false) * 0.12F;
		vanillaModel.rightArm.pitch = MathHelper.lerp(progress, vanillaModel.rightArm.pitch, CAST_ARM_PITCH + swayR);
		vanillaModel.rightArm.yaw = MathHelper.lerp(progress, vanillaModel.rightArm.yaw, 0.0F);
		vanillaModel.leftArm.pitch = MathHelper.lerp(progress, vanillaModel.leftArm.pitch, CAST_ARM_PITCH + swayL);
		vanillaModel.leftArm.yaw = MathHelper.lerp(progress, vanillaModel.leftArm.yaw, 0.0F);
	}
}
