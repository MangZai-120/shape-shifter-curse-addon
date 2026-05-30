package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Bat3;

/**
 * 吸血蝙蝠（Desmodus）- 蝙蝠SP形态
 * 继承原版蝙蝠三阶段（bat_3），复用其全部动画与行为（飞行、攀爬、倒挂、缓降等），
 * 围绕「雾血 + 幽雾化形」展开的吸血型形态。
 * 进化途径：玩家处于 shape-shifter-curse:bat_3 时，于诅咒之月夜晚使用月髓环进化获得。
 */
public class Form_BatDesmodus extends Form_Bat3 {
	public Form_BatDesmodus(Identifier formID) {
		super(formID);
	}
}
