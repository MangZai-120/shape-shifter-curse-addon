package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * 增强法阵物品的 NBT 数据读写工具（jackcooper）。法阵 NBT：
 * <ul>
 *   <li>{@code Element}（String）：系别 id（fire / ice）；</li>
 *   <li>{@code Level}（int）：法阵等级（1-5，品质白/绿/蓝/紫/橙与卷轴一致）；</li>
 *   <li>{@code Variant}（String，仅通用系）：变体 id（regen 回能 / mana 增能 / exp 经验；
 *       缺省 = regen，旧档通用法阵自动归回能变体）。</li>
 * </ul>
 *
 * <p>法阵物品链：宝箱开出（1-3 级）→ 右键「记录魔法」（存玩家数据，不依赖物品）→
 * 法术研究台消耗月尘学习 → 研究台消耗空白法阵纸 + 对应系油墨抄写实体法阵 →
 * 注魔台五角星装入魔法书生效。</p>
 */
public final class FormationData {
	public static final String NBT_ELEMENT = "Element";
	public static final String NBT_LEVEL = "Level";
	/** 通用系变体 NBT 键（仅 UNIVERSAL 系法阵有意义；缺省 = regen，兼容旧档）。 */
	public static final String NBT_VARIANT = "Variant";

	/** 法阵等级上限。 */
	public static final int MAX_FORMATION_LEVEL = 5;

	/** 每级数值（2026-09-20 重定稿）：同系伤 +12%、对立系伤 -12%、同系 cd -5%、对立系 cd +5%；
	 * 耗蓝：同系与对立系 +10%/级、其它系仅 +2.5%/级（1/4）；空间法阵对空间魔法距离 +6%/级。 */
	public static final float DAMAGE_BONUS_PER_LEVEL = 0.12f;
	public static final float DAMAGE_PENALTY_PER_LEVEL = 0.12f;
	public static final float COOLDOWN_REDUCTION_PER_LEVEL = 0.05f;
	/** 对立系冷却惩罚（2026-09-20 新增）：对立系魔法 CD 每级 +5%（与同系缩减对称）。 */
	public static final float COOLDOWN_PENALTY_PER_LEVEL = 0.05f;
	public static final float MANA_COST_PER_LEVEL = 0.10f;
	/** 其它系（非同系非对立）耗蓝代价（2026-09-20）：同系的 1/4，即 +2.5%/级。 */
	public static final float MANA_COST_OTHER_ELEMENT_PER_LEVEL = MANA_COST_PER_LEVEL / 4f;
	public static final float SPACE_RANGE_BONUS_PER_LEVEL = 0.06f;

	private FormationData() {
	}

