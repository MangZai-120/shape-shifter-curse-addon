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
     * Helper to check if an item is a locked form-exclusive item in a specific slot
     */
    private boolean isLockedAllayItem(int slot, ItemStack stack) {
        PlayerFormBase currentForm = FormAbilityManager.getForm(player);
        boolean isAllaySp = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "allay_sp"));
        if (!isAllaySp) return false;
        
        if (slot == 0 && stack.isOf(SscAddon.ALLAY_HEAL_WAND)) return true;
        if (slot == 1 && stack.isOf(SscAddon.ALLAY_JUKEBOX)) return true;
        return false;
    }

    /**
     * Prevents removing potion bag from slot 8 if player is Red form
     * Prevents removing allay items from slots 0/1 if player is Allay SP form
     */
    @Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void preventLockedItemRemoval(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = this.getStack(slot);

        // Red form: lock potion bag in slot 8
        if (slot == 8 && stack.isOf(SscAddon.POTION_BAG)) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
            if (isRedForm) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }

        // Allay SP form: lock heal wand in slot 0, jukebox in slot 1
        if (isLockedAllayItem(slot, stack)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    /**
     * Prevents setting locked items to wrong slots
     */
    @Inject(method = "setStack(ILnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void preventLockedItemMisplacement(int slot, ItemStack stack, CallbackInfo ci) {
        // Potion Bag logic (existing)
        if (stack.isOf(SscAddon.POTION_BAG) && slot != 8) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
            if (isRedForm) {
                ci.cancel();
                ItemStack slot8Stack = this.getStack(8);
                if (!slot8Stack.isOf(SscAddon.POTION_BAG)) {
                    if (!slot8Stack.isEmpty()) {
                        this.setStack(slot, slot8Stack);
                    }
                    this.setStack(8, stack);
                }
                return;
            }
        }

        // Allay Heal Wand: must stay in slot 0
        if (stack.isOf(SscAddon.ALLAY_HEAL_WAND) && slot != 0) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isAllaySp = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "allay_sp"));
            if (isAllaySp) {
                ci.cancel();
                return;
            }
        }

        // Allay Jukebox: must stay in slot 1
        if (stack.isOf(SscAddon.ALLAY_JUKEBOX) && slot != 1) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isAllaySp = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "allay_sp"));
            if (isAllaySp) {
                ci.cancel();
                return;
            }
        }
    }

    /**
     * Prevents inserting locked items outside their designated slots
     */
    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void preventLockedItemInsert(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(SscAddon.POTION_BAG)) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));
            if (!isRedForm) {
                cir.setReturnValue(false);
            } else if (slot != 8 && slot != -1) {
                cir.setReturnValue(false);
            }
        }

        if (stack.isOf(SscAddon.ALLAY_HEAL_WAND) || stack.isOf(SscAddon.ALLAY_JUKEBOX)) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            boolean isAllaySp = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "allay_sp"));
            if (!isAllaySp) {
                cir.setReturnValue(false);
            }
        }
    }
}
