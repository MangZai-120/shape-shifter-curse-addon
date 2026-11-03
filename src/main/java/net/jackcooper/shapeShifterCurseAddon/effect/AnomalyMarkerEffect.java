package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** Standard, synchronized and purifiable status; combat magnitudes live in the effect registry. */
public final class AnomalyMarkerEffect extends StatusEffect {
    public AnomalyMarkerEffect(int color) { super(StatusEffectCategory.HARMFUL, color); }
}
