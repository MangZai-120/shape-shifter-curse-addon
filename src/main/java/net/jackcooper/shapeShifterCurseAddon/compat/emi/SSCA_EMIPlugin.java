package net.jackcooper.shapeShifterCurseAddon.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.EmiBrewingRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;

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

		// 无限压缩能量药水：上中月髓环 + 中间行 附魔金苹果/压缩能量药水/附魔金苹果（上对齐摆放）
		// 注意：EMI 1.1.0 无 EmiIngredient.EMPTY，空槽用 EmiStack.EMPTY 表示
		EmiIngredient moonRing = EmiStack.of(SscAddon.SP_UPGRADE_THING);
		EmiIngredient apple = EmiStack.of(Items.ENCHANTED_GOLDEN_APPLE);
		// 中间槽：三种瓶型（饮用/喷溅/滞留）的压缩能量药水，EmiIngredient 组合为可切换列表
		EmiIngredient feedPotion = EmiIngredient.of(List.of(
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.POTION), RegCustomPotions.FEED_POTION)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), RegCustomPotions.FEED_POTION)),
				EmiStack.of(PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), RegCustomPotions.FEED_POTION))));
		List<EmiIngredient> potionGrid = List.of(
				EmiStack.EMPTY, moonRing, EmiStack.EMPTY,
				apple, feedPotion, apple,
				EmiStack.EMPTY, EmiStack.EMPTY, EmiStack.EMPTY);
		registry.addRecipe(new EmiCraftingRecipe(
				potionGrid, EmiStack.of(SscAddon.INFINITE_ENERGY_POTION),
				new Identifier("ssc_addon", "infinite_energy_potion"), false));

		// 酿造转换（mixin 实现，EMI 无法自动发现）：饮用型+火药→喷溅型；喷溅型+龙息→滞留型
		registry.addRecipe(new EmiBrewingRecipe(
				EmiStack.of(SscAddon.INFINITE_ENERGY_POTION),
				EmiStack.of(Items.GUNPOWDER),
				EmiStack.of(SscAddon.INFINITE_ENERGY_POTION_SPLASH),
				new Identifier("ssc_addon", "infinite_energy_potion_brewing_splash")));
		registry.addRecipe(new EmiBrewingRecipe(
				EmiStack.of(SscAddon.INFINITE_ENERGY_POTION_SPLASH),
				EmiStack.of(Items.DRAGON_BREATH),
				EmiStack.of(SscAddon.INFINITE_ENERGY_POTION_LINGERING),
				new Identifier("ssc_addon", "infinite_energy_potion_brewing_lingering")));
	}
}
