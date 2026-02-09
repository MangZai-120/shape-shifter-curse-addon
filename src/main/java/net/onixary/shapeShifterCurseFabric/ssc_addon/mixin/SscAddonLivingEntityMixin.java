package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.EffectEfficiencyReductionPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class SscAddonLivingEntityMixin {
    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
    private StatusEffectInstance modifyStatusEffect(StatusEffectInstance effect) {
        if (!effect.getEffectType().isInstant() && PowerHolderComponent.hasPower((LivingEntity)(Object)this, EffectEfficiencyReductionPower.class)) {
            int originalAmp = effect.getAmplifier();
            int newDuration;

            // Logic:
            // Level 1 (amp 0): Duration * 0.4 (40%)
            // Level 2 (amp 1): Duration * 0.6 (60%), Amp -> 0
            // Level 3 (amp 2): Duration * 0.8 (80%), Amp -> 0
            // Level 4+ (amp 3+): Duration * 1.0 (100%), Amp -> 0

            if (originalAmp == 0) {
                // Level 1
                newDuration = (int)(effect.getDuration() * 0.4);
            } else if (originalAmp == 1) {
                // Level 2
                newDuration = (int)(effect.getDuration() * 0.6);
            } else if (originalAmp == 2) {
                // Level 3
                newDuration = (int)(effect.getDuration() * 0.8);
            } else {
                // Level 4+
                newDuration = effect.getDuration();
            }

            return new StatusEffectInstance(
                effect.getEffectType(),
                newDuration,
                0, // Always force to Level 1 (amplifier 0)
                effect.isAmbient(),
                effect.shouldShowParticles(),
                effect.shouldShowIcon(),
                (StatusEffectInstance)null, 
                effect.getFactorCalculationData()
            );
        }
        return effect;
    }
}
