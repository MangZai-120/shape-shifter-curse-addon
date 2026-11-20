package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan;

import java.util.List;

/**
 * 便携加湿器升级配方（固定 3x3 图案，保留原充能，不超新等级上限）：
 * 1→2：四角湿海绵 + 四边金锭 + 中心一级加湿器。
 * 2→3：四角海洋之心 + 四边钻石 + 中心二级加湿器。
 */
public class UpgradeMoisturizerRecipe extends SpecialCraftingRecipe {

	private final int inputLevel;
	private final DefaultedList<Ingredient> ingredients;

	/** The original data-pack serializer accepts either input level. */
	public UpgradeMoisturizerRecipe(Identifier id, CraftingRecipeCategory category) {
		this(id, category, 0);
	}

	public UpgradeMoisturizerRecipe(Identifier id, CraftingRecipeCategory category, int inputLevel) {
		super(id, category);
		this.inputLevel = inputLevel;
		this.ingredients = inputLevel == 0 ? DefaultedList.of() : ingredientsForLevel(inputLevel);
	}

	private static Item material(int level, int slot) {
		if (slot == 4) return SscAddon.PORTABLE_MOISTURIZER;
		if (slot % 2 == 0) return level == 1 ? Items.WET_SPONGE : Items.HEART_OF_THE_SEA;
		return level == 1 ? Items.GOLD_INGOT : Items.DIAMOND;
	}

	public boolean acceptsLevel(int level) {
		return level >= 1 && level < PortableMoisturizerItem.MAX_LEVEL && (inputLevel == 0 || inputLevel == level);
	}

	public static DefaultedList<Ingredient> ingredientsForLevel(int level) {
		DefaultedList<Ingredient> grid = DefaultedList.ofSize(9, Ingredient.EMPTY);
		for (int slot = 0; slot < grid.size(); slot++) {
			grid.set(slot, slot == 4 ? Ingredient.ofStacks(PortableMoisturizerItem.preview(level))
					: Ingredient.ofItems(material(level, slot)));
		}
		return grid;
	}

	@Override
	public DefaultedList<Ingredient> getIngredients() {
		return ingredients;
	}

	/** Vanilla's recipe matcher groups all three humidifiers under the same raw item ID. */
	public SscTransferPlan.Plan planPlacement(CraftingScreenHandler table) {
		var required = ingredients.stream().map(ingredient -> List.of(ingredient.getMatchingStacks())).toList();
		var grid = table.slots.subList(1, 10).stream().map(Slot::getStack).toList();
		var inventory = table.slots.subList(10, 46).stream().map(Slot::getStack).toList();
		return SscTransferPlan.create(required, grid, inventory, table.getCursorStack());
	}

	@Override
	public boolean isIgnoredInRecipeBook() {
		return inputLevel == 0;
	}

	@Override
	public ItemStack getOutput(DynamicRegistryManager registryManager) {
		return inputLevel == 0 ? ItemStack.EMPTY : PortableMoisturizerItem.preview(inputLevel + 1);
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		if (inventory.getWidth() != 3 || inventory.getHeight() != 3) return false;
		ItemStack center = inventory.getStack(4);
		if (center.getItem() != SscAddon.PORTABLE_MOISTURIZER) return false;
		int level = PortableMoisturizerItem.getLevel(center);
		if (!acceptsLevel(level)) return false;
		for (int slot = 0; slot < 9; slot++) if (!inventory.getStack(slot).isOf(material(level, slot))) return false;
		return true;
	}

	@Override
	public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
		ItemStack center = inventory.getStack(4);
		if (center.getItem() != SscAddon.PORTABLE_MOISTURIZER) return ItemStack.EMPTY;
		int level = PortableMoisturizerItem.getLevel(center);
		if (!acceptsLevel(level)) return ItemStack.EMPTY;
		ItemStack result = center.copy();
		result.setCount(1);
		PortableMoisturizerItem.setLevel(result, level + 1);
		// 保留原充能（setCharge 内部按新等级上限自动裁剪）
		PortableMoisturizerItem.setCharge(result, PortableMoisturizerItem.getCharge(center));
		return result;
	}

	@Override
	public boolean fits(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return switch (inputLevel) {
			case 1 -> SscAddon.MOISTURIZER_TO_2;
			case 2 -> SscAddon.MOISTURIZER_TO_3;
			default -> SscAddon.UPGRADE_MOISTURIZER_SERIALIZER;
		};
	}
}
