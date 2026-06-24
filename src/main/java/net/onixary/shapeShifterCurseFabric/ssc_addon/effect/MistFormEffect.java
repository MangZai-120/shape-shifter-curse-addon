package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 幽雾化形 - 雾化状态标记效果。
 * 作为吸血蝙蝠形态核心技能的状态标记：拥有此效果时玩家化为一团雾，
 * 由数据驱动的 apoli:invulnerability（排除虚空伤害）提供免伤，
 * 由 MistFormAbilityPower 负责粒子、隐身与计时逻辑。
 * 本身不附带任何属性修改，仅作纯粹的状态标记。
 */
public class MistFormEffect extends StatusEffect {
	public MistFormEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0xB0B8C0); // 雾灰色
	}
}
