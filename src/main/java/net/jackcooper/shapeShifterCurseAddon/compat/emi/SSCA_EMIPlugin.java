package net.jackcooper.shapeShifterCurseAddon.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;

/**
 * SSCA 的 EMI 插件：注册「SSCA 特殊合成」分类展示毒液腺体配方
 * （8 蜘蛛眼环绕 + 中心剧毒药水×3 瓶型任一）。仅装 EMI 时加载（fabric.mod.json 的 emi 入口）。
 */
public class SSCA_EMIPlugin implements EmiPlugin {

	public static final EmiRecipeCategory SPECIAL_CRAFTING = new EmiRecipeCategory(
			new Identifier("ssc_addon", "special_crafting"),
			(EmiRenderable) (matrices, x, y, delta) ->
					matrices.drawItem(new ItemStack(SscAddon.VENOM_GLAND), x, y));

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(SPECIAL_CRAFTING);

		// 毒液腺体：8 蜘蛛眼 + 中心三种剧毒瓶型（EmiIngredient 组合为可切换列表）
		EmiStack eye = EmiStack.of(Items.SPIDER_EYE);
		EmiIngredient poison = EmiIngredient.of(List.of(
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON))));
		List<EmiIngredient> grid = List.of(eye, eye, eye, eye, poison, eye, eye, eye, eye);
		registry.addRecipe(new EmiCraftingRecipe(
				grid, EmiStack.of(SscAddon.VENOM_GLAND), new Identifier("ssc_addon", "venom_gland"), true));
	}
}
