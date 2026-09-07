package net.jackcooper.shapeShifterCurseAddon.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;

/**
 * 增强法阵的自然宝箱生成（jackcooper）。与魔法卷轴同域注入：目标结构箱子 3% 概率触发 →
 * 在「火/冰两系 × 1-3 级法阵」中按权重抽取一张（1 级最常见；4/5 级只能靠研究台抄写已记录的高等级法阵获得）。
 */
public final class FormationLoot {
	private FormationLoot() {
	}

	/** 每个箱子生成法阵的概率（比卷轴 5% 更稀有）。 */
	private static final float CHANCE = 0.03F;

	/** 各等级生成权重（index = level-1，只掉 1-3 级）。 */
	private static final int[] LEVEL_WEIGHTS = {40, 25, 12};

	/** 目标原版结构箱子战利品表（与魔法卷轴同域）。 */
	private static final Identifier[] TARGET_CHESTS = {
			new Identifier("minecraft", "chests/simple_dungeon"),
			new Identifier("minecraft", "chests/abandoned_mineshaft"),
			new Identifier("minecraft", "chests/igloo_chest"),
			new Identifier("minecraft", "chests/woodland_mansion"),
			new Identifier("minecraft", "chests/ruined_portal"),
			new Identifier("minecraft", "chests/shipwreck_treasure"),
			new Identifier("minecraft", "chests/buried_treasure"),
			new Identifier("minecraft", "chests/underwater_ruin_small"),
			new Identifier("minecraft", "chests/underwater_ruin_big"),
			new Identifier("minecraft", "chests/stronghold_library"),
			new Identifier("minecraft", "chests/stronghold_corridor"),
			new Identifier("minecraft", "chests/stronghold_crossing"),
			new Identifier("minecraft", "chests/ancient_city"),
			new Identifier("minecraft", "chests/bastion_other"),
			new Identifier("minecraft", "chests/end_city_treasure")
	};

	public static void register() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!isTargetChest(id)) {
				return;
			}
			// 3% 概率触发；触发后在火/冰 × 1-3 级法阵中按权重抽一张
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(CHANCE));
			for (FormationElement element : FormationElement.values()) {
				for (int level = 1; level <= LEVEL_WEIGHTS.length; level++) {
					pool.with(formationEntry(element, level, LEVEL_WEIGHTS[level - 1]));
				}
			}
			tableBuilder.pool(pool);
		});
	}

	// 1.20.1 中 SetNbtLootFunction.builder(NbtCompound) 是唯一可用重载（@Deprecated 但无替代，同 MagicScrollLoot）
	@SuppressWarnings("deprecation")
	private static net.minecraft.loot.entry.LootPoolEntry.Builder<?> formationEntry(FormationElement element, int level, int weight) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString(FormationData.NBT_ELEMENT, element.id);
		nbt.putInt(FormationData.NBT_LEVEL, level);
		return ItemEntry.builder(SscAddon.FORMATION)
				.apply(SetNbtLootFunction.builder(nbt))
				.weight(weight);
	}

	private static boolean isTargetChest(Identifier id) {
		for (Identifier target : TARGET_CHESTS) {
			if (target.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** 供命令调试：生成一张指定系别等级的法阵。 */
	public static ItemStack createFormation(FormationElement element, int level) {
		return FormationData.create(element, level);
	}
}