	/** 读取法阵系别（无绑定返回 null）。 */
	public static FormationElement getElement(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_ELEMENT)) {
			return null;
		}
		return FormationElement.byId(nbt.getString(NBT_ELEMENT));
	}

	/** 是否为增强法阵（绑定了有效系别）。 */
	public static boolean isFormation(ItemStack stack) {
		return getElement(stack) != null;
	}

	/** 法阵等级（1-5；缺省 1，兼容旧存档）。 */
	public static int getLevel(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(NBT_LEVEL)) {
			return Math.max(1, Math.min(MAX_FORMATION_LEVEL, nbt.getInt(NBT_LEVEL)));
		}
		return 1;
	}

	/** 写入法阵等级。 */
	public static void setLevel(ItemStack stack, int level) {
		stack.getOrCreateNbt().putInt(NBT_LEVEL, Math.max(1, Math.min(MAX_FORMATION_LEVEL, level)));
	}

	/** 等级对应品质（与卷轴一致：白/绿/蓝/紫/橙）。 */
	public static SpellRarity getRarity(int level) {
		return switch (level) {
			case 2 -> SpellRarity.GREEN;
			case 3 -> SpellRarity.BLUE;
			case 4 -> SpellRarity.PURPLE;
			case 5 -> SpellRarity.ORANGE;
			default -> SpellRarity.WHITE;
		};
	}

	/** 新建一个指定系别、等级的法阵（用于创造物品栏 / 抄写产出；variant 仅通用系有效）。 */
	public static ItemStack create(FormationElement element, int level) {
		return create(element, level, null);
	}

	/** 新建一个指定系别、等级、变体的法阵（variant 仅通用系合法，非法时归 regen）。 */
	public static ItemStack create(FormationElement element, int level, String variant) {
		ItemStack stack = new ItemStack(net.jackcooper.shapeShifterCurseAddon.SscAddon.FORMATION);
		stack.getOrCreateNbt().putString(NBT_ELEMENT, element.id);
		int lv = Math.max(1, Math.min(MAX_FORMATION_LEVEL, level == 0 ? 1 : level));
		stack.getOrCreateNbt().putInt(NBT_LEVEL, lv);
		if (element == FormationElement.UNIVERSAL) {
			String v = normalizeVariant(variant);
			stack.getOrCreateNbt().putString(NBT_VARIANT, v != null ? v : VARIANT_REGEN);
		}
		return stack;
	}

	// ---- 施法数值结算（服务端 SpellCastManager 调用；2026-09 签名重构为元素参数，支持多对立对） ----

	/**
	 * 汇总魔法书内全部法阵对「指定系别魔法」的伤害倍率。
	 * 同系每级 +12%、对立系每级 -12%，正负抵消后总体 clamp ≥ 0。
	 * 通用/空间系法阵不参与（空间走专属 cd/距离加成）。
	 */
	public static float sumDamageMultiplier(ItemStack book, FormationElement spellElement) {
		if (spellElement == null || spellElement == FormationElement.UNIVERSAL
				|| spellElement == FormationElement.SPACE) {
			return 1f;
		}
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null || element == FormationElement.UNIVERSAL || element == FormationElement.SPACE) {
				continue;
			}
			if (element == spellElement) {
				total += DAMAGE_BONUS_PER_LEVEL * getLevel(formation);
			} else if (element == spellElement.opponent()) {
				total -= DAMAGE_PENALTY_PER_LEVEL * getLevel(formation);
			}
		}
		return Math.max(0f, 1f + total);
	}

	/**
	 * 汇总全部法阵对「指定系别魔法」的冷却倍率（2026-09-20 重定稿）。
	 * 同系每级 -5%；对立系每级 +5%（新增对称惩罚，仅火冰/月诅/召虚三组对立对）；
	 * 空间系法阵只对空间系魔法生效（每级 -5%，空间无对立不惩罚）；
	 * 最低 0.2 倍防极端。
	 */
	public static float sumCooldownMultiplier(ItemStack book, FormationElement spellElement) {
		if (spellElement == null) {
			return 1f;
		}
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null || element == FormationElement.UNIVERSAL) {
				continue;
			}
			boolean isSpacePair = element == FormationElement.SPACE && spellElement == FormationElement.SPACE;
			if (element == spellElement || isSpacePair) {
				total -= COOLDOWN_REDUCTION_PER_LEVEL * getLevel(formation);
			} else if (element == spellElement.opponent()) {
				total += COOLDOWN_PENALTY_PER_LEVEL * getLevel(formation);
			}
		}
		return Math.max(0.2f, 1f + total);
	}

	/** 空间系法阵对「空间系魔法施法距离」的加成倍率（每级 +6%，仅空间法阵且仅空间魔法生效）。 */
	public static float sumSpaceRangeMultiplier(ItemStack book, FormationElement spellElement) {
		if (spellElement != FormationElement.SPACE) {
			return 1f;
		}
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == FormationElement.SPACE) {
				total += SPACE_RANGE_BONUS_PER_LEVEL * getLevel(formation);
			}
		}
		return 1f + total;
	}

	/**
	 * 汇总全部法阵对「指定系别魔法」的法力消耗倍率（2026-09-20 重定稿）。
	 * 同系 +10%/级、对立系 +10%/级、其它系仅 +2.5%/级（1/4）——不再全系等价；
	 * 通用系法阵不增加耗蓝；空间法阵对非空间魔法按其它系 +2.5%（空间无对立）。
	 * 多枚叠加不封顶。
	 */
	public static float sumManaCostMultiplier(ItemStack book, FormationElement spellElement) {
		return sumManaCostMultiplierNbt(book.getNbt(), spellElement);
	}

	/** NBT 版法力消耗倍率（法阵条目从书 NBT Formations 直读，与 ItemStack 版同式）。 */
	public static float sumManaCostMultiplierNbt(NbtCompound bookNbt, FormationElement spellElement) {
		if (spellElement == null) {
			return 1f;
		}
		float total = 0f;
		for (NbtCompound entry : SpellbookData.getFormationEntriesNbt(bookNbt)) {
			if (!entry.contains(NBT_ELEMENT)) {
				continue;
			}
			FormationElement element = FormationElement.byId(entry.getString(NBT_ELEMENT));
			if (element == null || element == FormationElement.UNIVERSAL) {
				continue; // 通用系不增加耗蓝
			}
			if (element == spellElement || element == spellElement.opponent()) {
				total += MANA_COST_PER_LEVEL * entryLevelNbt(entry);
			} else {
				total += MANA_COST_OTHER_ELEMENT_PER_LEVEL * entryLevelNbt(entry);
			}
		}
		return 1f + total;
	}

	/** 法阵条目 NBT 的等级（1-5；缺省 1，与 getLevel 同式）。 */
	static int entryLevelNbt(NbtCompound entry) {
		if (entry.contains(NBT_LEVEL)) {
			return Math.max(1, Math.min(MAX_FORMATION_LEVEL, entry.getInt(NBT_LEVEL)));
		}
		return 1;
	}

	// ---- 通用系：形态能量 → 书法术值转化（数值定义） ----

	/** 通用法阵每秒消耗的形态能量点数。 */
	// 回能法阵汇率（2026-09-17 用户定稿统一 5:1：1 形态 mana = 5 书法术值）
	public static final double UNIVERSAL_MANA_DRAIN_PER_SEC = 2.0;
	/** 通用法阵每秒回复的书法术值点数。 */
	public static final double UNIVERSAL_BOOK_MANA_PER_SEC = 10.0;
	/** 通用法阵触发水位（书法术值占比）按等级插值：Lv1=20% … Lv5=100%。 */
	public static double universalThreshold(int level) {
		return 0.2 + 0.2 * (clampFormationLevel(level) - 1);
	}

	// ---- 通用系三变体：回能（转化）/ 增能（法力上限）/ 经验（exp 效率）（2026-09-15 拆分） ----

	/** 通用系变体 id。 */
	public static final String VARIANT_REGEN = "regen";
	public static final String VARIANT_MANA = "mana";
	public static final String VARIANT_EXP = "exp";
	/** 回息变体（2026-09-17）：提升书法术值自然回复量，每级 +20%，多张可叠加。 */
	public static final String VARIANT_RECOVERY = "recovery";

	/** 通用法阵变体合法性校验（非法/空归 null）。 */
	public static String normalizeVariant(String s) {
		return switch (s == null ? "" : s) {
			case VARIANT_REGEN, VARIANT_MANA, VARIANT_EXP, VARIANT_RECOVERY -> s;
			default -> null;
		};
	}

	/** 读取通用法阵变体（非通用系返回 null；通用系缺省/非法回退 regen——旧档兼容）。 */
	public static String getVariant(ItemStack stack) {
		if (getElement(stack) != FormationElement.UNIVERSAL) {
			return null;
		}
		NbtCompound nbt = stack.getNbt();
		String v = (nbt != null && nbt.contains(NBT_VARIANT)) ? nbt.getString(NBT_VARIANT) : null;
		return normalizeVariant(v) != null ? normalizeVariant(v) : VARIANT_REGEN;
	}

	/** 书内指定变体等级最高的通用法阵（0 = 未装该变体；三种效果均取最高不叠加）。 */
	public static int getBestUniversalVariantLevel(ItemStack book, String variant) {
		String v = normalizeVariant(variant);
		if (v == null) {
			return 0;
		}
		int best = 0;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			if (getElement(formation) == FormationElement.UNIVERSAL && v.equals(getVariant(formation))) {
				best = Math.max(best, getLevel(formation));
			}
		}
		return best;
	}

	/** 经验法阵：施法经验倍率每级 +10%（Lv1=×1.1 … Lv5=×1.5）。 */
	public static final float UNIVERSAL_EXP_PER_LEVEL = 0.10f;
	/** 增能法阵：法力上限比例 Lv1=+20% 起每级 +10%（Lv5=+60%）。 */
	public static final float UNIVERSAL_MANA_CAP_BASE = 0.20f;

	/** 经验法阵施法经验倍率。 */
	public static float universalExpMultiplier(int level) {
		return 1f + UNIVERSAL_EXP_PER_LEVEL * clampFormationLevel(level);
	}

	/** 回息变体（可叠加，区别于其它三变体取最高）：书内全部回息法阵等级总和（0 = 未装）。 */
	public static int sumUniversalRecoveryLevels(ItemStack book) {
		int total = 0;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			if (getElement(formation) == FormationElement.UNIVERSAL
					&& VARIANT_RECOVERY.equals(getVariant(formation))) {
				total += getLevel(formation);
			}
		}
		return total;
	}

	/** 回息变体：书法术值自然回复量倍率（每级 +20%，可叠加；总和 0 → ×1）。 */
	public static float universalRecoveryMultiplier(ItemStack book) {
		return 1f + 0.20f * sumUniversalRecoveryLevels(book);
	}

	/** 增能法阵法力上限加成比例（基于书等级基础值计算）。 */
	public static float universalManaBonusPct(int level) {
		return UNIVERSAL_MANA_CAP_BASE + 0.10f * (clampFormationLevel(level) - 1);
	}

	/** 等级收敛到 [1, MAX_FORMATION_LEVEL]。 */
	private static int clampFormationLevel(int level) {
		return Math.max(1, Math.min(MAX_FORMATION_LEVEL, level));
	}
}
