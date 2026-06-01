package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

// 失聪：客机 SoundManagerDeafenMixin 据此静音受影响玩家自身的所有声音
public class DeafenEffect extends StatusEffect {
	public DeafenEffect() {
		super(StatusEffectCategory.HARMFUL, 0x6B5B95);
	}
}
