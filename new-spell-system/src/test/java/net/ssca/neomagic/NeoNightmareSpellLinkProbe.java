package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Own dream ledger, actual damage/status/projectiles/mist/blueprint and unchanged original scope. */
public final class NeoNightmareSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-4,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t)throws Exception{return NeoFormCastingProbe.player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,0);}
    private static PigEntity pig(ServerPlayerEntity p,double x,double z){
        var mob=EntityType.PIG.create(p.getServerWorld());mob.setAiDisabled(true);mob.setNoGravity(true);mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);mob.setHealth(200);
        mob.refreshPositionAndAngles(p.getX()+x,p.getY(),p.getZ()+z,0,0);p.getServerWorld().getChunk(mob.getBlockPos());check(p.getServerWorld().spawnEntity(mob),"Loaded living fixture");return mob;
    }
    private static void dream(ServerPlayerEntity p,LivingEntity mob){mob.timeUntilRegen=0;check(mob.damage(mob.getDamageSources().playerAttack(p),10),"Real hit seeds dream");check(NightmareDreamManager.isDreaming(p,mob),"Real damage enters this nightmare's ledger");mob.timeUntilRegen=0;}
    private static float hit(ServerPlayerEntity p,LivingEntity target){target.timeUntilRegen=0;float hp=target.getHealth();check(SpellHitHelper.hitRaw(p,target,10,0)==SpellHitHelper.HitResult.HIT,"Actual native spell damage succeeds");return hp-target.getHealth();}
    private static void collide(ProjectileEntity projectile,LivingEntity target)throws Exception{var method=ProjectileEntity.class.getDeclaredMethod("onCollision",HitResult.class);method.setAccessible(true);method.invoke(projectile,new EntityHitResult(target));}
    private static void blueprint(ServerPlayerEntity p,LivingEntity target){
        var b=new Blueprint("dream",Core.CURSE,Carrier.LOCK,List.of(),Core.CURSE,1,0,20);var q=NeoLegacyCasting.blueprintQuote(p,b);var s=new Session(b,q);s.complete=true;s.paid=q.actual();
        var r=new NeoState.Experiment();r.blueprint=b;r.form=NeoRuntime.form(p);r.profile=NeoRuntime.profile(p);r.dimension=p.getWorld().getRegistryKey().getValue().toString();r.actual=q.actual();r.paid=q.actual();
        var a=new NeoRuntime.Active(s,r,ItemStack.EMPTY,Hand.MAIN_HAND,new NeoEffects.Anchor(target.getPos(),target.getUuid()),new Random(0));a.style=FormSpellStyle.plan(p,false,b.range(),0,0,-1);NeoRuntime.data(p).add(r);
        check(NeoEffects.release(p,a),"Actual curse-lock blueprint releases");
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        var p=player(t);var other=player(t);var own=pig(p,0,3);dream(p,own);
        near(hit(p,own),13.5,"Native spell damage grows thirty-five percent");
        var awake=pig(p,2,3);near(hit(p,awake),10,"Hit entering dream is not retroactively enhanced");
        var foreign=pig(p,-2,3);dream(other,foreign);near(hit(p,foreign),10,"Another nightmare's dream does not qualify");
        var plain=pig(p,0,6);float hp=plain.getHealth();plain.damage(plain.getDamageSources().magic(),2);near(hp-plain.getHealth(),2,"Unattributed magic unchanged");
        own.timeUntilRegen=0;hp=own.getHealth();own.damage(own.getDamageSources().playerAttack(p),2);near(hp-own.getHealth(),2,"Native melee does not receive spell bonus");
        var style=FormSpellStyle.plan(p,SpellRegistry.get("dread_whisper"),1,0,0);
        style.effect(()->own.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,200,1),p));
        check(own.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==270&&own.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier()==1,"Debuff lasts 1.35 times with exactly the same amplifier");
        own.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,100,0),p);check(own.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==100,"Nonspell status with the same player source is unchanged");
        style.effect(()->own.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,100,0),p));check(own.getStatusEffect(StatusEffects.SPEED).getDuration()==100,"Beneficial effects unchanged");
        var foreignStatus=pig(p,0,8);dream(other,foreignStatus);style.effect(()->foreignStatus.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,200,1),p));check(foreignStatus.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==200,"Foreign dream does not lengthen debuffs");
        var boltTarget=pig(p,3,5);dream(p,boltTarget);var bolt=new SpellFireBoltEntity(p.getWorld(),p);bolt.setDamage(10);hp=boltTarget.getHealth();collide(bolt,boltTarget);near(hp-boltTarget.getHealth(),13.5,"Delayed native fire projectile gets the same multiplier outside cast context");
        var markTarget=pig(p,-3,5);dream(p,markTarget);var mark=new SpellCurseMarkEntity(p.getWorld(),p);mark.setDuration(200);mark.setLevel(2);collide(mark,markTarget);check(markTarget.getStatusEffect(SscAddon.CURSE_MARK).getDuration()==270&&markTarget.getStatusEffect(SscAddon.CURSE_MARK).getAmplifier()==1,"Delayed custom curse grows duration only");
        var aoe=player(t);var aoeOwn=pig(aoe,1,2);var aoeAwake=pig(aoe,-1,2);dream(aoe,aoeOwn);float beforeOwn=aoeOwn.getHealth(),beforeAwake=aoeAwake.getHealth();var nova=SpellRegistry.get("frost_nova");var novaStyle=FormSpellStyle.plan(aoe,nova,1,0,0);novaStyle.effect(()->nova.cast(aoe,5,false,1));near(beforeOwn-aoeOwn.getHealth(),(beforeAwake-aoeAwake.getHealth())*1.35,"One AOE handles each target independently");check(aoeOwn.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==Math.ceil(aoeAwake.getStatusEffect(StatusEffects.SLOWNESS).getDuration()*1.35-1e-9),"Native AOE applies duration once per dreamed target");
        var bp=player(t);var bpOwn=pig(bp,0,3);var bpAwake=pig(bp,2,3);dream(bp,bpOwn);float bpOwnHp=bpOwn.getHealth(),bpAwakeHp=bpAwake.getHealth();blueprint(bp,bpOwn);blueprint(bp,bpAwake);near(bpOwnHp-bpOwn.getHealth(),(bpAwakeHp-bpAwake.getHealth())*1.35,"Blueprint direct damage gets one multiplier");check(bpOwn.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==Math.ceil(bpAwake.getStatusEffect(StatusEffects.WEAKNESS).getDuration()*1.35-1e-9),"Blueprint debuff duration gets one multiplier");
        var mistCaster=player(t);var mistOwn=pig(mistCaster,1,1);var mistAwake=pig(mistCaster,-1,1);dream(mistCaster,mistOwn);SpellRegistry.get("corrupt_mist").cast(mistCaster,1,false,1);
        t.waitAndRun(42,()->{check(mistOwn.hasStatusEffect(StatusEffects.POISON)&&mistAwake.hasStatusEffect(StatusEffects.POISON),"Persistent mist pulse applies after the original cast returns");check(mistOwn.getStatusEffect(StatusEffects.POISON).getDuration()-mistAwake.getStatusEffect(StatusEffects.POISON).getDuration()==28,"Mist poison duration grows from eighty to one hundred eight ticks");check(mistOwn.getStatusEffect(StatusEffects.POISON).getAmplifier()==mistAwake.getStatusEffect(StatusEffects.POISON).getAmplifier(),"Mist poison strength unchanged");});
        t.waitAndRun(205,()->{near(hit(p,own),10,"Expired own dream does not qualify");System.out.println("NEO_NIGHTMARE_LINK_PASS checks="+checks);t.complete();});
    }
}
