package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 形态亲和（jackcooper，2026-09）：当前形态对魔法书施法的定向加成乘区。
 * 服务端权威结算；耗蓝公式同时供客户端预检使用。
 *
 * <p>映射表（全部为乘法乘区，与法阵 ±12% 独立叠加）：</p>
 * <ul>
 *   <li>雪狐/寒棘狐 → 冰系伤害 ×1.15</li>
 *   <li>月织蛛 → 月辉系伤害 ×1.15</li>
 *   <li>堕落之灵 → 诅咒系持续时间 ×1.15；跳蛛 ×1.10</li>
 *   <li>金沙岚 → 火系伤害 ×1.15</li>
 *   <li>使魔系（SP/进化/红狐/契灵） → 全系耗蓝 ×0.85</li>
 *   <li>悦灵 SP → 月辉系 CD ×0.9</li>
 *   <li>风灵 → 空间系 CD ×0.9</li>
 *   <li>荧光幼灵/阿澪/悦灵 SP/寄生果蝠 → 召唤效果等级 +1，上限 5；五级档按四级费用结算</li>
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
		// 火系亲和：金沙岚
		if (element == FormationElement.FIRE
				&& FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			return 1.15f;
		}
		// 虚无系亲和：食梦魔（噬梦流派的伤害面，命中返还见 FormCastingStyle）
		if (element == FormationElement.VOID
				&& FormUtils.isForm(player, FormIdentifiers.WILD_CAT_NIGHTMARE)) {
			return 1.15f;
		}
		return 1f;
	}

	public static int curseDurationTicks(ServerPlayerEntity player, int ticks) {
		return SpellCastingRules.curseDurationTicks(ticks,
				FormUtils.isForm(player, FormIdentifiers.FALLEN_ALLAY_SP),
				FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE));
	}

	/** 亲和耗蓝乘区（无亲和返回 1；使魔系全系 ×0.85；野猫 SP 全系 ×0.75，用户定稿 2026-09-17）。 */
	public static float manaCostMultiplier(PlayerEntity player) {
		if (player == null) {
			return 1f;
		}
		if (FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_SP)
				|| FormUtils.isForm(player, FormIdentifiers.UPGRADE_FAMILIAR_FOX)
				|| FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED)
				|| FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			return 0.85f;
		}
		// 野猫 SP「猎手本能」：全系耗蓝 ×0.75（与 CD ×0.75 配套）
		if (FormUtils.isForm(player, FormIdentifiers.WILD_CAT_SP)) {
			return 0.75f;
		}
		return 1f;
	}

	/** 亲和冷却乘区（无亲和返回 1；悦灵 SP 月辉 ×0.9、风灵空间 ×0.9；野猫 SP 全系 ×0.75）。 */
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
		// 冰系 CD 亲和：美西螈 SP / 进化美西螈「潮汐」（与雪狐伤害亲和区分；水中耗蓝减免见 FormCastingStyle）
		if (element == FormationElement.ICE
				&& (FormUtils.isForm(player, FormIdentifiers.AXOLOTL_SP)
					|| FormUtils.isForm(player, FormIdentifiers.UPGRADE_AXOLOTL))) {
			return 0.95f;
		}
		// 野猫 SP「猎手本能」：全系 CD ×0.75（与耗蓝 ×0.75 配套）
		if (FormUtils.isForm(player, FormIdentifiers.WILD_CAT_SP)) {
			return 0.75f;
		}
		return 1f;
	}

	/** 召唤系亲和：荧光幼灵/阿澪/悦灵 SP/寄生果蝠召唤魔法等级 +1（上限 5；用户定稿 2026-09-17）。 */
	public static int bonusSpellLevel(PlayerEntity player, FormationElement element, int level) {
		if (player != null && element == FormationElement.SUMMON
				&& (FormUtils.isForm(player, FormIdentifiers.AXOLOTL_FLUORESCENT)
					|| FormUtils.isForm(player, FormIdentifiers.AXOLOTL_ALING)
					|| FormUtils.isForm(player, FormIdentifiers.ALLAY_SP)
					|| FormUtils.isForm(player, FormIdentifiers.BAT_PARASITIC_FRUIT))) {
			return Math.min(5, level + 1);
		}
		return level;
	}

	public static int manaCostLevel(PlayerEntity player, FormationElement element, int level) {
		return element == FormationElement.SUMMON
				? SpellCastingRules.summonManaLevel(level, bonusSpellLevel(player, element, 1) > 1) : level;
	}
}
