package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Spider3;

/**
 * 月织蛛（Moon Weaver）- 蜘蛛SP形态
 * 继承原版蜘蛛三阶段（spider_3），复用其全部动画与行为（爬墙、吐丝、搭桥、毒素免疫、夜视、节肢生物组等），
 * 被动与特性完全与原版 spider_3 平齐。
 * 进化途径：玩家处于 shape-shifter-curse:spider_3 时，于诅咒之月夜晚使用月髓环进化获得。
 */
public class Form_SpiderMoonWeaver extends Form_Spider3 {
	public Form_SpiderMoonWeaver(Identifier formID) {
		super(formID);
	}
}
