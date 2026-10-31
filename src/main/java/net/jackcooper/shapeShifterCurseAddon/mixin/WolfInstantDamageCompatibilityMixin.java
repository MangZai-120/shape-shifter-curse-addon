package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.Entity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyInstantDamagePower;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the existing wolf Harming-to-healing calculation after adding the undead entity group. */
// Apply after SSC's priority-1000 mixin so this HEAD callback precedes its cancellable handler.
@Mixin(value = StatusEffect.class, priority = 900)
public abstract class WolfInstantDamageCompatibilityMixin {
    @Unique
    private boolean ssca$applyLegacyHarming(LivingEntity target, Entity source, Entity attacker,
                                            int amplifier, double proximity, boolean instant) {
        if (target.getWorld().isClient || (Object) this != StatusEffects.INSTANT_DAMAGE
                || !(target instanceof PlayerEntity player) || !target.isUndead()) return false;
        var form = FormUtils.getPlayerForm(player);
        if (form == null || !(FormIdentifiers.ANUBIS_WOLF_SP.equals(form.getFormID())
                || FormIdentifiers.GOLDEN_SANDSTORM_SP.equals(form.getFormID()))) return false;
        var powers = PowerHolderComponent.getPowers(target, ModifyInstantDamagePower.class);
        boolean legacy = false;
        for (var power : powers) {
            var id = power.getType().getIdentifier();
            if (id.getNamespace().equals("shape-shifter-curse")
                    && id.getPath().equals("form_anubis_wolf_3_reverse_instant_damage")
                    && power.isActive() && power.ApplyMulScale(1F) < 0F) {
                legacy = true;
                break;
            }
        }
        if (!legacy) return false;
        // Same -6 basis, proximity rounding, modifier order and damage source as SSC before
        // the entity-group change. Enter before SSC's cancellable effect handler.
        float amount = -6 << amplifier;
        if (instant) amount = (float) (proximity * amount + 0.5);
        for (var power : powers) amount = power.ApplyMulScale(amount);
        if (amount > 0) target.heal(amount);
        else if (amount < 0) target.damage(instant && source != null && attacker != null
                ? target.getDamageSources().indirectMagic(source, attacker)
                : target.getDamageSources().magic(), -amount);
        return true;
    }

    @Inject(method = "applyInstantEffect", at = @At("HEAD"), cancellable = true)
    private void ssca$legacyInstantHarming(Entity source, Entity attacker, LivingEntity target,
                                          int amplifier, double proximity, CallbackInfo ci) {
        if (ssca$applyLegacyHarming(target, source, attacker, amplifier, proximity, true)) ci.cancel();
    }

    @Inject(method = "applyUpdateEffect", at = @At("HEAD"), cancellable = true)
    private void ssca$legacyUpdatedHarming(LivingEntity target, int amplifier, CallbackInfo ci) {
        if (ssca$applyLegacyHarming(target, null, null, amplifier, 0, false)) ci.cancel();
    }
}
