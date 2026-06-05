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
 * 双生种荚（寄生果蝠专属）。
 * 好处：一次「灵果寄生」播种会额外寄生主目标附近最近的另一个生物（敌友各自结对应果实）。
 * 坏处：种子量消耗翻倍（每次 2 点），且冷却额外 +1 秒。
 * 数值逻辑在 ParasiticFruitSeedPower.onUse 中按是否装备本饰品调整。
 */
public class TwinPodItem extends TrinketItem {
	public TwinPodItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatParasiticFruit(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.twin_pod.tooltip_1").formatted(Formatting.DARK_GREEN));
		tooltip.add(Text.translatable("item.ssc_addon.twin_pod.tooltip_2").formatted(Formatting.YELLOW));
		tooltip.add(Text.translatable("item.ssc_addon.twin_pod.tooltip_exclusive").formatted(Formatting.GOLD));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
