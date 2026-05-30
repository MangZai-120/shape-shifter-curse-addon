package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 幽雾化形 - 凝聚爆破蓄力标记效果。
 * 蓄力期间附加此标记，客户端据此将化雾飞行速度减半（蓄力减速 50%），
 * 由 MistFormAbilityPower 负责蓄力计时、聚集粒子与最终引爆。
 * 本身不附带任何属性修改，仅作纯粹的状态标记。
 */
public class MistChargingEffect extends StatusEffect {
	public MistChargingEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0xC81E1E); // 血红色
	}
}
