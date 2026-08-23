package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;

/**
 * REI 展示：合成台特殊配方（毒液腺体 / 无限能量药水）。
 * 输入为有序 3×3 槽位列表（null = 空槽），输出为成品。
 */
public class SpecialCraftingDisplay extends BasicDisplay {

	private final List<EntryIngredient> orderedInputs; // 固定 9 槽（含空槽占位）

	public SpecialCraftingDisplay(List<EntryIngredient> orderedInputs, EntryIngredient output) {
		super(orderedInputs, List.of(output));
		this.orderedInputs = orderedInputs;
	}

	/** 有序 3×3 输入（空槽用 EntryIngredient.empty()）。 */
	public List<EntryIngredient> getOrderedInputs() {
		return orderedInputs;
	}

	@Override
	public List<EntryIngredient> getInputEntries() {
		return orderedInputs;
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return SSCA_REIPlugin.SPECIAL_CRAFTING;
	}

	/** 便捷：空槽占位。 */
	public static EntryIngredient empty() {
		return EntryIngredient.empty();
	}

	/** 便捷：物品输入。 */
	public static EntryIngredient of(net.minecraft.item.ItemConvertible item) {
		return EntryIngredients.of(item);
	}
}
