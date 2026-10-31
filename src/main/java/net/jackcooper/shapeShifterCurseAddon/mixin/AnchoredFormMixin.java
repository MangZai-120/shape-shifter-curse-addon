package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拦在能力、缩放、历史和事件变更之前；允许重登时重新应用同一个形态。 */
@Mixin(value = FormUtils.class, remap = false)
public abstract class AnchoredFormMixin {
    @Inject(method = {"_setForm", "setForm"}, at = @At("HEAD"), cancellable = true, require = 2)
    private static void ssca$keepForm(PlayerEntity player, IForm form, CallbackInfo ci) {
        if (InstinctAnchors.blocksChange(player, form)) ci.cancel();
    }

    @Inject(method = "_loadForm", at = @At("HEAD"), cancellable = true)
    private static void ssca$keepLoadedForm(PlayerEntity player, IForm form, boolean reload, CallbackInfo ci) {
        if (InstinctAnchors.blocksChange(player, form)) ci.cancel();
    }

    @Inject(method = "isFormCanUse", at = @At("HEAD"), cancellable = true)
    private static void ssca$rejectOtherForm(PlayerEntity player, IForm form, CallbackInfoReturnable<Boolean> cir) {
        if (InstinctAnchors.blocksChange(player, form)) cir.setReturnValue(false);
    }
}
