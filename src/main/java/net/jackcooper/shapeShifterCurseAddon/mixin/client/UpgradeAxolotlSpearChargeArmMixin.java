package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.jackcooper.shapeShifterCurseAddon.client.UpgradeAxolotlSpearRenderState;

/**
 * 进化美西螈「投掷水矛」蓄力期：同步形态手臂与手持物挂点的举矛姿势。
 *
 * <p>形态模型使用 GeoBone，HeldItemFeatureRenderer 则使用原版 ModelPart 挂载物品。
 * 两者必须在 SSC 动画处理后使用同一组旋转；仅覆盖 GeoBone 会让水矛留在身侧。
 * 持矛手由玩家的主手决定，另一只手向前伸。</p>
 *
 * <p>仅对 {@code isCharging} 为真的玩家生效，非蓄力玩家零影响。多人对其它蓄力玩家同样生效
 * （蓄力状态已广播）。</p>
 */
@Mixin(DefaultModelAnimationSystem.class)
public class UpgradeAxolotlSpearChargeArmMixin {

    // 保留现有举矛动作，以右利手为基准（弧度）；左利手镜像 yaw。
    private static final float THROW_SPEAR_RIGHT_PITCH = -3.0F;
    private static final float THROW_SPEAR_RIGHT_YAW = -0.875F;
    // 左臂改为「向前伸」（托住矛杆 / 指向前方），而非双手都举过肩
    private static final float LEFT_ARM_FORWARD_PITCH = -1.4F;   // 手臂近乎水平指向前方
    private static final float LEFT_ARM_FORWARD_YAW = -0.3F;     // 略内收

    @Inject(method = "processAnimation", at = @At("TAIL"), require = 0)
    private void ssc_addon$raiseSpearChargeArm(FormRenderer formRenderer, FormModel model,
            PlayerEntityRenderer renderer, PlayerEntity player, float limbAngle, float limbDistance,
            float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (player == null || !UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
            return;
        }
        boolean rightHanded = player.getMainArm() == Arm.RIGHT;
        float rightPitch = rightHanded ? THROW_SPEAR_RIGHT_PITCH : LEFT_ARM_FORWARD_PITCH;
        float rightYaw = rightHanded ? THROW_SPEAR_RIGHT_YAW : -LEFT_ARM_FORWARD_YAW;
        float leftPitch = rightHanded ? LEFT_ARM_FORWARD_PITCH : THROW_SPEAR_RIGHT_PITCH;
        float leftYaw = rightHanded ? LEFT_ARM_FORWARD_YAW : -THROW_SPEAR_RIGHT_YAW;

        // 先同步物品实际使用的原版手臂，清除行走/待机动画残留的 roll。
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

}
