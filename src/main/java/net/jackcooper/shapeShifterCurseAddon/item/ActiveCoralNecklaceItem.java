package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.client.item.TooltipContext;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ActiveCoralNecklaceItem extends AccessoryItem {
	public ActiveCoralNecklaceItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.active_coral_necklace.desc").formatted(Formatting.BLUE));
		tooltip.add(Text.translatable("item.ssc_addon.active_coral_necklace.tooltip_decay").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.active_coral_necklace.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
