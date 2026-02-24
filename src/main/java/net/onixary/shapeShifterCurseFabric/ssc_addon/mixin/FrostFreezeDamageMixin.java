package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FrostFreezeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to:
 * 1. Increase damage taken by entities with Frost Freeze effect (+20%)
 * 2. Reduce damage taken by players during Teleport Attack (-65%)
 */
@Mixin(LivingEntity.class)
public abstract class FrostFreezeDamageMixin {
    
    /**
     * Modify the damage amount based on various conditions
     */
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyDamageForFrostEffects(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        float modifiedAmount = amount;
        
        // 1. Check if player is using Teleport Attack (reduce damage by 65%)
        if (self instanceof ServerPlayerEntity serverPlayer) {
            float reduction = SnowFoxSpTeleportAttack.getDamageReduction(serverPlayer);
            if (reduction > 0) {
                modifiedAmount = modifiedAmount * (1.0f - reduction);
            }
        }
        
        // 2. Check if entity has Frost Freeze effect (increase damage by 35%)
        StatusEffectInstance frostFreezeEffect = self.getStatusEffect(SscAddon.FROST_FREEZE);
        if (frostFreezeEffect != null && FrostFreezeEffect.isPhysicalOrMagicDamage(source)) {
            modifiedAmount = modifiedAmount * 1.35f;
        }


        return modifiedAmount;
    }
}
