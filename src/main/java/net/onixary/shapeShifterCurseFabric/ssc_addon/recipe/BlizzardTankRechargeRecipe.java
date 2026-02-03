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
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableFridgeItem;

public class BlizzardTankRechargeRecipe extends SpecialCraftingRecipe {

    public BlizzardTankRechargeRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        ItemStack tankStack = ItemStack.EMPTY;
        boolean hasSnowBlock = false;
        
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == SscAddon.PORTABLE_FRIDGE) {
                    if (!tankStack.isEmpty()) return false; // Only 1 tank allowed
                    tankStack = stack;
                } else if (stack.getItem() == Items.SNOW_BLOCK) {
                    hasSnowBlock = true;
                } else {
                    return false; // No other items allowed
                }
            }
        }
        
        if (!tankStack.isEmpty() && hasSnowBlock) {
            return PortableFridgeItem.getCharge(tankStack) < PortableFridgeItem.MAX_CHARGE;
        }

        return false;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack tank = ItemStack.EMPTY;
        int chargeToAdd = 0;
        
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() == SscAddon.PORTABLE_FRIDGE) {
                    tank = stack.copy();
                } else if (stack.getItem() == Items.SNOW_BLOCK) {
                    chargeToAdd += 8;
                }
            }
        }
        
        if (!tank.isEmpty() && chargeToAdd > 0) {
            int currentCharge = PortableFridgeItem.getCharge(tank);
            PortableFridgeItem.setCharge(tank, currentCharge + chargeToAdd); // setCharge handles current + add, and internal min/max logic
            return tank;
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SscAddon.BLIZZARD_TANK_RECHARGE_SERIALIZER;
    }
}
