package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Ownership belongs to a layer of vanilla poison, including while a stronger layer hides it. */
public interface SpellPoison {
    UUID ssca$getPoisonCaster();
    void ssca$setPoisonCaster(UUID caster);

    static StatusEffectInstance fromCaster(StatusEffectInstance effect, ServerPlayerEntity caster) {
        if (effect.getEffectType() == StatusEffects.POISON && caster != null) {
            ((SpellPoison) effect).ssca$setPoisonCaster(caster.getUuid());
        }
        return effect;
    }

    static StatusEffectInstance copyOwner(StatusEffectInstance original, StatusEffectInstance replacement) {
        if (original.getEffectType() == StatusEffects.POISON && original instanceof SpellPoison poison) {
            ((SpellPoison) replacement).ssca$setPoisonCaster(poison.ssca$getPoisonCaster());
        }
        return replacement;
    }
}
