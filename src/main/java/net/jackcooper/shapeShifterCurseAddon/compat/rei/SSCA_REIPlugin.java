package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.*;
import me.shedaniel.rei.plugin.common.displays.brewing.DefaultBrewingDisplay;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes;
import net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class SSCA_REIPlugin implements REIClientPlugin {
    public Identifier getIdentifier() { return new Identifier("ssc_addon", "rei_plugin"); }
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        var nbt = EntryComparator.nbt();
        registry.register((context, stack) -> nbt.hash(context, PotionTestTubes.subtypeData(stack)), PotionTestTubes.FILLED);
    }
    @Override public void registerEntries(EntryRegistry registry) {
        registry.addEntries(PotionTestTubes.viewerStacks().stream().map(EntryStacks::of).toList());
    }
    @Override public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new SscSpecialRecipeTransferHandler());
    }
    @Override public void registerCategories(CategoryRegistry registry) {
        registry.add(new SscProcessingDisplay.Category());
        registry.addWorkstations(SscProcessingDisplay.ID, EntryStacks.of(net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks.ENERGY_BOTTLER));
    }
	@Override
	public void registerScreens(ScreenRegistry registry) {
		// 绘制法阵界面（FormationResearchScreen）是全屏画布，REI 物品侧栏会盖住画布：
		// 注册 OverlayDecider 对该屏幕返回 FAIL，REI 遍历 decider 时一票否决、不渲染 overlay
		//（反编译核实：RoughlyEnoughItemsCoreClient._shouldReturn 遇任何非 PASS 即隐藏）。
		// 对其它屏幕一律 PASS，行为不变。
		registry.registerDecider(new me.shedaniel.rei.api.client.registry.screen.OverlayDecider() {
			@Override
			public <R extends net.minecraft.client.gui.screen.Screen> boolean isHandingScreen(Class<R> screen) {
				return screen == net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen.class;
			}

			@Override
			public <R extends net.minecraft.client.gui.screen.Screen> ActionResult shouldScreenBeOverlaid(R screen) {
				return screen instanceof net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen
						? ActionResult.FAIL : ActionResult.PASS;
			}
		});
	}

    @Override public void registerDisplays(DisplayRegistry registry) {
        for (var recipe : SscViewerRecipes.crafting(registry.getRecipeManager())) {
            registry.add(new SscSpecialCraftingDisplay(recipe));
            registry.add(DefaultInformationDisplay.createFromEntry(EntryStacks.of(recipe.output()), recipe.output().getName()).line(recipe.description()));
        }
        for (var recipe : SscViewerRecipes.brewing()) registry.add(new DefaultBrewingDisplay(
            EntryIngredients.of(recipe.input()), EntryIngredients.of(recipe.ingredient()), EntryStacks.of(recipe.output())));
        for (var recipe : SscViewerRecipes.processes()) registry.add(new SscProcessingDisplay(recipe));
        for (var info : SscViewerRecipes.information()) registry.add(DefaultInformationDisplay.createFromEntries(
            EntryIngredients.ofItemStacks(info.stacks()), info.stacks().get(0).getName()).line(info.description()));
    }
}
