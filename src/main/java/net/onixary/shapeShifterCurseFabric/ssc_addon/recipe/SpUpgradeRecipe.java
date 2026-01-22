package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import java.util.ArrayList;
import java.util.List;

public class SpUpgradeRecipe extends SpecialCraftingRecipe {

    public SpUpgradeRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        // Grid must be at least 3x3 for safety, though inventory usually is 3x3
        if (inventory.getWidth() < 3 || inventory.getHeight() < 3) return false;

        // Check Fixed Positions
        // 0 1 2
        // 3 4 5
        // 6 7 8
        
        // Corners: Gold Ingot
        if (!stackMatches(inventory.getStack(0), Items.GOLD_INGOT) ||
            !stackMatches(inventory.getStack(2), Items.GOLD_INGOT) ||
            !stackMatches(inventory.getStack(6), Items.GOLD_INGOT) ||
            !stackMatches(inventory.getStack(8), Items.GOLD_INGOT)) {
            return false;
        }

        // Center: Morphscale Core
        if (!stackMatches(inventory.getStack(4), RegCustomItem.MORPHSCALE_CORE)) {
            return false;
        }

        // Remaining Slots: 1, 3, 5, 7. Must contain exactly {Emerald, Redstone, MoonShard, Netherite}
        List<Item> finding = new ArrayList<>();
        finding.add(Items.EMERALD);
        finding.add(Items.REDSTONE);
        finding.add(RegCustomItem.MOONDUST_CRYSTAL_SHARD);
        finding.add(Items.NETHERITE_INGOT);

        int[] checkSlots = {1, 3, 5, 7};
        for (int i : checkSlots) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) return false;
            if (finding.contains(stack.getItem())) {
                finding.remove(stack.getItem());
            } else {
                return false; // Valid item but duplicate or unexpected
            }
        }

        return finding.isEmpty();
    }

    private boolean stackMatches(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.getItem() == item;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        return new ItemStack(SscAddon.SP_UPGRADE_THING);
    }

    @Override
    public boolean fits(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SscAddon.SP_UPGRADE_SERIALIZER;
    }
}
