package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 嗜血指环（吸血蝙蝠专属）。
 * 好处：高血渴阶段（50+ / 75+）吸血率额外 +15%。
 * 坏处：自身满血时仍触发吸血会反噬——每秒对自己造成 1 点真实伤害。
 * 数值逻辑在 SscAddonLivingEntityMixin（吸血加成）与 BatDesmodusBloodThirst（满血反噬）中处理。
 *
 * 获取途径：20% 概率出现在废弃矿井战利品箱中。
 */
public class BloodlustRingItem extends TrinketItem {

	private static final Identifier MINESHAFT_LOOT = new Identifier("minecraft", "chests/abandoned_mineshaft");

	public BloodlustRingItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册嗜血指环到废弃矿井战利品表（25% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!MINESHAFT_LOOT.equals(id)) return;
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(0.20F))
					.with(ItemEntry.builder(SscAddon.BLOODLUST_RING));
			tableBuilder.pool(pool);
		});
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
