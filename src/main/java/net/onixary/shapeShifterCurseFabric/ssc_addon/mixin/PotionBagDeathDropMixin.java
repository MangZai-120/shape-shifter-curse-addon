package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.GameRules;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PotionBagDeathDropMixin {

    @Shadow public abstract PlayerInventory getInventory();

    /**
     * Drop potion bag items on death if keepInventory is disabled
     */
    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void dropPotionBagItems(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Check if keepInventory is enabled
        boolean keepInventory = player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
        
        // If keepInventory is enabled, don't drop anything
        if (keepInventory) {
            return;
        }
        
        // Find potion bag in inventory
        ItemStack potionBag = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(SscAddon.POTION_BAG)) {
                potionBag = stack;
                break;
            }
        }
        
        // Drop all items from the potion bag
        if (!potionBag.isEmpty() && potionBag.hasNbt()) {
            NbtCompound nbt = potionBag.getNbt();
            if (nbt != null && nbt.contains("Items", 9)) {
                NbtList list = nbt.getList("Items", 10);
                for (int i = 0; i < list.size(); ++i) {
                    NbtCompound itemTag = list.getCompound(i);
                    ItemStack stack = ItemStack.fromNbt(itemTag);
                    if (!stack.isEmpty()) {
                        player.dropItem(stack, true, false);
                    }
                }
                // Clear the potion bag's items
                nbt.remove("Items");
            }
        }
    }
}
