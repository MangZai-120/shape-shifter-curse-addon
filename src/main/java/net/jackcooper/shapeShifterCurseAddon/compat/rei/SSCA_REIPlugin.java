package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import me.shedaniel.rei.plugin.common.displays.brewing.DefaultBrewingDisplay;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;

import java.util.List;
import java.util.Optional;

/**
 * SSCA 的 REI 客户端插件（vanilla 工作台风格显示）。
 * <p>
 * 毒液腺体是 SpecialCraftingRecipe（按药水 NBT 匹配），REI 默认插件无法自动解析；
 * 这里手工构造一个 {@link DefaultCraftingDisplay} 子类挂到 vanilla 工作台分类
 * （BuiltinPlugin.CRAFTING）——布局、渲染、搜索全部复用 REI 原版合成表的那套，
 * 与其它合成配方同页展示。仅在装有 REI 时加载（fabric.mod.json 的 rei_client 入口）。
 */
public class SSCA_REIPlugin implements REIClientPlugin {

	public Identifier getIdentifier() {
		return new Identifier("ssc_addon", "rei_plugin");
	}

	public void registerDisplays(DisplayRegistry registry) {
		registry.add(venomGlandDisplay());
		registry.add(infiniteEnergyPotionDisplay());
		// 酿造转换（mixin 实现，REI 无法自动发现）：饮用型+火药→喷溅型；喷溅型+龙息→滞留型。
		// DefaultBrewingDisplay 自带酿造分类标识，自动归入 REI 的酿造页。
		registry.add(new DefaultBrewingDisplay(
				EntryIngredients.of(SscAddon.INFINITE_ENERGY_POTION),
				EntryIngredients.of(Items.GUNPOWDER),
				EntryStacks.of(SscAddon.INFINITE_ENERGY_POTION_SPLASH)));
		registry.add(new DefaultBrewingDisplay(
				EntryIngredients.of(SscAddon.INFINITE_ENERGY_POTION_SPLASH),
				EntryIngredients.of(Items.DRAGON_BREATH),
				EntryStacks.of(SscAddon.INFINITE_ENERGY_POTION_LINGERING)));
	}

	/**
	 * 毒液腺体配方卡片：8 蜘蛛眼环绕 + 中心剧毒药水（三种瓶型可切换）。
	 * 继承 DefaultCraftingDisplay = 实现 CraftingDisplay 接口，
	 * DisplayValidator 泛型校验对 vanilla CRAFTING 分类必然通过。
	 * recipe 传 Optional.empty()——显示不需要它（仅 R 键配方填充用到）。
	 */
	private static DefaultCraftingDisplay<?> venomGlandDisplay() {
		EntryIngredient eye = EntryIngredients.of(Items.SPIDER_EYE);
		// 中心槽：三种瓶型（饮用/喷溅/滞留）的剧毒药水作为可切换选项
		EntryIngredient poison = EntryIngredient.of(
				EntryStacks.of(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON)),
				EntryStacks.of(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON)),
				EntryStacks.of(PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON)));
		return new DefaultCraftingDisplay<>(
				List.of(eye, eye, eye, eye, poison, eye, eye, eye, eye),
				List.of(EntryIngredients.of(SscAddon.VENOM_GLAND)),
				Optional.empty()) {
			// SimpleGridMenuDisplay 的两个抽象方法（3×3 满格网格）
			@Override public int getWidth() { return 3; }
			@Override public int getHeight() { return 3; }
		};
	}

	/**
	 * 无限压缩能量药水配方卡片：上中月髓环 + 中间行 附魔金苹果/压缩能量药水/附魔金苹果（上对齐摆放）。
	 * 同为 SpecialCraftingRecipe（按药水 NBT 匹配），REI 默认插件无法自动解析，手工构造展示。
	 */
	private static DefaultCraftingDisplay<?> infiniteEnergyPotionDisplay() {
		EntryIngredient empty = EntryIngredient.empty();
		EntryIngredient moonRing = EntryIngredients.of(SscAddon.SP_UPGRADE_THING);
		EntryIngredient apple = EntryIngredients.of(Items.ENCHANTED_GOLDEN_APPLE);
		EntryIngredient feedPotion = EntryIngredients.of(
				PotionUtil.setPotion(new ItemStack(Items.POTION), RegCustomPotions.FEED_POTION));
		return new DefaultCraftingDisplay<>(
				List.of(empty, moonRing, empty, apple, feedPotion, apple, empty, empty, empty),
				List.of(EntryIngredients.of(SscAddon.INFINITE_ENERGY_POTION)),
				Optional.empty()) {
			@Override public int getWidth() { return 3; }
			@Override public int getHeight() { return 3; }
		};
	}
}
