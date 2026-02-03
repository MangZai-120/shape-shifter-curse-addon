package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.SnowballLauncherItem;

public class ReloadSnowballLauncherRecipe extends SpecialCraftingRecipe {

    public ReloadSnowballLauncherRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        boolean hasLauncher = false;
        boolean hasAmmo = false;
        
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == SscAddon.SNOWBALL_LAUNCHER) {
                    if (hasLauncher) return false; // Only 1 launcher
                    hasLauncher = true;
                } else if (stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.SNOW_BLOCK || stack.getItem() == Items.SNOW) {
                    hasAmmo = true;
                } else {
                    return false; // Invalid item
                }
            }
        }
        return hasLauncher && hasAmmo;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack launcher = ItemStack.EMPTY;
        int ammoToAdd = 0;
        
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == SscAddon.SNOWBALL_LAUNCHER) {
                    launcher = stack.copy();
                } else if (stack.getItem() == Items.SNOWBALL) {
                    ammoToAdd += 1;
                } else if (stack.getItem() == Items.SNOW_BLOCK) {
                    ammoToAdd += 8; // Increased from 4 to 8 for faster reload
                } else if (stack.getItem() == Items.SNOW) {
                    ammoToAdd += 1; // Snow Layer
                }
            }
        }
        
        if (!launcher.isEmpty()) {
            int currentAmmo = SnowballLauncherItem.getAmmo(launcher);
            SnowballLauncherItem.setAmmo(launcher, currentAmmo + ammoToAdd);
            return launcher;
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SscAddon.RELOAD_SNOWBALL_LAUNCHER_SERIALIZER;
    }
}
