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
 * 渴血石榴石（吸血蝙蝠专属）。
 * 好处：血渴值累积速度 +50%（普攻 / 技能 / 击杀获得量 ×1.5）。
 * 坏处：脱战衰减 +50% 且每秒被动流失 1 点血渴值。
 * 数值逻辑在 BatDesmodusBloodThirst 中按是否装备本饰品调整。
 */
public class BloodGarnetItem extends TrinketItem {
	public BloodGarnetItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatDesmodus(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.blood_garnet.tooltip_1").formatted(Formatting.DARK_RED));
		tooltip.add(Text.translatable("item.ssc_addon.blood_garnet.tooltip_2").formatted(Formatting.RED));
		tooltip.add(Text.translatable("item.ssc_addon.blood_garnet.tooltip_exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
