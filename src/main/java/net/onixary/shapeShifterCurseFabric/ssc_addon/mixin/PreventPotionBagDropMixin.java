package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PreventPotionBagDropMixin {

    /**
     * Prevents dropping the potion bag using Q key or any other drop method
     */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void preventPotionBagDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<?> cir) {
        if (stack.isOf(SscAddon.POTION_BAG)) {
            cir.setReturnValue(null);
        }
    }
}
