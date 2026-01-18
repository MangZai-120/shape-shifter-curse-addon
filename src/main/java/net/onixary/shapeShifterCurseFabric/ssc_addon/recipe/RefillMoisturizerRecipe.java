package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

public class RefillMoisturizerRecipe extends SpecialCraftingRecipe {

    public RefillMoisturizerRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        boolean hasMoisturizer = false;
        boolean hasBucket = false;
        
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == SscAddon.PORTABLE_MOISTURIZER && !hasMoisturizer) {
                    hasMoisturizer = true;
                } else if (stack.getItem() == Items.AXOLOTL_BUCKET && !hasBucket) {
                    hasBucket = true;
                } else {
                    return false;
                }
            }
        }
        return hasMoisturizer && hasBucket;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack moisturizer = ItemStack.EMPTY;
        
        // Find input moisturizer to copy NBT if needed (though we reset charge anyway)
        // We might want to preserve "Active" state? Or reset it?
        // Let's create a fresh one with max charge.
        
        // Actually, we should probably output a copy of the input moisturizer but with full charge.
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == SscAddon.PORTABLE_MOISTURIZER) {
                moisturizer = stack.copy();
                break;
            }
        }
        
        if (!moisturizer.isEmpty()) {
            boolean wasActive = moisturizer.getOrCreateNbt().getBoolean("Active");
            moisturizer.setCount(1);
            
            // Set Charge to Max (600)
            moisturizer.getOrCreateNbt().putInt("Charge", 600);
            
            // Should we keep it active? Usually refilling allows it to continue working immediately.
            moisturizer.getOrCreateNbt().putBoolean("Active", wasActive);
            
            return moisturizer;
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SscAddon.REFILL_MOISTURIZER_SERIALIZER;
    }
}
