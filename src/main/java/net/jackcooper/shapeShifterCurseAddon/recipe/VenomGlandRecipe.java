package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

/**
 * 毒液腺体合成配方（特殊配方：中心必须是剧毒药水，原版 shaped 无法按药水 NBT 匹配）。
 * 布局（3×3 满格）：
 * <pre>
 *   S S S    S = 蜘蛛眼 ×8（minecraft:spider_eye）
 *   S P S    P = 剧毒药水（普通 poison / 延长 long_poison / II strong_poison 三种均可）
 *   S S S
 * </pre>
 */
public class VenomGlandRecipe extends SpecialCraftingRecipe {

	public VenomGlandRecipe(Identifier id, CraftingRecipeCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(RecipeInputInventory inventory, World world) {
		if (inventory.getWidth() < 3 || inventory.getHeight() < 3) {
			return false;
		}
		// 中心（index 4）必须是剧毒药水，其余 8 格全是蜘蛛眼
		if (!isPoisonPotion(inventory.getStack(4))) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (i == 4) continue;
			if (!inventory.getStack(i).isOf(Items.SPIDER_EYE)) {
				return false;
			}
		}
		return true;
	}

	/** 剧毒药水 = 饮用/喷溅/滞留任一瓶且药水类型为 poison / long_poison / strong_poison（三级任一）。 */
	private boolean isPoisonPotion(ItemStack stack) {
		if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
			return false;
		}
		Potion potion = PotionUtil.getPotion(stack);
		return potion == Potions.POISON || potion == Potions.LONG_POISON || potion == Potions.STRONG_POISON;
	}

	@Override
	public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
		return new ItemStack(SscAddon.VENOM_GLAND);
	}

	@Override
	public boolean fits(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.VENOM_GLAND_SERIALIZER;
	}
}
