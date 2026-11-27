package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.effect.SpellPoison;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StatusEffect.class)
abstract class SpellPoisonDamageMixin {
    @ModifyExpressionValue(method = "applyUpdateEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/damage/DamageSources;magic()Lnet/minecraft/entity/damage/DamageSource;"))
    private DamageSource ssca$attributeSpellPoison(DamageSource source, LivingEntity target, int amplifier) {
        if ((Object) this == StatusEffects.POISON && !target.getWorld().isClient
                && target.getStatusEffect(StatusEffects.POISON) instanceof SpellPoison poison) {
            var owner = poison.ssca$getPoisonCaster();
            var player = owner == null ? null : target.getWorld().getPlayerByUuid(owner);
            if (player != null) return new DamageSource(source.getTypeRegistryEntry(), null, player);
        }
        return source;
    }
}
