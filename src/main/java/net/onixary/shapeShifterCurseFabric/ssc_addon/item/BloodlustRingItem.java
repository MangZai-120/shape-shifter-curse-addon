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
 * 嗜血指环（吸血蝙蝠专属）。
 * 好处：高血渴阶段（50+ / 75+）吸血率额外 +15%。
 * 坏处：自身满血时仍触发吸血会反噬——每秒对自己造成 1 点真实伤害。
 * 数值逻辑在 SscAddonLivingEntityMixin（吸血加成）与 BatDesmodusBloodThirst（满血反噬）中处理。
 */
public class BloodlustRingItem extends TrinketItem {
	public BloodlustRingItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatDesmodus(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.bloodlust_ring.tooltip_1").formatted(Formatting.DARK_RED));
		tooltip.add(Text.translatable("item.ssc_addon.bloodlust_ring.tooltip_2").formatted(Formatting.RED));
		tooltip.add(Text.translatable("item.ssc_addon.bloodlust_ring.tooltip_exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
