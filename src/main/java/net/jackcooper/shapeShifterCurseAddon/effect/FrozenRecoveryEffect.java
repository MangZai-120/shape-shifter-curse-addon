package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** The exact twenty-five-percent movement reduction after an ice enclosure shatters. */
public final class FrozenRecoveryEffect extends StatusEffect {
    public FrozenRecoveryEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8DC9E8);
        // AnomalyEffects selects the strongest active anomaly slow using one shared modifier.
    }
}
