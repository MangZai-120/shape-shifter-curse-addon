package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.compat.CarryOnCompat;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

/** Loaded by SscAddonMixinConfigPlugin only on clients with Carry On installed. */
@Mixin(value = CarryOnCompat.class, remap = false)
public abstract class CarryOnAnimationMixin {
    @Inject(method = "isInCarryingAnimation", at = @At("HEAD"), cancellable = true)
    private static void ssca$carryingAnimation(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        cir.setReturnValue(carry != null && carry.isCarrying()
                && !player.isSwimming() && !player.isFallFlying());
    }
}
