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
 * 腐殖之戒（寄生果蝠专属）。
 * 好处：敌方削弱果效果时长 +50%。
 * 坏处：友方增益果效果时长 -30%（更偏攻击 / 控制向）。
 * 数值逻辑在 ParasiticFruitSeedPower 的果实时长计算中按是否装备本饰品调整。
 *
 * 获取途径：20% 概率出现在废弃矿井战利品箱中。
 */
public class HumusRingItem extends TrinketItem {

	private static final Identifier MINESHAFT_LOOT = new Identifier("minecraft", "chests/abandoned_mineshaft");

	public HumusRingItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册腐殖之戒到废弃矿井战利品表（25% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!MINESHAFT_LOOT.equals(id)) return;
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(0.20F).build())
					.with(ItemEntry.builder(SscAddon.HUMUS_RING));
			tableBuilder.pool(pool);
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatParasiticFruit(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.humus_ring.tooltip_1").formatted(Formatting.DARK_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.humus_ring.tooltip_2").formatted(Formatting.GREEN));
		tooltip.add(Text.translatable("item.ssc_addon.humus_ring.tooltip_exclusive").formatted(Formatting.GOLD));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
