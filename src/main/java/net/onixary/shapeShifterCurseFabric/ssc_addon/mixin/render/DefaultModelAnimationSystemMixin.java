package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.render.OutlinePassTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * 修复：DefaultModelAnimationSystem 的 tail-drag 状态字段在多玩家间被
 * FormRenderer 单例共享，导致同形态玩家相对运动时尾巴动画状态互相污染
 * （表现为他人视角下尾巴向下/抽搐）。
 * 方案：在每次 beforeRender 时按 PlayerEntity 把状态切入实例字段，
 *      afterRender 末尾再回写到 map，实现按玩家隔离。
 */
@Mixin(value = DefaultModelAnimationSystem.class, remap = false)
public abstract class DefaultModelAnimationSystemMixin {
    @Shadow(remap = false) private float tailDragAmount;
    @Shadow(remap = false) private float tailDragAmountO;
    @Shadow(remap = false) private float currentTailDragAmount;
    @Shadow(remap = false) private float tailDragAmountVertical;
    @Shadow(remap = false) private float tailDragAmountVerticalO;
    @Shadow(remap = false) private float currentTailDragAmountVertical;

    @Unique
    private final WeakHashMap<PlayerEntity, float[]> ssc_addon$tailStateByPlayer = new WeakHashMap<>();

    @Inject(method = "beforeRender", at = @At("HEAD"))
    private void ssc_addon$loadTailState(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
                                         PlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                                         float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        float[] s = ssc_addon$tailStateByPlayer.computeIfAbsent(player, p -> new float[6]);
        this.tailDragAmount = s[0];
        this.tailDragAmountO = s[1];
        this.currentTailDragAmount = s[2];
        this.tailDragAmountVertical = s[3];
        this.tailDragAmountVerticalO = s[4];
        this.currentTailDragAmountVertical = s[5];
    }

    @Inject(method = "afterRender", at = @At("TAIL"))
    private void ssc_addon$saveTailState(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
                                         PlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                                         float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        // outline pass 不写回，避免同一帧 normal+outline 两 pass 重复累计 tailDrag
        if (OutlinePassTracker.isInOutlinePass()) {
            return;
        }
        float[] s = ssc_addon$tailStateByPlayer.computeIfAbsent(player, p -> new float[6]);
        s[0] = this.tailDragAmount;
        s[1] = this.tailDragAmountO;
        s[2] = this.currentTailDragAmount;
        s[3] = this.tailDragAmountVertical;
        s[4] = this.tailDragAmountVerticalO;
        s[5] = this.currentTailDragAmountVertical;
    }
}
