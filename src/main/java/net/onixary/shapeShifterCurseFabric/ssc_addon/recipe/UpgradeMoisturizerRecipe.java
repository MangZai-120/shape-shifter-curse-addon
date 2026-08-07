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
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem;

/**
 * 便携加湿器升级配方（固定 3x3 图案，保留原充能，不超新等级上限）：
 * 1→2：四角湿海绵 + 四边金锥 + 中心一级加湿器。
 * 2→3：四角海洋之心 + 四边钻石 + 中心二级加湿器。
 */
public class UpgradeMoisturizerRecipe extends SpecialCraftingRecipe {

	private static final int[] CORNERS = {0, 2, 6, 8};
	private static final int[] EDGES = {1, 3, 5, 7};

	public UpgradeMoisturizerRecipe(Identifier id, CraftingRecipeCategory category) {
		super(id, category);
	}

	/** 升级到下一级四角所需材料：一级用湿海绵，二级用海洋之心。 */
	private static Item cornerMaterial(int level) {
		return level == 1 ? Items.WET_SPONGE : Items.HEART_OF_THE_SEA;
	}

	/** 升级到下一级四边所需材料：一级用金锥，二级用钻石。 */
	private static Item edgeMaterial(int level) {
		return level == 1 ? Items.GOLD_INGOT : Items.DIAMOND;
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		if (inventory.getWidth() != 3 || inventory.getHeight() != 3) return false;
		ItemStack center = inventory.getStack(4);
		if (center.getItem() != SscAddon.PORTABLE_MOISTURIZER) return false;
		int level = PortableMoisturizerItem.getLevel(center);
		if (level >= PortableMoisturizerItem.MAX_LEVEL) return false;
		Item corner = cornerMaterial(level);
		Item edge = edgeMaterial(level);
		for (int i : CORNERS) if (inventory.getStack(i).getItem() != corner) return false;
		for (int i : EDGES) if (inventory.getStack(i).getItem() != edge) return false;
		return true;
	}

	@Override
	public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
		ItemStack center = inventory.getStack(4);
		if (center.getItem() != SscAddon.PORTABLE_MOISTURIZER) return ItemStack.EMPTY;
		int level = PortableMoisturizerItem.getLevel(center);
		if (level >= PortableMoisturizerItem.MAX_LEVEL) return ItemStack.EMPTY;
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
		return SscAddon.UPGRADE_MOISTURIZER_SERIALIZER;
	}
}
