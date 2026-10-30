package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Live target lookup, scoped only to spell effects; no extra tick scan or network channel. */
public final class NightmareSpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private static final ThreadLocal<Optional<ServerPlayerEntity>> EFFECT=new ThreadLocal<>();
    private NightmareSpellLink(){}
    public static ServerPlayerEntity current(){var p=EFFECT.get();return p==null?FormSpellStyle.currentCaster():p.orElse(null);}
    public static boolean with(Entity source,BooleanSupplier effect){
        var previous=EFFECT.get();EFFECT.set(Optional.ofNullable(source instanceof ServerPlayerEntity p?p:null));
        try{return effect.getAsBoolean();}finally{if(previous==null)EFFECT.remove();else EFFECT.set(previous);}
    }
    public static double multiplier(ServerPlayerEntity p,LivingEntity target){
        return p!=null&&!target.getWorld().isClient&&FormUtils.isForm(p,FormIdentifiers.WILD_CAT_NIGHTMARE)
                &&FormSpellStyle.legalTarget(p,target)&&NightmareDreamManager.isDreaming(p,target)
                ?BAL.d("nightmare_spell_multiplier",1.35):1;
    }
    public static float damage(LivingEntity target,DamageSource source,float amount){
        if(!(source.getAttacker() instanceof ServerPlayerEntity p)
                ||!source.isOf(SpellDamageSource.SPELL_DAMAGE)&&current()!=p)return amount;
        return (float)(amount*multiplier(p,target));
    }
    public static StatusEffectInstance debuff(LivingEntity target,StatusEffectInstance effect){
        if(effect.getEffectType().getCategory()!=StatusEffectCategory.HARMFUL||effect.getDuration()<=0)return effect;
        double factor=multiplier(current(),target);if(factor==1)return effect;
        int duration=(int)Math.min(Integer.MAX_VALUE,Math.ceil(effect.getDuration()*factor-1e-9));
        return new StatusEffectInstance(effect.getEffectType(),duration,effect.getAmplifier(),effect.isAmbient(),
                effect.shouldShowParticles(),effect.shouldShowIcon(),null,effect.getFactorCalculationData());
    }
}
