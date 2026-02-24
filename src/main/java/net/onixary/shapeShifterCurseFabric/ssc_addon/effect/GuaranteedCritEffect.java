package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class GuaranteedCritEffect extends StatusEffect {
    public GuaranteedCritEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFF0000); // Red color
        // +25% speed for 3 seconds as well
        this.addAttributeModifier(
            EntityAttributes.GENERIC_MOVEMENT_SPEED,
            "71077713-3984-4786-8800-478950587747",
            0.25,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }
}
