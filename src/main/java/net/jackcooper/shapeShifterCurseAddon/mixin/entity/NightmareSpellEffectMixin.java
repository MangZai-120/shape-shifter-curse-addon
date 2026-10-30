package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.NightmareSpellLink;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public class NightmareSpellEffectMixin {
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float ssca$dreamSpellDamage(float amount,DamageSource source,float original){
        return NightmareSpellLink.damage((LivingEntity)(Object)this,source,amount);
    }
    @ModifyVariable(method="addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private StatusEffectInstance ssca$dreamSpellDuration(StatusEffectInstance effect,StatusEffectInstance original,Entity source){
        return NightmareSpellLink.debuff((LivingEntity)(Object)this,effect);
    }
}
