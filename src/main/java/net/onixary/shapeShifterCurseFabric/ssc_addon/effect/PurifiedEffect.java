package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 净化标记效果 - SP悦灵净化技能施加的短暂标记
 * 用于通知Java端技能（如冰霜风暴蓄力、闪现攻击）被打断
 * 效果本身无任何属性修改，仅作为信号使用
 * 持续时间极短（1秒），仅用于在下一个tick被Java代码检测到
 */
public class PurifiedEffect extends StatusEffect {
    
    public PurifiedEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x99DDFF); // Light blue color
    }
    
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false; // No update effect needed, just a marker
    }
}
