package net.jackcooper.shapeShifterCurseAddon.mixin;

import java.util.function.Consumer;
import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拒绝完整变身请求，尤其防止 forceTransform 提前清空历史。 */
@Mixin(value = TransformManager.class, remap = false)
public abstract class AnchoredTransformMixin {
    @Inject(method = "startTransform", at = @At("HEAD"), cancellable = true)
    private static void ssca$rejectStart(PlayerEntity player, IForm form,
            Consumer<TransformManager.PlayerTransformData> complete, CallbackInfoReturnable<Boolean> cir) {
        if (InstinctAnchors.blocksChange(player, form)) cir.setReturnValue(false);
    }

    @Inject(method = "immediatelyTransform", at = @At("HEAD"), cancellable = true)
    private static void ssca$rejectImmediate(PlayerEntity player, IForm form, CallbackInfoReturnable<Boolean> cir) {
        if (InstinctAnchors.blocksChange(player, form)) cir.setReturnValue(false);
    }

    @Inject(method = "forceTransform", at = @At("HEAD"), cancellable = true)
    private static void ssca$rejectForced(PlayerEntity player, IForm form, boolean immediate, CallbackInfoReturnable<Boolean> cir) {
        if (InstinctAnchors.blocksChange(player, form)) cir.setReturnValue(false);
    }
}
