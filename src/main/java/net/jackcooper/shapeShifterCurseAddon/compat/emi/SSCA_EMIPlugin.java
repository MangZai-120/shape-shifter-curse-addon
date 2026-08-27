package net.jackcooper.shapeShifterCurseAddon.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

import java.util.List;

/**
 * SSCA 的 EMI 插件：毒液腺体配方用 vanilla 工作台分类展示
 * （EmiCraftingRecipe 天然归入 EMI 的 crafting 分类，与原版合成配方同页）。
 * 仅装 EMI 时加载（fabric.mod.json 的 emi 入口）。
 */
public class SSCA_EMIPlugin implements EmiPlugin {

	@Override
	public void register(EmiRegistry registry) {
		// 毒液腺体：8 蜘蛛眼 + 中心三种剧毒瓶型（EmiIngredient 组合为可切换列表）
		EmiStack eye = EmiStack.of(Items.SPIDER_EYE);
		EmiIngredient poison = EmiIngredient.of(List.of(
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON))));
		List<EmiIngredient> grid = List.of(eye, eye, eye, eye, poison, eye, eye, eye, eye);
		registry.addRecipe(new EmiCraftingRecipe(
				grid, EmiStack.of(SscAddon.VENOM_GLAND), new Identifier("ssc_addon", "venom_gland"), false));
	}
}
