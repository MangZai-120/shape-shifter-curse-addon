package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.OptionalAccessoryBridges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="top.theillusivec4.curios.common.inventory.DynamicStackHandler",remap=false)
public class CuriosSizeMutationMixin {
    @Inject(method={"grow","shrink"},at=@At("RETURN"))
    private void ssca$changed(CallbackInfo ci) {OptionalAccessoryBridges.inventoryChanged(this);}
}
