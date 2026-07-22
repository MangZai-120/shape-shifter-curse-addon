package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 修复：多人下「其它玩家」的兽形（FERAL）尾巴在被攻击一次后持续下翘，直到该玩家移动/碰撞才恢复。
 *
 * <p><b>根因</b>：原版 {@link DefaultModelAnimationSystem#finishRender} 用
 * {@code player.getVelocity().y} 作为尾巴垂直拖拽（{@code tailDragAmountVertical} → 骨骼 setRotX）的输入。
 * 对「远程玩家」（非本地客户端模拟的玩家），客户端<b>不跑物理 tick 衰减其 velocity</b>，
 * 而服务端击退包会把 velocity.y 设为非零；该值之后一直卡住不归零，导致垂直拖拽恒为非零、尾巴持续下翘。
 * 直到玩家碰撞 / 移动产生新的速度更新包，velocity.y 才变化，尾巴恢复。
 * （水平拖拽用 {@code bodyYaw} 差值、正常同步，故只在垂直方向出问题。）</p>
 *
 * <p><b>修法</b>：把垂直速度来源从「不衰减的 velocity」改为「每帧实际垂直位移」
 * {@code getY() - prevY}。该位移对本地与远程玩家都基于真实位置变化：静止时为 0（尾巴回正），
 * 跳跃 / 下落时反映真实运动，行为与本地玩家一致。纯客户端、纯附属，原版零改动。</p>
 */
@Mixin(DefaultModelAnimationSystem.class)
public class TailVerticalDragFixMixin {

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
