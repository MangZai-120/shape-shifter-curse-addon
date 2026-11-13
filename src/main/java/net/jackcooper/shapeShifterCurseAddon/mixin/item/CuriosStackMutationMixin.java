package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.OptionalAccessoryBridges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only observed functional Curios handlers have an owner; other Forge inventories are ignored. */
@Pseudo
@Mixin(targets="net.minecraftforge.items.ItemStackHandler",remap=false)
public class CuriosStackMutationMixin {
    @Inject(method={"onContentsChanged","onLoad","setSize"},at=@At("RETURN"))
    private void ssca$changed(CallbackInfo ci) {OptionalAccessoryBridges.inventoryChanged(this);}
}
