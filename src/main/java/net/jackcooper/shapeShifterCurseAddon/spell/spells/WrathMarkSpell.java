package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WrathMarkSpell extends LockedTargetSpell {
    public WrathMarkSpell() { super("wrath_mark"); }

    @Override protected boolean eligible(ServerPlayerEntity caster, LivingEntity target) {
        return super.eligible(caster, target) && !target.hasStatusEffect(SscAddon.PURIFIED);
    }
    private static int duration(int tier) {
        int seconds = switch (tier) {
            case 1, 2 -> 10;
            case 3 -> 8;
            case 4 -> 7;
            default -> 5;
        };
        return RuneCastContext.current().duration(20 * seconds, RuneModifiers.Stat.NEGATIVE);
    }
    @Override public SpellBacklash.Effects backlashEffects(ServerPlayerEntity caster,float power,boolean solo,int level) {
        return SpellBacklash.Effects.debuffs(0,new StatusEffectInstance(StatusEffects.BLINDNESS,duration(level)));
    }
    @Override public void cast(ServerPlayerEntity caster,float power,boolean solo) { cast(caster,power,solo,1); }
    @Override public void cast(ServerPlayerEntity caster,float power,boolean solo,int level) {
        LivingEntity target = takeTarget(caster);
        if (target != null) {
            int tier=Math.max(1,Math.min(level,getMaxLevel()));
            boolean alreadyDebuffed=FormCastingStyle.hasHarmfulEffect(target);
            if (WrathMark.apply(caster,target,duration(tier),power)) {
                net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.hit(caster,target);
                FormCastingStyle.onSpellHit(caster,target,FormationElement.CURSE,solo ? null : ssc_addon$getRefundCastId(),alreadyDebuffed);
            }
        }
    }
}
