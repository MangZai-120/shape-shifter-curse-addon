package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.jetbrains.annotations.NotNull;

/**
 * 无限压缩能量药水合成配方（特殊配方，需匹配带 NBT 的 feed_potion 药水，原版 shaped 无法匹配）。
 * 布局（3x3）：
 * <pre>
 *   0 M 0      M = 月髓环 (ssc_addon:sp_upgrade_thing)
 *   A I A      A = 附魔金苹果 (minecraft:enchanted_golden_apple)
 *   0 0 0      I = 压缩能量药水 (原版药水 + feed_potion)
 * </pre>
 */
public class InfiniteEnergyPotionRecipe extends SpecialCraftingRecipe {

	public InfiniteEnergyPotionRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		// 1.20.5+ 的合成输入会先裁剪到非空物品包围盒（顶左对齐）再传给 matches()：
		// 布局「0 M 0 / A I A / 0 0 0」底行全空，包围盒恒为 3×2，裁剪后 M=1 A=3 I=4 A=5，空=0,2。
		// 若玩家在包围盒外多放物品把包围盒撑成 3×3，高度可能为 3，需通过下方空槽循环一并拒绝。
		if (input.getWidth() < 3 || input.getHeight() < 2) {
			return false;
		}
		if (!isMoonRing(input.getStackInSlot(1))) {
			return false;
		}
		if (!isEnchantedGoldenApple(input.getStackInSlot(3)) || !isEnchantedGoldenApple(input.getStackInSlot(5))) {
			return false;
		}
		if (!isCompressedEnergyPotion(input.getStackInSlot(4))) {
			return false;
		}
		// 除 1/3/4/5 四个配方槽外，其余槽（含包围盒被撑大时的额外槽）必须为空
		int size = input.getSize();
		for (int slot = 0; slot < size; slot++) {
			if (slot != 1 && slot != 3 && slot != 4 && slot != 5 && !input.getStackInSlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private boolean isMoonRing(ItemStack stack) {
		return stack.isOf(SscAddon.SP_UPGRADE_THING);
	}

	private boolean isEnchantedGoldenApple(ItemStack stack) {
		return stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
	}

	/** 压缩能量药水 = 饮用/喷溅/滞留三种瓶型之一，且药水类型为 feed_potion。 */
	private boolean isCompressedEnergyPotion(ItemStack stack) {
		boolean isPotionBottle = stack.isOf(Items.POTION)
				|| stack.isOf(Items.SPLASH_POTION)
				|| stack.isOf(Items.LINGERING_POTION);
		PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
		Potion potion = contents != null ? contents.potion().map(RegistryEntry::value).orElse(null) : null;
		return isPotionBottle && potion == RegCustomPotions.FEED_POTION;
	}

	@Override
	public @NotNull ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		return new ItemStack(SscAddon.INFINITE_ENERGY_POTION);
	}

	@Override
	public boolean fits(int width, int height) {
		// 有效形状经包围盒裁剪后为 3×2（两行），配方书按裁剪后的尺寸询问
		return width >= 3 && height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.INFINITE_ENERGY_POTION_SERIALIZER;
	}
}