package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.Formatting;

/**
 * 增强法阵的元素系别（jackcooper）。
 *
 * <p>火/冰按系别影响魔法书施法数值：同系魔法伤害 +12%/级、cd -5%/级；
 * <b>对立系</b>魔法伤害 -12%/级（对称惩罚）；全魔法法力消耗 +10%/级（叠加制衡）。
 * 火与冰互为对立系（对应火系/冰系法阵油墨）。</p>
 *
 * <p><b>通用（UNIVERSAL）</b>：不参与伤害/冷却/耗蓝结算，改为「形态能量 → 书法术值」转化
 * （见 {@code UniversalFormationManager}）：书法术值低于阈值（橙 100%/白 20%，按等级插值）时
 * 每秒耗 3 形态能量回 6 法术值；抄写用普通法阵油墨。</p>
 */
public enum FormationElement {
	/** 火系（对立：冰）。 */
	FIRE("fire", 0xFF6A28, Formatting.GOLD),
	/** 冰系（对立：火）。 */
	ICE("ice", 0x5AC8E8, Formatting.AQUA),
	/** 月辉系（对立：诅咒）——净化/治疗/辅助。 */
	LUNAR("lunar", 0xF5EFD5, Formatting.YELLOW),
	/** 诅咒系（对立：月辉）——减益/控场。 */
	CURSE("curse", 0x7B2FBE, Formatting.DARK_PURPLE),
	/** 召唤系（对立：虚无）——协战/增益宠物。 */
	SUMMON("summon", 0x6FD3A5, Formatting.GREEN),
	/** 虚无系（对立：召唤）——侵蚀/削弱。 */
	VOID("void", 0x3A3A5C, Formatting.DARK_GRAY),
	/** 空间系（无对立；不参与 ±12% 伤害，专属法阵只缩减空间系 cd + 施法距离加成）。 */
	SPACE("space", 0xC9E8F5, Formatting.AQUA),
	/** 通用系（不参与伤害/CD/耗蓝；形态能量转化，无对立）。 */
	UNIVERSAL("universal", 0xB8B8B8, Formatting.GRAY);

	/** 系别 id（NBT / lang 前缀用）。 */
	public final String id;
	/** 系别主题色（贴图染色 / GUI 用）。 */
	public final int color;
	/** 系别名颜色（名称显示）。 */
	public final Formatting formatting;

	FormationElement(String id, int color, Formatting formatting) {
		this.id = id;
		this.color = color;
		this.formatting = formatting;
	}

	/** 对立系（通用/空间无对立，返回自身——仅用于 tooltip 文案，实际结算已跳过两者）。 */
	public FormationElement opponent() {
		return switch (this) {
			case FIRE -> ICE;
			case ICE -> FIRE;
			case LUNAR -> CURSE;
			case CURSE -> LUNAR;
			case SUMMON -> VOID;
			case VOID -> SUMMON;
			case SPACE -> SPACE;
			case UNIVERSAL -> UNIVERSAL;
		};
	}

	/** 按 id 解析（null 安全）。 */
	public static FormationElement byId(String id) {
		for (FormationElement e : values()) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** 系别名 lang key：formation.ssc_addon.element.&lt;id&gt;。 */
	public String getNameKey() {
		return "formation.ssc_addon.element." + id;
	}
}
