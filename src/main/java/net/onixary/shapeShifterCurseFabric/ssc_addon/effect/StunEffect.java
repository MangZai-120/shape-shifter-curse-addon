package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class StunEffect extends StatusEffect {
    public StunEffect() {
        super(StatusEffectCategory.HARMFUL, 0x888888);
        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, 
            "7107DE5E-7CE8-4030-940E-514C1F160890", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, 
            "22653B89-116E-49DC-9B6B-9971489B5C0A", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Removed explicit velocity reset to allow gravity/knockback to work
        // entity.setVelocity(0, entity.getVelocity().y, 0);
        // entity.velocityModified = true;
    }
}
