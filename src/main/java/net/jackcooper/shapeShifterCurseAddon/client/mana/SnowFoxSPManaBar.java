package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

/** SP 雪狐霜寒条（80×5）：按近战/远程切换状态选用两套贴图。 */
@Environment(EnvType.CLIENT)
public class SnowFoxSPManaBar extends SimpleResourceBarRenderer {
	// 近战形态纹理
	private static final Identifier MELEE_FULL = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_melee_full.png");
	private static final Identifier MELEE_EMPTY = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_melee_empty.png");
	// 远程形态纹理
	private static final Identifier RANGED_FULL = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_ranged_full.png");
	private static final Identifier RANGED_EMPTY = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_ranged_empty.png");

	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
	// 近战/远程切换状态：0=近战，1=远程
	private static final Identifier SWITCH_STATE_ID = new Identifier("my_addon", "form_snow_fox_sp_switch_state");

	@Override
	protected Identifier resourceId() {
		return RESOURCE_ID;
	}

	@Override
	protected Identifier texFull() {
		return isRanged() ? RANGED_FULL : MELEE_FULL;
	}

	@Override
	protected Identifier texEmpty() {
		return isRanged() ? RANGED_EMPTY : MELEE_EMPTY;
	}

	private static boolean isRanged() {
		return PowerUtils.getClientResourceValue(
				net.minecraft.client.MinecraftClient.getInstance().player, SWITCH_STATE_ID) == 1;
	}
}
