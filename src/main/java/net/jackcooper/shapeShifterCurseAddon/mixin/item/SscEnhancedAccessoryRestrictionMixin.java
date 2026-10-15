package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SSC 的增强饰品按附属的形态限制校验；变形后的卸下由服务端统一守卫处理。 */
@Mixin(AccessoryItem.class)
public abstract class SscEnhancedAccessoryRestrictionMixin {
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$checkEnhancedAccessory(ItemStack stack, LivingEntity entity,
                                           AccessoryItem.SlotData slotData,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!AddonAccessoryGuard.canEquipMappedAccessory(entity, (AccessoryItem) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
