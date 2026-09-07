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

	/** 每级数值：同系伤 +12%、对立系伤 -12%、同系 cd -5%、全魔法耗蓝 +10%。 */
	public static final float DAMAGE_BONUS_PER_LEVEL = 0.12f;
	public static final float DAMAGE_PENALTY_PER_LEVEL = 0.12f;
	public static final float COOLDOWN_REDUCTION_PER_LEVEL = 0.05f;
	public static final float MANA_COST_PER_LEVEL = 0.10f;

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

	// ---- 施法数值结算（服务端 SpellCastManager 调用） ----

	/**
	 * 汇总魔法书内全部法阵对「指定系别魔法」的伤害倍率。
	 * 同系每级 +12%、对立系每级 -12%，正负抵消后总体 clamp ≥ 0。
	 */
	public static float sumDamageMultiplier(ItemStack book, boolean spellIsIce) {
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null) {
				continue;
			}
			boolean formationIsIce = element == FormationElement.ICE;
			if (formationIsIce == spellIsIce) {
				total += DAMAGE_BONUS_PER_LEVEL * getLevel(formation);
			} else {
				total -= DAMAGE_PENALTY_PER_LEVEL * getLevel(formation);
			}
		}
		return Math.max(0f, 1f + total);
	}

	/** 汇总全部法阵对「指定系别魔法」的冷却倍率（同系每级 -5%，最低 0.2 倍防极端）。 */
	public static float sumCooldownMultiplier(ItemStack book, boolean spellIsIce) {
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null) {
				continue;
			}
			boolean formationIsIce = element == FormationElement.ICE;
			if (formationIsIce == spellIsIce) {
				total -= COOLDOWN_REDUCTION_PER_LEVEL * getLevel(formation);
			}
		}
		return Math.max(0.2f, 1f + total);
	}

	/** 汇总全部法阵对「全魔法」的法力消耗倍率（每级 +10%，不封顶——这就是叠加的代价）。 */
	public static float sumManaCostMultiplier(ItemStack book) {
		float total = 0f;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			FormationElement element = getElement(formation);
			if (element == null) {
				continue;
			}
			total += MANA_COST_PER_LEVEL * getLevel(formation);
		}
		return 1f + total;
	}
}
