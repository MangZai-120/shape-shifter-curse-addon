package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.nbt.NbtCompound;

/**
 * 法术最终数值统一结算工具（jackcooper，阶段 B 战斗底座）。
 *
 * <p>目的：冷却的「最终值 = 多乘区 + 双层下限」公式在服务端施法、单独使用、客户端 HUD
 * 三处共用同一实现（计划书 P0「最终数值与面板一致」——同一权威计算结果用于扣费与展示）。</p>
 *
 * <p>冷却公式（计划书 §6.2）：
 * <pre>C_final = max( C_absolute, round(0.2 × C_L), round(C_L × (2 − ρ) × F_formation × F_form) )</pre>
 * 其中 C_L = 基础 CD × 等级倍率（solo 路径再乘 solo 惩罚倍率）；ρ 为卷轴耐久比（满=1）；
 * C_absolute 为法术配置的 {@code cooldown_floor_ticks}（0 = 无绝对下限）。
 * 0.2×C_L 是「相对本等级」的下限，防止法阵/亲和乘区把等级减 CD 的收益整体吃掉。</p>
 */
public final class SpellNumbers {
	private SpellNumbers() {}

	public static int finalManaCost(Spell spell, net.minecraft.item.ItemStack book,
	                               net.minecraft.entity.player.PlayerEntity player, int selectedLevel) {
		int costLevel = FormAffinity.manaCostLevel(player, spell.getElement(), selectedLevel);
		int manaCost = manaCost(spell.getConfig(), costLevel, FormationData.sumManaCostMultiplier(book, spell.getElement()),
				FormAffinity.manaCostMultiplier(player));
		return FormCastingStyle.applyTidalDiscount(player, spell.getElement(), manaCost);
	}

	/** JSON 基础消耗 × 当前施放等级倍率 × 书内法阵倍率 × 形态倍率；-1 表示配置尚不可用。 */
	public static int manaCost(net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig config,
	                           int level, float formationMultiplier, float affinityMultiplier) {
		if (!config.manaCostConfigured || !Float.isFinite(formationMultiplier) || formationMultiplier <= 0
				|| !Float.isFinite(affinityMultiplier) || affinityMultiplier <= 0) return -1;
		int cost = Math.round(config.manaCost * formationMultiplier * affinityMultiplier * config.manaCostMultiplier(level));
		return config.manaCost > 0 ? Math.max(1, cost) : 0;
	}

	/** 两端共用降档选择；只计算临时档位，不修改卷轴 NBT。 */
	public static int highestAffordableLevel(Spell spell, net.minecraft.item.ItemStack book,
	                                         net.minecraft.entity.player.PlayerEntity player, int maxLevel) {
		if (book == null || book.isEmpty()) return 0;
		return highestAffordableLevelNbt(spell, book.getNbt(), player, maxLevel);
	}

	/** NBT 版降档选择（测试环境无注册表时直接验这条链）。 */
	public static int highestAffordableLevelNbt(Spell spell, net.minecraft.nbt.NbtCompound bookNbt,
	                                            net.minecraft.entity.player.PlayerEntity player, int maxLevel) {
		if (bookNbt == null) return 0;
		long available = SpellbookData.getManaNbt(bookNbt, getMaxManaOf(bookNbt));
		return SpellCastingRules.highestAffordableLevel(Math.min(spell.getMaxLevel(), maxLevel), available,
				level -> finalManaCostNbt(spell, bookNbt, player, level));
	}

	/** 从书 NBT 计算法力上限（等级 + 精通档 + 增能法阵，与 SpellbookData.getMaxMana 同式）。 */
	static int getMaxManaOf(NbtCompound bookNbt) {
		return SpellbookData.getMaxManaNbt(bookNbt);
	}

	/** NBT 版最终耗蓝（法阵系别/等级从书 NBT Formations 直读，与 ItemStack 版同式）。 */
	static int finalManaCostNbt(Spell spell, NbtCompound bookNbt,
	                            net.minecraft.entity.player.PlayerEntity player, int selectedLevel) {
		int costLevel = FormAffinity.manaCostLevel(player, spell.getElement(), selectedLevel);
		int manaCost = manaCost(spell.getConfig(), costLevel,
				FormationData.sumManaCostMultiplierNbt(bookNbt, spell.getElement()),
				FormAffinity.manaCostMultiplier(player));
		return FormCastingStyle.applyTidalDiscount(player, spell.getElement(), manaCost);
	}

	/** 相对等级基准的冷却下限倍率（计划书 §6.2：0.2 × C_L）。 */
	public static final float RELATIVE_CD_FLOOR = 0.2f;

	/**
	 * 书内施法的最终冷却（tick）。
	 *
	 * @param spell            法术
	 * @param level            施放等级（已含召唤亲和 +1 后的有效等级；等级倍率按此取）
	 * @param ratio            卷轴耐久比（1=满次数；CD 乘 (2−ratio)）
	 * @param formationCdMul   法阵冷却乘区（{@link FormationData#sumCooldownMultiplier}）
	 * @param affinityCdMul    形态亲和冷却乘区（{@link FormAffinity#cooldownMultiplier}）
	 */
	public static int finalCooldownTicks(Spell spell, int level, float ratio,
	                                     float formationCdMul, float affinityCdMul) {
		if (spell == null) {
			return 0;
		}
		int base = spell.getBaseCooldownTicks();
		float levelCd = base * spell.getCooldownMultiplier(level);   // C_L
		int raw = Math.round(levelCd * (2.0f - ratio) * formationCdMul * affinityCdMul);
		return applyFloors(spell, levelCd, raw);
	}

	/**
	 * 单独使用卷轴的最终冷却（tick）：solo 惩罚倍率并入等级基准（无耐久比项——
	 * solo 每次消耗 1 次数，不按耐久比再缩放，与旧口径一致）。
	 */
	public static int finalSoloCooldownTicks(Spell spell, int level) {
		if (spell == null) {
			return 0;
		}
		float levelCd = spell.getBaseCooldownTicks()
				* spell.getSoloCooldownMultiplier() * spell.getCooldownMultiplier(level);
		int raw = Math.round(levelCd);
		return applyFloors(spell, levelCd, raw);
	}

	/** 应用双层下限：绝对 floor（JSON）与相对等级基准 0.2×C_L。 */
	private static int applyFloors(Spell spell, float levelCd, int raw) {
		int absoluteFloor = spell.getCooldownFloorTicks();
		int relativeFloor = Math.round(levelCd * RELATIVE_CD_FLOOR);
		return Math.max(raw, Math.max(absoluteFloor, relativeFloor));
	}

	/**
	 * 判定最终冷却是否被下限抬升（即乘区缩减收益已被 floor 截断）。
	 * 供「已达冷却下限」提示（计划书 §6.2）——此时相邻等级实际 CD 可能相同。
	 */
	public static boolean isAtFloor(Spell spell, int level, float ratio,
	                                float formationCdMul, float affinityCdMul) {
		if (spell == null) {
			return false;
		}
		float levelCd = spell.getBaseCooldownTicks() * spell.getCooldownMultiplier(level);
		int raw = Math.round(levelCd * (2.0f - ratio) * formationCdMul * affinityCdMul);
		int floor = Math.max(spell.getCooldownFloorTicks(),
				Math.round(levelCd * RELATIVE_CD_FLOOR));
		return floor > raw;
	}
}
