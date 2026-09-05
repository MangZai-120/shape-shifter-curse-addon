package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.Formatting;

/**
 * 魔法卷轴稀有度（jackcooper）。
 *
 * <p>决定卷轴<b>单独使用</b>（不放入魔法书直接右键释放）时的可用次数与名称颜色。
 * 放入魔法书后不受次数限制（只消耗魔法书的法力），但魔法效果会按卷轴当前剩余次数比例缩放
 * （满次数=正常；用过则按剩余比例衰减，见 {@link ScrollData#getDurabilityRatio}）。</p>
 */
public enum SpellRarity {
	/** 白色：单独可用 8 次。 */
	WHITE(8, Formatting.WHITE),
	/** 绿色：单独可用 6 次。 */
	GREEN(6, Formatting.GREEN),
	/** 蓝色：单独可用 4 次。 */
	BLUE(4, Formatting.AQUA),
	/** 紫色：单独可用 2 次。 */
	PURPLE(2, Formatting.LIGHT_PURPLE),
	/** 橙色：单独可用 1 次。 */
	ORANGE(1, Formatting.GOLD),
	/** 红色：不可单独使用，只能放入魔法书。 */
	RED(0, Formatting.RED);

	/** 单独使用次数上限（0 表示禁止单独使用）。 */
	public final int soloUses;
	/** 名称显示颜色。 */
	public final Formatting color;

	SpellRarity(int soloUses, Formatting color) {
		this.soloUses = soloUses;
		this.color = color;
	}

	/** 是否允许单独使用（红色不可）。 */
	public boolean canUseSolo() {
		return soloUses > 0;
	}

	/** 稀有度名 lang key：rarity.ssc_addon.&lt;name&gt;。 */
	public String getTranslationKey() {
		return "rarity.ssc_addon." + name().toLowerCase();
	}
}
