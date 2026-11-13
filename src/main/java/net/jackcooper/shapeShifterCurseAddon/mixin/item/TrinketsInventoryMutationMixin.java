package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import dev.emi.trinkets.api.TrinketInventory;
import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.item.ItemStack;

@Mixin(TrinketInventory.class)
public class TrinketsInventoryMutationMixin {
    @Inject(method={"setStack","clear","markDirty"},at=@At("RETURN"))
    private void ssca$written(CallbackInfo ci) {ssca$changed();}
    @Inject(method="removeStack",at=@At("RETURN"))
    private void ssca$removed(CallbackInfoReturnable<ItemStack> cir) {ssca$changed();}
    @Inject(method="markUpdate",at=@At("RETURN"),remap=false)
    private void ssca$resized(CallbackInfo ci) {ssca$changed();}
    private void ssca$changed() {
        DualAccessoryEquipment.changed(((TrinketInventory)(Object)this).getComponent().getEntity());
    }
}
