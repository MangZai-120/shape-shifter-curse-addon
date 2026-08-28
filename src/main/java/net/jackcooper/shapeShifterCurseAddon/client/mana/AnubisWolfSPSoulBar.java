package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

/** 阿努比斯之狼 SP 灵魂能量条（80×5）。 */
@Environment(EnvType.CLIENT)
public class AnubisWolfSPSoulBar extends SimpleResourceBarRenderer {
	private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/anubis_wolf_sp_soul_bar_full.png");
	private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/anubis_wolf_sp_soul_bar_empty.png");
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_anubis_wolf_sp_soul_energy");

	@Override
	protected Identifier resourceId() {
		return RESOURCE_ID;
	}

	@Override
	protected Identifier texFull() {
		return TEX_FULL;
	}

	@Override
	protected Identifier texEmpty() {
		return TEX_EMPTY;
	}
}
