package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.item.Item;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.recipe.Ingredient;

/**
 * 无限压缩能量药水的静态酿造 item 配方。
 * 直接向 {@link BrewingRecipeRegistry#ITEM_RECIPES} 注册，使酿造台底部槽位接受这些自定义物品
 * （vanilla 通过 isItemRecipe 扫描 ITEM_RECIPES 的 input 判断是否可放入）。
 * 继承 {@link BrewingRecipeRegistry.Recipe} 而非主包的 DynamicRecipe，因此不会被主包动态酿造 reload 清除，
 * 也不依赖主包数据驱动系统的版本支持。
 */
public class InfinitePotionBrewingRecipe extends BrewingRecipeRegistry.Recipe<Item> {
	public InfinitePotionBrewingRecipe(Item input, Ingredient ingredient, Item output) {
		super(input, ingredient, output);
	}
}
