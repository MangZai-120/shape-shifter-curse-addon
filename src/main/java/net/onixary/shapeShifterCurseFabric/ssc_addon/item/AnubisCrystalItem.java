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
 * 阿努比斯权杖上的水晶 - SP阿努比斯之狼专属饰品
 * 效果：增加冥狼召唤数量和上限
 * 获取途径：沙漠神殿战利品箱，15%概率
 */
public class AnubisCrystalItem extends TrinketItem {
	public AnubisCrystalItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册到沙漠神殿战利品表（15%概率，1个）
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (id.equals(new Identifier("minecraft", "chests/desert_pyramid"))) {
				LootPool.Builder poolBuilder = LootPool.builder()
						.rolls(ConstantLootNumberProvider.create(1.0F))
						.conditionally(RandomChanceLootCondition.builder(0.15F).build())
						.with(ItemEntry.builder(SscAddon.ANUBIS_CRYSTAL));
				tableBuilder.pool(poolBuilder);
			}
		});
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
