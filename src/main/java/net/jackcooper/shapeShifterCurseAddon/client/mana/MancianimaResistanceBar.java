package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.ManaBarPos;

/**
 * 契灵 - 抗伤值条 HUD。
 * 设计：
 *  - 与 mana 条中心对齐（mana 条宽度 80）。
 *  - 长度 = FULL_WIDTH * current/max（线性），current=0 时不显示。
 *  - 位置紧贴 mana 条上方 2 像素。
 *  - 仅在契灵形态显示（max>1 即代表持有该 power）。
 */
@Environment(EnvType.CLIENT)
public class MancianimaResistanceBar extends SimpleResourceBarRenderer {
	private static final Identifier BAR_FULL = new Identifier("my_addon", "textures/gui/mancianima_resistance_full.png");
	private static final Identifier BAR_EMPTY = new Identifier("my_addon", "textures/gui/mancianima_resistance_empty.png");
	private static final int MANA_WIDTH = 80;
	private static final int FULL_WIDTH = 92;
	private static final int CENTER_OFFSET_X = -(FULL_WIDTH - MANA_WIDTH) / 2; // -6，在 mana 条上居中

	@Override
	protected Identifier resourceId() {
		return FormIdentifiers.MANCIANIMA_RESISTANCE;
	}

	@Override
	protected Identifier texFull() {
		return BAR_FULL;
	}

	@Override
	protected Identifier texEmpty() {
		return BAR_EMPTY;
	}

	@Override
	protected int width() {
		return FULL_WIDTH;
	}

	/** 与 mana 条同 y（复用 offsetY=0 的布局参数），X 额外做居中偏移。 */
	@Override
	protected int[] manaBarPosArgs() {
		return ManaBarPos.get(8, 100, 0);
	}

	@Override
	protected int barX(Pair<Integer, Integer> pos) {
		return pos.getLeft() + CENTER_OFFSET_X;
	}
}
