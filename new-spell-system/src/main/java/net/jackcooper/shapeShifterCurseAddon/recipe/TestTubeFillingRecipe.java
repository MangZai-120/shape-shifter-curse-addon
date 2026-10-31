package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** Shapeless bottle + three tubes, keeping the glass bottle in its original grid slot. */
public final class TestTubeFillingRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<TestTubeFillingRecipe> SERIALIZER = new SpecialRecipeSerializer<>(TestTubeFillingRecipe::new);
    public TestTubeFillingRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }
    public static void register() { Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "test_tube_filling"), SERIALIZER); }

    private int bottleSlot(RecipeInputInventory inventory) {
        int bottle = -1, tubes = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(PotionTestTubes.EMPTY)) tubes++;
            else if (bottle == -1 && stack.getCount() == 1 && PotionTestTubes.isBottle(stack)) bottle = i;
            else return -1;
        }
        return tubes == PotionTestTubes.PORTIONS ? bottle : -1;
    }
    @Override public boolean matches(RecipeInputInventory inventory, World world) { return bottleSlot(inventory) >= 0; }
    @Override public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        int slot = bottleSlot(inventory);
        return slot < 0 ? ItemStack.EMPTY : PotionTestTubes.divide(inventory.getStack(slot));
    }
    @Override public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
        var remainder = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        int slot = bottleSlot(inventory);
        if (slot >= 0) remainder.set(slot, new ItemStack(Items.GLASS_BOTTLE));
        return remainder;
    }
    @Override public boolean fits(int width, int height) { return width * height >= 4; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
}
