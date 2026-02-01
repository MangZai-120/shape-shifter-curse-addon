package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class TrueInvisibilityEffect extends StatusEffect {
    public TrueInvisibilityEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x00FFFF); // Cyan color
        this.addAttributeModifier(
            EntityAttributes.GENERIC_MOVEMENT_SPEED,
            "12db6328-9844-4e20-9118-202758169971", 
            0.25, 
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }
}
