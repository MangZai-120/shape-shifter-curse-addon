package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.Formatting;

/**
 * 增强法阵的元素系别（jackcooper）。
 *
 * <p>法阵按系别影响魔法书施法数值：同系魔法伤害 +12%/级、cd -5%/级；
 * <b>对立系</b>魔法伤害 -12%/级（对称惩罚）；全魔法法力消耗 +10%/级（叠加制衡）。
 * 火与冰互为对立系（对应火系/冰系法阵油墨）。</p>
 */
public enum FormationElement {
	/** 火系（对立：冰）。 */
	FIRE("fire", 0xFF6A28, Formatting.GOLD),
	/** 冰系（对立：火）。 */
	ICE("ice", 0x5AC8E8, Formatting.AQUA);

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

	/** 对立系。 */
	public FormationElement opponent() {
		return this == FIRE ? ICE : FIRE;
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
