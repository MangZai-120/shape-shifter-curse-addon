package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;

/** Independent full freeze; normal status removal also restores movement speed immediately. */
public final class FrozenEffect extends StatusEffect {
    public FrozenEffect() {
        super(StatusEffectCategory.HARMFUL, 0x9DE8FF);
        addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "5175e13e-dd9a-4c37-8282-c150357ae65e",
                -1, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
