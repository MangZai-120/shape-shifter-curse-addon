package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
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

	@Override
	public void registerTransferHandlers(TransferHandlerRegistry registry) {
		// SSCA 特殊配方的快速转移：支持带 NBT 的药水材料（REI 原生转移只按裸 item id 匹配会丢 NBT）
		registry.register(new SscSpecialRecipeTransferHandler());
	}

	public void registerDisplays(DisplayRegistry registry) {
		registry.add(venomGlandDisplay());
		registry.add(infiniteEnergyPotionDisplay());		// 酿造转换（mixin 实现，REI 无法自动发现）：饮用型+火药→喷溅型；喷溅型+龙息→滞留型。
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
	 * 使用 SscSpecialCraftingDisplay 以获得支持 NBT 药水的快速转移。
	 */
	private static SscSpecialCraftingDisplay venomGlandDisplay() {
		ItemStack[] poisonAlts = new ItemStack[]{
				PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON),
				PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON),
				PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON)};
		ItemStack[][] grid = new ItemStack[9][];
		for (int i = 0; i < 9; i++) {
			grid[i] = (i == 4) ? poisonAlts : new ItemStack[]{new ItemStack(Items.SPIDER_EYE)};
		}
		return SscSpecialCraftingDisplay.of(grid, new ItemStack(SscAddon.VENOM_GLAND));
	}

	/**
	 * 无限压缩能量药水配方卡片：上中月髓环 + 中间行 附魔金苹果/压缩能量药水（三种瓶型）/附魔金苹果。
	 * 使用 SscSpecialCraftingDisplay 以获得支持 NBT 药水的快速转移。
	 */
	private static SscSpecialCraftingDisplay infiniteEnergyPotionDisplay() {
		ItemStack[] feedAlts = new ItemStack[]{
				PotionUtil.setPotion(new ItemStack(Items.POTION), RegCustomPotions.FEED_POTION),
				PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), RegCustomPotions.FEED_POTION),
				PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), RegCustomPotions.FEED_POTION)};
		ItemStack[] moonRing = new ItemStack[]{new ItemStack(SscAddon.SP_UPGRADE_THING)};
		ItemStack[] apple = new ItemStack[]{new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)};
		ItemStack[][] grid = new ItemStack[9][];
		grid[0] = new ItemStack[0];
		grid[1] = moonRing;
		grid[2] = new ItemStack[0];
		grid[3] = apple;
		grid[4] = feedAlts;
		grid[5] = apple;
		grid[6] = new ItemStack[0];
		grid[7] = new ItemStack[0];
		grid[8] = new ItemStack[0];
		return SscSpecialCraftingDisplay.of(grid, new ItemStack(SscAddon.INFINITE_ENERGY_POTION));
	}
}
