package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper",remap=false)
public abstract class CuriosInventoryMutationMixin {
    @Shadow public abstract LivingEntity getWearer();
    @Inject(method={"setCurios","readTag","loadInventory","reset","growSlotType","shrinkSlotType"},at=@At("RETURN"))
    private void ssca$changed(CallbackInfo ci) {DualAccessoryEquipment.changed(getWearer());}
}
