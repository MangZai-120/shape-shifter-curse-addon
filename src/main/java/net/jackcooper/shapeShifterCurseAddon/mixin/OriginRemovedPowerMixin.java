package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.jackcooper.shapeShifterCurseAddon.compat.OriginPowerReloadGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.integration.origins.component.PlayerOriginComponent;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerOriginComponent.class, remap = false)
public abstract class OriginRemovedPowerMixin {
    @Shadow private PlayerEntity player;

    @Inject(method = "revokeRemovedPowers", at = @At("HEAD"), cancellable = true)
    private void ssca$deferSameFormCleanup(Origin origin, PowerHolderComponent powers, CallbackInfo ci) {
        if (OriginPowerReloadGuard.deferCleanup(player, origin, powers)) ci.cancel();
    }
}
