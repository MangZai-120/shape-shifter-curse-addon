package net.jackcooper.shapeShifterCurseAddon.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

import java.util.Set;

/**
 * 月髓环（{@link SscAddon#SP_UPGRADE_THING}）与进化石（{@link SscAddon#EVOLUTION_STONE}）的自然宝箱生成。
 * <p>
 * 仅沉船 / 古城 / 堡垒遗迹（猪人城堡） / 末地船 的箱子生成：每个箱子 <b>3% 概率</b>触发，
 * 触发后在月髓环与进化石之间<b>二选一</b>（等权重各 50%）。
 * 注：战利品表粒度只能到结构级——堡垒遗迹 4 种箱子、沉船补给/宝藏箱、末地船共用末地城宝藏表，均一并覆盖。
 */
public final class EvolutionItemsLoot {
	private EvolutionItemsLoot() {
	}

	/** 目标原版结构箱子战利品表。 */
	private static final Set<Identifier> TARGET_CHESTS = Set.of(
			// 沉船（补给舱 + 宝藏舱）
			new Identifier("minecraft", "chests/shipwreck_supply"),
			new Identifier("minecraft", "chests/shipwreck_treasure"),
			// 古城
			new Identifier("minecraft", "chests/ancient_city"),
			// 堡垒遗迹（猪人城堡）4 种箱子
			new Identifier("minecraft", "chests/bastion_treasure"),
			new Identifier("minecraft", "chests/bastion_other"),
			new Identifier("minecraft", "chests/bastion_bridge"),
			new Identifier("minecraft", "chests/bastion_hoglin_stable"),
			// 末地船（与末地城宝藏箱共用 end_city_treasure）
			new Identifier("minecraft", "chests/end_city_treasure")
	);

	/** 每个箱子生成月髓环 / 进化石（二选一）的总概率。 */
	private static final float CHANCE = 0.03F;

	public static void register() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!TARGET_CHESTS.contains(id)) {
				return;
			}
			// 3% 概率触发；触发后在月髓环与进化石之间二选一（等权重各 50%）
			tableBuilder.pool(LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(CHANCE))
					.with(ItemEntry.builder(SscAddon.SP_UPGRADE_THING))
					.with(ItemEntry.builder(SscAddon.EVOLUTION_STONE)));
		});
	}
}
