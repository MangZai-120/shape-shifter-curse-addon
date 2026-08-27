package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.client.item.TooltipContext;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InvisibilityCloakItem extends AccessoryItem {
	public InvisibilityCloakItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isWildCatSP);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.special").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
