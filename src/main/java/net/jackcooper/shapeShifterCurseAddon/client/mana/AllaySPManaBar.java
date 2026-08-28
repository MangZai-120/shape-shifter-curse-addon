package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

/** SP 悦灵魔力条（80×5，空/满双层贴图按百分比裁剪）。 */
@Environment(EnvType.CLIENT)
public class AllaySPManaBar extends SimpleResourceBarRenderer {
	private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_full.png");
	private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_empty.png");
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");

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
