package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保护直接调用组件 setter 的接入方；NBT 恢复和原版同步仍正常执行。 */
@Mixin(value = PlayerFormComponent.class, remap = false)
public abstract class AnchoredFormComponentMixin {
    @Shadow public PlayerEntity player;

    @Inject(target = @Desc(value = "setForm", args = IForm.class), at = @At("HEAD"), cancellable = true)
    private void ssca$keepOwnedForm(IForm form, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InstinctAnchors.locked(player)) ci.cancel();
    }

    @Inject(target = @Desc(value = "setForm", args = Identifier.class), at = @At("HEAD"), cancellable = true)
    private void ssca$keepOwnedIdentifier(Identifier form, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InstinctAnchors.locked(player)) ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void ssca$keepOwnedState(CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InstinctAnchors.locked(player)) ci.cancel();
    }
}
