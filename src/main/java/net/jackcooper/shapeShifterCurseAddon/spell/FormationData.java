package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * 增强法阵物品的 NBT 数据读写工具（jackcooper）。法阵 NBT：
 * <ul>
 *   <li>{@code Element}（String）：系别 id（fire / ice）；</li>
 *   <li>{@code Level}（int）：法阵等级（1-5，品质白/绿/蓝/紫/橙与卷轴一致）。</li>
 * </ul>
 *
 * <p>法阵物品链：宝箱开出（1-3 级）→ 右键「记录魔法」（存玩家数据，不依赖物品）→
 * 法术研究台消耗月尘学习 → 研究台消耗空白法阵纸 + 对应系油墨抄写实体法阵 →
 * 注魔台五角星装入魔法书生效。</p>
 */
public final class FormationData {
	public static final String NBT_ELEMENT = "Element";
	public static final String NBT_LEVEL = "Level";

	/** 法阵等级上限。 */
	public static final int MAX_FORMATION_LEVEL = 5;

	/** 每级数值：同系伤 +12%、对立系伤 -12%、同系 cd -5%、全魔法耗蓝 +10%；空间法阵对空间魔法距离 +6%/级。 */
	public static final float DAMAGE_BONUS_PER_LEVEL = 0.12f;
	public static final float DAMAGE_PENALTY_PER_LEVEL = 0.12f;
	public static final float COOLDOWN_REDUCTION_PER_LEVEL = 0.05f;
	public static final float MANA_COST_PER_LEVEL = 0.10f;
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

	/** 新建一个指定系别、等级的法阵（用于创造物品栏 / 抄写产出）。 */
	public static ItemStack create(FormationElement element, int level) {
		ItemStack stack = new ItemStack(net.jackcooper.shapeShifterCurseAddon.SscAddon.FORMATION);
		stack.getOrCreateNbt().putString(NBT_ELEMENT, element.id);
		int lv = Math.max(1, Math.min(MAX_FORMATION_LEVEL, level == 0 ? 1 : level));
		stack.getOrCreateNbt().putInt(NBT_LEVEL, lv);
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
	 * 汇总全部法阵对「指定系别魔法」的冷却倍率。
	 * 对立对（火冰/月诅/召虚）同系每级 -5%；空间系法阵只对空间系魔法生效（每级 -5%）；
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

	/** 汇总全部法阵对「全魔法」的法力消耗倍率（每级 +10%，不封顶——这就是叠加的代价）。 */
	public static float sumManaCostMultiplier(ItemStack book) {
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null || element == FormationElement.UNIVERSAL) {
				continue; // 通用系不增加耗蓝
			}
			total += MANA_COST_PER_LEVEL * getLevel(formation);
		}
		return 1f + total;
	}

	// ---- 通用系：形态能量 → 书法术值转化（数值定义） ----

	/** 通用法阵每秒消耗的形态能量点数。 */
	public static final double UNIVERSAL_MANA_DRAIN_PER_SEC = 3.0;
	/** 通用法阵每秒回复的书法术值点数。 */
	public static final double UNIVERSAL_BOOK_MANA_PER_SEC = 6.0;
	/** 通用法阵触发水位（书法术值占比）按等级插值：Lv1=20% … Lv5=100%。 */
	public static double universalThreshold(int level) {
		return 0.2 + 0.2 * (Math.max(1, Math.min(MAX_FORMATION_LEVEL, level)) - 1);
	}
}
