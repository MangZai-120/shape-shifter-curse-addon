package net.jackcooper.shapeShifterCurseAddon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

/**
 * 吸血蝙蝠形态 - 雾血资源条 HUD。
 * 仅在玩家拥有雾血资源（即处于吸血蝙蝠形态）时渲染，复用暗色调灵魂条贴图契合吸血主题。
 * 位置复用魔力条配置，吸血蝙蝠形态无魔力条，两者不冲突。
 */
@Environment(EnvType.CLIENT)
public class BatDesmodusBloodBar extends SimpleResourceBarRenderer {
	private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/bat_desmodus_blood_bar_full.png");
	private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/bat_desmodus_blood_bar_empty.png");
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_bat_desmodus_blood_resource");

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
