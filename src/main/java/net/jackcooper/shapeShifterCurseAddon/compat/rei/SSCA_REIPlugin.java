package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

/**
 * SSCA 的 REI 客户端插件：注册「SSCA 特殊合成」分类，
 * 手工添加 REI 解析不了的两个 SpecialCraftingRecipe 展示：
 * <ul>
 *   <li>毒液腺体（8 蜘蛛眼 + 剧毒药水×3 瓶型任一）</li>
 * </ul>
 * 仅在装有 REI 时加载（fabric.mod.json 的 rei_plugins 入口）。
 */
public class SSCA_REIPlugin implements REIClientPlugin {

	public static final me.shedaniel.rei.api.common.category.CategoryIdentifier<SpecialCraftingDisplay> SPECIAL_CRAFTING =
			me.shedaniel.rei.api.common.category.CategoryIdentifier.of(new Identifier("ssc_addon", "special_crafting"));

	public Identifier getIdentifier() {
		return new Identifier("ssc_addon", "rei_plugin");
	}

	public void registerCategories(CategoryRegistry registry) {
		registry.add(new SpecialCraftingCategory());
	}

	public void registerDisplays(DisplayRegistry registry) {
		registry.add(SpecialCraftingCategory.venomGland());
	}
}
