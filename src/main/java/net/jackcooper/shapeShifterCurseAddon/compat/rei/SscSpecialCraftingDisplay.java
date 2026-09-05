package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SSCA 特殊配方的 REI 展示卡片（可被 {@link SscSpecialRecipeTransferHandler} 识别）。
 * <p>
 * 继承 DefaultCraftingDisplay 挂到 vanilla 工作台分类（BuiltinPlugin.CRAFTING），
 * 布局/渲染/搜索复用 REI 原版合成表；额外携带「每格材料需求」供快速转移用
 * （含 NBT 的药水栈——REI 原生转移按裸 item id 匹配会丢 NBT，故自管转移）。
 */
public class SscSpecialCraftingDisplay extends DefaultCraftingDisplay<net.minecraft.recipe.Recipe<?>> {

	/** 每格的材料候选（9 格，每格可多个候选如三种瓶型；空列表=该格留空）。 */
	private final List<List<ItemStack>> requiredPerSlot;

	public SscSpecialCraftingDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs,
								 List<List<ItemStack>> requiredPerSlot) {
		super(inputs, outputs, Optional.empty());
		this.requiredPerSlot = requiredPerSlot;
	}

	// SimpleGridMenuDisplay 的两个抽象方法（3×3 满格网格）
	@Override
	public int getWidth() {
		return 3;
	}

	@Override
	public int getHeight() {
		return 3;
	}

	/** 供转移处理器预检与取材：第 i 格（0..8）的材料候选列表。 */
	public List<List<ItemStack>> getRequiredPerSlot() {
		return requiredPerSlot;
	}

	/** 便捷构造：单候选材料网格直接转 EntryIngredient。 */
	public static SscSpecialCraftingDisplay of(ItemStack[][] grid3x3, ItemStack output) {
		List<EntryIngredient> inputs = new ArrayList<>(9);
		List<List<ItemStack>> required = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			ItemStack[] alts = grid3x3[i];
			List<ItemStack> altList = new ArrayList<>();
			EntryIngredient.Builder builder = EntryIngredient.builder();
			if (alts != null) {
				for (ItemStack alt : alts) {
					if (alt != null && !alt.isEmpty()) {
						altList.add(alt);
						builder.add(me.shedaniel.rei.api.common.util.EntryStacks.of(alt));
					}
				}
			}
			required.add(altList);
			inputs.add(builder.build());
		}
		return new SscSpecialCraftingDisplay(inputs,
				List.of(EntryIngredient.of(me.shedaniel.rei.api.common.util.EntryStacks.of(output))), required);
	}
}
