package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(ItemStack.class)
public abstract class PortableBeaconGlintMixin {
    @ModifyReturnValue(method = "hasGlint", at = @At("RETURN"))
    private boolean ssca$activeBeaconGlint(boolean original) {
        ItemStack stack = (ItemStack) (Object) this;
        return original || AllaySPPortableBeacon.isActiveStack(stack)
                || net.jackcooper.shapeShifterCurseAddon.ability.AllaySPTotem.isMarkedActiveTotem(stack);
    }
}
