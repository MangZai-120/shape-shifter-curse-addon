package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 空白法阵纸（jackcooper）。月尘纯晶×4 中间夹一张纸合成；放入法术研究台，
 * 配合对应系油墨抄写已学习的增强法阵。
 */
public class BlankFormationPaperItem extends Item {

	public BlankFormationPaperItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.blank_formation_paper.tip").formatted(Formatting.DARK_GRAY));
	}
}
