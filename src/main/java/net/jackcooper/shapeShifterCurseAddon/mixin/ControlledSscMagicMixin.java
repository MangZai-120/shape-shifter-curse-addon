package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.magic.MagicUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MagicUtils.class)
public abstract class ControlledSscMagicMixin {
    // SSC 1.10.0 release routes toolbar keys directly through PressKeyMagic/Apoli;
    // later 1.10.0 builds also expose these dispatch methods.
    @Inject(method = {"invokeNowMagic_CLIENT", "invokeNowMagic_SERVER"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ssca$blockControlledMagic(PlayerEntity player, boolean continuous, CallbackInfo ci) {
        if (AbilityUseGuard.blocked(player)) ci.cancel();
    }
}
