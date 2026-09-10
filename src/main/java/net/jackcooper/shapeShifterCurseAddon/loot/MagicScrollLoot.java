package net.jackcooper.shapeShifterCurseAddon.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;

/**
 * 魔法卷轴的自然宝箱生成（jackcooper）。各法术卷轴等级固定 1-5 级，<b>不可升级</b>，
 * 只能从世界内自然生成的箱子中开出（照 {@link EvolutionItemsLoot} 模式注入原版战利品表）。
 *
 * <p>生成规则：目标结构箱子 5% 概率触发 → 先按「法术权重」抽出一个法术，再在「该法术 1-5 级」中
 * 按等级权重抽出一张（1 级最常见、5 级最稀有：权重 30/28/22/13/7）。等级存卷轴 NBT {@code Level}（缺省 1）。</p>
 */
public final class MagicScrollLoot {
	private MagicScrollLoot() {
	}

	/** 各等级生成权重（index = level-1）。 */
	private static final int[] LEVEL_WEIGHTS = {30, 28, 22, 13, 7};
	/** 各法术生成权重（与下方法顺序一致；新法术比冰锥稍稀有，AOE/防御类再低一档）。 */
	private static final int[] SPELL_WEIGHTS = {30, 22, 14, 8, 18, 14, 10};
	/** 参与宝箱抽取的法术 id 列表（顺序与权重一致）：冰锥/火球/烈焰新星/陨火/冰锥齐射/冰霜新星/霜甲。 */
	private static final String[] SPELL_PATHS = {
			"frost_spike", "fire_bolt", "flame_nova", "meteor", "ice_barrage", "frost_nova", "frost_armor"
	};
	/** 每个箱子生成魔法卷轴的概率。 */
	private static final float CHANCE = 0.05F;

	/** 目标原版结构箱子战利品表（覆盖常见探险结构，与故事书生成同域）。 */
	private static final Identifier[] TARGET_CHESTS = {
			// 村庄各类箱子
			new Identifier("minecraft", "chests/village/village_plains_house"),
			new Identifier("minecraft", "chests/village/village_savanna_house"),
			new Identifier("minecraft", "chests/village/village_snowy_house"),
			new Identifier("minecraft", "chests/village/village_desert_house"),
			new Identifier("minecraft", "chests/village/village_taiga_house"),
			new Identifier("minecraft", "chests/village/village_fisher"),
			new Identifier("minecraft", "chests/village/village_armorer"),
			new Identifier("minecraft", "chests/village/village_butcher"),
			new Identifier("minecraft", "chests/village/village_cartographer"),
			new Identifier("minecraft", "chests/village/village_mason"),
			new Identifier("minecraft", "chests/village/village_shepherd"),
			new Identifier("minecraft", "chests/village/village_tannery"),
			new Identifier("minecraft", "chests/village/village_temple"),
			new Identifier("minecraft", "chests/village/village_toolsmith"),
			new Identifier("minecraft", "chests/village/village_weaponsmith"),
			// 地下城 / 废弃矿井 / 雪屋 / 林地府邸 / 废弃传送门
			new Identifier("minecraft", "chests/simple_dungeon"),
			new Identifier("minecraft", "chests/abandoned_mineshaft"),
			new Identifier("minecraft", "chests/igloo_chest"),
			new Identifier("minecraft", "chests/woodland_mansion"),
			new Identifier("minecraft", "chests/ruined_portal"),
			// 沉船 / 埋藏的宝藏 / 水下遗迹
			new Identifier("minecraft", "chests/shipwreck_supply"),
			new Identifier("minecraft", "chests/shipwreck_treasure"),
			new Identifier("minecraft", "chests/buried_treasure"),
			new Identifier("minecraft", "chests/underwater_ruin_small"),
			new Identifier("minecraft", "chests/underwater_ruin_big"),
			// 要塞 / 古城 / 堡垒遗迹 / 末地城
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
			// 5% 概率触发；触发后先抽法术、再抽等级（权重两层级联）
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(CHANCE));
			for (int s = 0; s < SPELL_PATHS.length; s++) {
				for (int level = 1; level <= LEVEL_WEIGHTS.length; level++) {
					pool.with(scrollEntry(SPELL_PATHS[s], level, LEVEL_WEIGHTS[level - 1] * SPELL_WEIGHTS[s]));
				}
			}
			tableBuilder.pool(pool);
		});
	}

	// 1.20.1 中 SetNbtLootFunction.builder(NbtCompound) 是唯一可用重载（@Deprecated 但无替代，同 StoryBookLoot）
	@SuppressWarnings("deprecation")
	private static net.minecraft.loot.entry.LootPoolEntry.Builder<?> scrollEntry(String spellPath, int level, int weight) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString(ScrollData.NBT_SPELL, spellPath);
		// 单独使用次数按等级对应品质上限（如白8/绿6/蓝4/紫2/橙1）
		net.jackcooper.shapeShifterCurseAddon.spell.Spell spell = SpellRegistry.get(spellPath);
		nbt.putInt(ScrollData.NBT_USES, spell == null ? 8 : spell.getRarity(level).soloUses);
		nbt.putInt(ScrollData.NBT_LEVEL, level);
		return ItemEntry.builder(SscAddon.MAGIC_SCROLL)
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

	/** 供命令调试：生成一张指定等级的冰锥卷轴。 */
	public static ItemStack createFrostSpikeScroll(int level) {
		return ScrollData.create("frost_spike", level);
	}
}
