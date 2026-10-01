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
 * 在各系与通用变体的一至五级法阵中按权重抽取，四、五级为稀有产出。
 */
public final class FormationLoot {
	private FormationLoot() {
	}

	/** 每个箱子生成法阵的概率（比卷轴 5% 更稀有）。 */
	private static final float CHANCE = 0.03F;

	/** 各等级生成权重（index = level-1）。 */
	private static final int[] LEVEL_WEIGHTS = {40, 25, 12, 5, 2};

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
// 3% 概率触发；触发后按系别（+通用系三变体）× 1-3 级法阵按权重抽一张
		LootPool.Builder pool = LootPool.builder()
				.rolls(ConstantLootNumberProvider.create(1.0F))
				.conditionally(RandomChanceLootCondition.builder(CHANCE));
			for (FormationElement element : FormationElement.values()) {
				for (String variant : variantsOf(element)) {
					for (int level = 1; level <= LEVEL_WEIGHTS.length; level++) {
						pool.with(formationEntry(element, variant, level, LEVEL_WEIGHTS[level - 1]));
					}
				}
			}
			tableBuilder.pool(pool);
		});
	}

	/** 通用系三变体迭代（非通用系返回 null 占位）。 */
	private static String[] variantsOf(FormationElement element) {
		return element == FormationElement.UNIVERSAL
				? new String[]{FormationData.VARIANT_REGEN, FormationData.VARIANT_MANA,
						FormationData.VARIANT_EXP, FormationData.VARIANT_RECOVERY}
				: new String[]{null};
	}

	// 1.20.1 中 SetNbtLootFunction.builder(NbtCompound) 是唯一可用重载（@Deprecated 但无替代，同 MagicScrollLoot）
	@SuppressWarnings("deprecation")
	private static net.minecraft.loot.entry.LootPoolEntry.Builder<?> formationEntry(FormationElement element, String variant, int level, int weight) {
		NbtCompound nbt = createNaturalFormation(element, level, variant).getOrCreateNbt();
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

	public static ItemStack createNaturalFormation(FormationElement element, int level, String variant) {
		return net.jackcooper.shapeShifterCurseAddon.spell.ArcaneAnalysis.markUnanalyzed(FormationData.create(element, level, variant));
	}
}
