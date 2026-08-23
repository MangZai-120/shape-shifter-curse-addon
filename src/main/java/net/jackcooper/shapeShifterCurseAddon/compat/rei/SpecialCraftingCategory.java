package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.DisplayRenderer;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.ArrayList;
import java.util.List;

/**
 * REI 分类：SSCA 特殊合成（合成台 3×3 特殊配方，REI 无法自动解析自定义 SpecialRecipe）。
 * 布局 = 3×3 输入槽 + 箭头 + 输出槽；图标 = 毒液腺体。
 */
public class SpecialCraftingCategory implements DisplayCategory<SpecialCraftingDisplay> {

	@Override
	public CategoryIdentifier<? extends SpecialCraftingDisplay> getCategoryIdentifier() {
		return SSCA_REIPlugin.SPECIAL_CRAFTING;
	}

	@Override
	public Text getTitle() {
		return Text.translatable("gui.ssc_addon.category.special_crafting");
	}

	@Override
	public Renderer getIcon() {
		ItemStack icon = new ItemStack(SscAddon.VENOM_GLAND);
		return new DisplayRenderer() {
			@Override
			public void render(net.minecraft.client.gui.DrawContext context, Rectangle bounds, int mouseX, int mouseY, float delta) {
				context.drawItem(icon, bounds.x, bounds.y);
			}
			@Override
			public int getHeight() {
				return 16;
			}
		};
	}

	@Override
	public int getDisplayHeight() {
		return 66;
	}

	@Override
	public List<Widget> setupDisplay(SpecialCraftingDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		Point origin = new Point(bounds.x, bounds.y);
		List<EntryIngredient> inputs = display.getOrderedInputs();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int idx = row * 3 + col;
				EntryIngredient ing = (idx < inputs.size()) ? inputs.get(idx) : EntryIngredient.empty();
				Point slotPos = new Point(origin.x + col * 18, origin.y + 6 + row * 18);
				widgets.add(Widgets.createSlotBackground(slotPos));
				if (ing.isEmpty()) continue; // 空槽只画背景
				widgets.add(Widgets.createSlot(slotPos).entries(ing).markInput());
			}
		}
		Point arrow = new Point(origin.x + 58, origin.y + 24);
		widgets.add(Widgets.createArrow(arrow));
		Point outPos = new Point(origin.x + 90, origin.y + 24);
		widgets.add(Widgets.createResultSlotBackground(outPos));
		widgets.add(Widgets.createSlot(outPos).entries(display.getOutputEntries().get(0)).markOutput());
		return widgets;
	}

	/** 供插件构造毒液腺体展示：8 蜘蛛眼环绕 + 中心剧毒药水（三种瓶型作为可选项展示）。 */
	static SpecialCraftingDisplay venomGland() {
		EntryIngredient eye = EntryIngredients.of(Items.SPIDER_EYE);
		EntryIngredient poison = EntryIngredient.of(EntryStacks.of(
				PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON)),
				EntryStacks.of(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON)),
				EntryStacks.of(PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON)));
		List<EntryIngredient> grid = new ArrayList<>(List.of(eye, eye, eye, eye, poison, eye, eye, eye, eye));
		return new SpecialCraftingDisplay(grid, EntryIngredients.of(SscAddon.VENOM_GLAND));
	}
}
