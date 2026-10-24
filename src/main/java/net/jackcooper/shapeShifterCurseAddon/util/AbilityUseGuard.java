package net.jackcooper.shapeShifterCurseAddon.util;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;

/** Full control prevents active abilities; movement-only roots and casting slows do not. */
public final class AbilityUseGuard {
    private AbilityUseGuard() { }

    public interface PowerOwner {
        LivingEntity ssca$abilityOwner();
    }

    public static boolean fullyControlled(LivingEntity entity) {
        return entity != null && (entity.hasStatusEffect(SscAddon.STUN)
                || entity.hasStatusEffect(SscAddon.PLAYING_DEAD)
                || entity.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT));
    }

    public static boolean blocked(LivingEntity entity) {
        return fullyControlled(entity) || entity != null && entity.hasStatusEffect(SscAddon.SHEEP_FORM);
    }
}
