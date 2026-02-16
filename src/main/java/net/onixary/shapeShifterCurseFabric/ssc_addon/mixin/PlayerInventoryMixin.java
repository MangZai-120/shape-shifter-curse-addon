package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow public PlayerEntity player;
    
    @Shadow public abstract ItemStack getStack(int slot);
    @Shadow public abstract void setStack(int slot, ItemStack stack);
    @Shadow public abstract boolean insertStack(ItemStack stack);

    /**
     * Prevents removing potion bag from slot 8 if player is Red form
     */
    @Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void preventPotionBagRemoval(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 8) {
            ItemStack stack = this.getStack(8);
            if (stack.isOf(SscAddon.POTION_BAG)) {
                PlayerFormBase currentForm = FormAbilityManager.getForm(player);
                boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
                if (isRedForm) {
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * Prevents setting potion bag to any slot other than slot 8
     * Also ensures if potion bag is somehow placed elsewhere, it gets moved back to slot 8
     */
    @Inject(method = "setStack(ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void preventPotionBagMisplacement(int slot, ItemStack stack, CallbackInfo ci) {
        if (stack.isOf(SscAddon.POTION_BAG) && slot != 8) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
            
            if (isRedForm) {
                // Cancel this operation
                ci.cancel();
                
                // If slot 8 is empty or not a potion bag, move it there
                ItemStack slot8Stack = this.getStack(8);
                if (!slot8Stack.isOf(SscAddon.POTION_BAG)) {
                    // Move whatever is in slot 8 to the intended slot
                    if (!slot8Stack.isEmpty()) {
                        this.setStack(slot, slot8Stack);
                    }
                    // Place potion bag in slot 8
                    this.setStack(8, stack);
                }
            }
        }
    }

    /**
     * Prevents inserting potion bag into inventory if it's not for Red form
     */
    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void preventPotionBagInsert(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(SscAddon.POTION_BAG)) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
            
            // If not Red form, prevent insertion
            if (!isRedForm) {
                cir.setReturnValue(false);
            }
            // If Red form and slot is not 8, redirect to slot 8
            else if (slot != 8 && slot != -1) {
                cir.setReturnValue(false);
            }
        }
    }
}
