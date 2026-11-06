package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class PortableBeaconGlintMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void ssca$activeBeaconGlint(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (AllaySPPortableBeacon.isActiveStack(stack)
                || net.jackcooper.shapeShifterCurseAddon.ability.AllaySPTotem.isMarkedActiveTotem(stack)) cir.setReturnValue(true);
    }
}
