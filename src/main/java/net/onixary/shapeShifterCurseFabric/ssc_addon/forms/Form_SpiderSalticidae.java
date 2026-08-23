package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Spider3;

/**
 * 跳蛛（Salticidae / Jumping Spider）- 蜘蛛SP形态
 * 继承原版蜘蛛三阶段（spider_3），复用其全部动画与行为（爬墙、吐丝、搭桥、毒素免疫、夜视、节肢生物组等），
 * 被动与特性完全与原版 spider_3 平齐。
 * 进化途径：玩家处于 shape-shifter-curse:spider_3 时使用进化石进化获得（与月髓环→月织蛛并行，不同道具不冲突）。
 * 定位：弹跳捕猎型——现实跳蛛不结网、主动扑猎、视力极佳、弹跳力惊人（专属技能后续设计）。
 */
public class Form_SpiderSalticidae extends Form_Spider3 {
	public Form_SpiderSalticidae(Identifier formID) {
		super(formID);
	}
}
