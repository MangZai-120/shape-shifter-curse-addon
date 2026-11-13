package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.OptionalAccessoryBridges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="io.wispforest.accessories.impl.AccessoriesHolderImpl",remap=false)
public class AccessoriesHolderMutationMixin {
    @Inject(method={"read","init"},at=@At("RETURN"))
    private void ssca$changed(CallbackInfo ci) {OptionalAccessoryBridges.inventoryChanged(this);}
}
