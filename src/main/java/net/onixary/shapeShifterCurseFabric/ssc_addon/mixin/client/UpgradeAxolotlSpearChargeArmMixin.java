package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;

/**
 * 进化美西螈「投掷水矛」蓄力期：直接把 FERAL 兽形的右臂骨骼设为「三叉戟蓄力」举矛过肩角度。
 *
 * <p><b>背景</b>：FERAL 兽形手臂由 {@link DefaultModelAnimationSystem#processAnimation} 用
 * {@code playerModel.rightArm} 旋转驱动骨骼。理论上 {@code UpgradeAxolotlSpearArmPoseMixin} 把 armPose
 * 设为 {@code THROW_SPEAR} 后，{@code playerModel.rightArm} 会自动变成举矛角度。但对 SSC 的 FERAL
 * 兽形该链路实测未生效（手臂垂直不动）。故在此 TAIL 注入，蓄力时<b>直接覆盖</b>右臂骨骼旋转，
 * 复刻 vanilla {@code BipedEntityModel.setAngles} 中 {@code THROW_SPEAR} 分支的右臂角度
 * （pitch=-3.0, yaw=-0.875，弧度）。</p>
 *
 * <p>仅对 {@code isCharging} 为真的玩家生效，非蓄力玩家零影响。多人对其它蓄力玩家同样生效
 * （蓄力状态已广播）。</p>
 */
@Mixin(DefaultModelAnimationSystem.class)
public class UpgradeAxolotlSpearChargeArmMixin {

    // vanilla BipedEntityModel.setAngles 中 THROW_SPEAR 的右臂角度（弧度）
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
        // 蓄力中：覆盖右臂（持矛手）骨骼旋转为三叉戟蓄力角度（举过肩）
        // 用 var 让编译器推断类型，避免编辑器在 GeckoLib/AzureLib 同名 GeoBone 间的类型歧义误报
        var rightArm = model.getCachedGeoBone("bipedRightArm");
        if (rightArm != null) {
            rightArm.setRotX(THROW_SPEAR_RIGHT_PITCH);
            rightArm.setRotY(THROW_SPEAR_RIGHT_YAW);
        }
        // 左臂向前伸（托住矛杆 / 指向前方），不举过肩
        var leftArm = model.getCachedGeoBone("bipedLeftArm");
        if (leftArm != null) {
            leftArm.setRotX(LEFT_ARM_FORWARD_PITCH);
            leftArm.setRotY(LEFT_ARM_FORWARD_YAW);
        }
    }

    /**
     * 修复多人下「其它玩家」兽形(FERAL)尾巴被击后持续下翘。（原 TailVerticalDragFixMixin 合并至此；同为 DefaultModelAnimationSystem 目标，行为不变。）
     * 把垂直速度来源从「不衷减的 velocity」改为「每帧实际垂直位移」getY()-prevY，对本地/远程玩家一致。
     */
    @WrapOperation(method = "finishRender",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getVelocity()Lnet/minecraft/util/math/Vec3d;"),
            require = 0)
    private Vec3d ssc_addon$fixRemoteTailVerticalDrag(PlayerEntity player, Operation<Vec3d> original) {
        Vec3d velocity = original.call(player);
        double realVerticalDelta = player.getY() - player.prevY;
        return new Vec3d(velocity.x, realVerticalDelta, velocity.z);
    }
}
