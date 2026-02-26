package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

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
        return true; 
    }
    
    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

        // Clear all harmful effects (or all effects except this one)
        // Since we are iterating while modifying, we need a copy
        List<StatusEffectInstance> effects = new ArrayList<>(entity.getStatusEffects());
        
        for (StatusEffectInstance instance : effects) {
            // Don't remove self
            if (instance.getEffectType() == this) continue;
            
            // Remove the effect
            // Note: removeStatusEffect returns boolean, doesn't throw concurrent modification if we iterate over a copy
            entity.removeStatusEffect(instance.getEffectType());
        }
    }
}
