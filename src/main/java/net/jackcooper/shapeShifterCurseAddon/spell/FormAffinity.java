package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 形态亲和（jackcooper，2026-09）：当前形态对魔法书施法的定向加成乘区。
 * 服务端调用（SpellCastManager 结算处），多人天然一致。
 *
 * <p>映射表（全部为乘法乘区，与法阵 ±12% 独立叠加）：</p>
 * <ul>
 *   <li>雪狐/寒棘狐 → 冰系伤害 ×1.15</li>
 *   <li>月织蛛 → 月辉系伤害 ×1.15</li>
 *   <li>堕灵（堕落悦灵） → 诅咒系伤害 ×1.15</li>
 *   <li>金沙岚 → 火系伤害 ×1.15</li>
 *   <li>使魔系（SP/进化/红狐/契灵） → 全系耗蓝 ×0.85</li>
 *   <li>悦灵 SP → 月辉系 CD ×0.9</li>
 *   <li>风灵 → 空间系 CD ×0.9</li>
 *   <li>荧光幼灵/阿澪 → 召唤系魔法等级视作 +1（月灵寿命/数量受益）</li>
 *   <li>朔望 → 通用法阵转化效率 3 耗 4 回（见 UniversalFormationManager 分支）</li>
 * </ul>
 */
public final class FormAffinity {
	private FormAffinity() {
	}

	/** 亲和伤害乘区（无亲和返回 1）。 */
	public static float damageMultiplier(ServerPlayerEntity player, FormationElement element) {
		if (player == null || element == null) {
			return 1f;
		}
		// 冰系亲和：雪狐 / 寒棘狐
		if (element == FormationElement.ICE
				&& (FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_SP)
				|| FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_FROSTSPINE))) {
			return 1.15f;
		}
		// 月辉系亲和：月织蛛
		if (element == FormationElement.LUNAR
				&& FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)) {
			return 1.15f;
		}
		// 诅咒系亲和：堕灵（堕落悦灵）
		if (element == FormationElement.CURSE
				&& FormUtils.isForm(player, FormIdentifiers.FALLEN_ALLAY_SP)) {
			return 1.15f;
		}
		// 火系亲和：金沙岚
		if (element == FormationElement.FIRE
				&& FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			return 1.15f;
		}
		return 1f;
	}

	/** 亲和耗蓝乘区（无亲和返回 1；使魔系全系 ×0.85）。 */
	public static float manaCostMultiplier(ServerPlayerEntity player) {
		if (player == null) {
			return 1f;
		}
		if (FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_SP)
				|| FormUtils.isForm(player, FormIdentifiers.UPGRADE_FAMILIAR_FOX)
				|| FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED)
				|| FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			return 0.85f;
		}
		return 1f;
	}

	/** 亲和冷却乘区（无亲和返回 1；悦灵 SP 月辉 ×0.9、风灵空间 ×0.9）。 */
	public static float cooldownMultiplier(ServerPlayerEntity player, FormationElement element) {
		if (player == null || element == null) {
			return 1f;
		}
		if (element == FormationElement.LUNAR
				&& FormUtils.isForm(player, FormIdentifiers.ALLAY_SP)) {
			return 0.9f;
		}
		if (element == FormationElement.SPACE
				&& FormUtils.isForm(player, FormIdentifiers.OCELOT_SP)) {
			return 0.9f;
		}
		return 1f;
	}

	/** 召唤系亲和：荧光幼灵/阿澪召唤魔法等级 +1（上限 5）。 */
	public static int bonusSpellLevel(ServerPlayerEntity player, FormationElement element, int level) {
		if (player != null && element == FormationElement.SUMMON
				&& (FormUtils.isForm(player, FormIdentifiers.AXOLOTL_FLUORESCENT)
				|| FormUtils.isForm(player, FormIdentifiers.AXOLOTL_ALING))) {
			return Math.min(5, level + 1);
		}
		return level;
	}
}
