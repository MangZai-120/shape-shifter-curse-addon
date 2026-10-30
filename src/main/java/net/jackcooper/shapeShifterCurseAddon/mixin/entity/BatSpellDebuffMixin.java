package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.BatSpellLink;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the accepted spell-effect context may seed a contagion. */
@Mixin(LivingEntity.class)
public class BatSpellDebuffMixin {
    @Inject(method="addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",at=@At("RETURN"))
    private void ssca$spellDebuff(StatusEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValueZ()){
            BatSpellLink.applied(BatSpellLink.current(),(LivingEntity)(Object)this,effect,source);
            net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.status(net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.current(),(LivingEntity)(Object)this,effect);
        }
    }
    @Inject(method="removeStatusEffectInternal",at=@At("RETURN"))
    private void ssca$cleansed(StatusEffect effect,CallbackInfoReturnable<StatusEffectInstance> cir){
        if(cir.getReturnValue()!=null)BatSpellLink.cured((LivingEntity)(Object)this,effect);
    }
    @Inject(method="clearStatusEffects",at=@At("RETURN"))
    private void ssca$allCleansed(CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValueZ())BatSpellLink.cured((LivingEntity)(Object)this,null);}
}
