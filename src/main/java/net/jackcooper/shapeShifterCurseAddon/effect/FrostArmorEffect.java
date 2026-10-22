package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** 独立计时。清理由 LivingEntity 的实际移除钩子负责，避免刷新效果时的 onRemoved 重算误清护盾。 */
public final class FrostArmorEffect extends StatusEffect {
    public FrostArmorEffect() { super(StatusEffectCategory.BENEFICIAL, 0x80d8ff); }
}
