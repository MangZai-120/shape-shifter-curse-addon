package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 阿努比斯权杖上的水晶 - SP阿努比斯之狼专属饰品
 * 效果：增加冥狼召唤数量和上限
 */
public class AnubisCrystalItem extends TrinketItem {
	public AnubisCrystalItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isAnubisWolfSP(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.anubis_crystal.tooltip_1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.anubis_crystal.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.anubis_crystal.tooltip_3").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.anubis_crystal.tooltip_4").formatted(Formatting.RED));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
