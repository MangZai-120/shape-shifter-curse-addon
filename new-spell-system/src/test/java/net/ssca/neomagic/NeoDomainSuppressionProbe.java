package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.*;
import java.util.*;

/** Actual channel/domain/sequence lifecycle in an isolated Minecraft world; no rendered/audio acceptance. */
public final class NeoDomainSuppressionProbe {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    @SuppressWarnings("unchecked") private static Map<UUID,Object> map(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return (Map<UUID,Object>)f.get(null);}
    private static Object sequence(ServerPlayerEntity p)throws Exception{return map(ExplosionManager.class,"SEQUENCES").values().stream().filter(s->{try{return field(s,"owner").equals(p.getUuid());}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();}
    private static void phase(ServerPlayerEntity owner,int elapsed)throws Exception{
        var fields=map(DomainManager.class,"FIELDS");Object old=fields.get(owner.getUuid());var type=old.getClass();var components=type.getRecordComponents();
        Class<?>[] types=Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);Object[] values=new Object[components.length];
        for(int i=0;i<components.length;i++){var getter=components[i].getAccessor();getter.setAccessible(true);values[i]=components[i].getName().equals("startTick")?owner.getServer().getTicks()-elapsed:getter.invoke(old);}
        var constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);fields.put(owner.getUuid(),constructor.newInstance(values));
    }
    private static boolean validate(ServerPlayerEntity p)throws Exception{
        Object channel=map(SpellChannelManager.class,"ACTIVE").get(p.getUuid());var method=SpellChannelManager.class.getDeclaredMethod("ensureValid",channel.getClass());method.setAccessible(true);return (boolean)method.invoke(null,channel);
    }
    private static ServerPlayerEntity player(TestContext t)throws Exception{
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.FAMILIAR_FOX_SP,0);
        Vec3d origin=t.getAbsolutePos(new BlockPos(2,10,2)).toCenterPos();p.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,45);
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(origin).add(-5,-6,-2),BlockPos.ofFloored(origin).add(5,-6,20)))p.getServerWorld().setBlockState(pos,Blocks.STONE.getDefaultState());
        NeoRuntime.data(p).spellbookMasteryExpTen=120000;
        return p;
    }
    private static int[] startExplosion(ServerPlayerEntity caster)throws Exception{
        var spell=SpellRegistry.get("explosion");int[] result={0,0,0};
        check(SpellChannelManager.start(caster,spell,ScrollData.create("explosion"),1,false,7701,300,100,()->true,
                target->{result[0]++;ExplosionManager.detonate(caster,target,10,null);},cd->{result[1]++;result[2]=cd;}),"Explosion starts through production channel");
        return result;
    }
    private static ServerPlayerEntity domain(TestContext t,Vec3d center,int elapsed)throws Exception{
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.FAMILIAR_FOX_SP,0);p.refreshPositionAndAngles(center.x,center.y,center.z,0,0);
        var spell=SpellRegistry.get("domain");
        check(SpellChannelManager.start(p,spell,ScrollData.create("domain"),1,false,7702,1,100,()->true,v->{},cd->{}),"Domain starts through production channel");
        phase(p,elapsed);return p;
    }
    private static void cleanup(ServerPlayerEntity p){SpellChannelManager.onFullyControlled(p);DomainManager.remove(p);}
    public static void start(TestContext t){t.waitAndRun(65,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception{
        // An overlapping circle edge alone is insufficient; its center must actually be covered.
        var p=player(t);int[] outcome=startExplosion(p);Object seq=sequence(p);Vec3d target=(Vec3d)field(seq,"center");
        var owner=domain(t,target.add(.8,0,0),DomainRules.expandStartTick());
        check(!DomainManager.enclosedTrapping(p.getWorld(),target),"Initial 0.75-radius domain does not cover center at 0.8 distance");
        check(validate(p)&&SpellChannelManager.isCasting(p),"Circle overlap without center coverage keeps casting");
        cleanup(owner);cleanup(p);

        // Pre-expansion charging is not a covering sphere, even when its origin equals the target.
        p=player(t);startExplosion(p);seq=sequence(p);target=(Vec3d)field(seq,"center");owner=domain(t,target,DomainRules.expandStartTick()-1);
        check(validate(p),"Domain preparation before expansion does not suppress explosion");cleanup(owner);cleanup(p);

        // The expanding shell includes its boundary and overrides the already locked explosion.
        p=player(t);outcome=startExplosion(p);seq=sequence(p);target=(Vec3d)field(seq,"center");
        for(int tick=1;tick<=10;tick++)NeoLegacyCasting.active(p).advance(tick);
        double paid=NeoRuntime.data(p).burden;
        set(seq,"startTime",p.getWorld().getTime()-620);
        check(SpellRegistry.get("explosion").isLockedIn(p),"Explosion is actually in protected audio/animation stage");
        SpellChannelManager.cancelSelf(p);SpellChannelManager.onDamaged(p);check(SpellChannelManager.isCasting(p),"Ordinary cancellation and damage still respect lock");
        owner=domain(t,target.add(.75,0,0),DomainRules.expandStartTick());
        check(!DomainManager.enclosedTrapping(p.getWorld(),p.getPos()),"Caster remains outside tiny domain; only impact center is covered");
        check(!validate(p)&&!SpellChannelManager.isCasting(p),"Boundary center coverage interrupts locked explosion");
        check(outcome[0]==0&&outcome[1]==1&&outcome[2]==SpellRegistry.get("explosion").getInterruptedCooldown(100),"No effect, one cooldown settlement using existing interrupted-CD rule");
        check(NeoRuntime.data(p).burden==paid,"Previously paid collapse is retained without extra payment");
        check((int)field(seq,"suppressedAt")>=0&&!(boolean)field(seq,"exploded")&&((List<?>)field(seq,"pendingTargets")).isEmpty(),"Suppressed sequence only retains inert visual finale");
        check(!ExplosionManager.isBallCharging(p),"Suppression immediately clears release lock");
        ExplosionManager.detonate(p,target,1000,null);check(!(boolean)field(seq,"exploded"),"Late detonate cannot revive cancelled explosion");
        check(!ExplosionManager.suppressIfDomainCovered(p)&&outcome[1]==1,"Repeated coverage does not settle twice");
        UUID suppressedOwner=p.getUuid();cleanup(owner);
        t.waitAndRun(42,()->{try{check(map(ExplosionManager.class,"SEQUENCES").values().stream().noneMatch(s->{try{return field(s,"owner").equals(suppressedOwner);}catch(Exception e){throw new RuntimeException(e);}}),"Cancelled visual sequence expires after its finale");System.out.println("NEO_DOMAIN_SUPPRESSION_PASS checks="+checks);t.complete();}catch(Exception e){throw new RuntimeException(e);}});

        // Fully active domain coverage follows the same force-stop path.
        p=player(t);outcome=startExplosion(p);seq=sequence(p);target=(Vec3d)field(seq,"center");owner=domain(t,target,DomainRules.chargeTicks());DomainManager.activate(owner);
        check(!validate(p)&&outcome[0]==0&&outcome[1]==1,"Complete domain also cancels without releasing damage");cleanup(owner);

        // Once damage has been authorized, a later domain does not undo the released effect.
        p=player(t);startExplosion(p);seq=sequence(p);target=(Vec3d)field(seq,"center");ExplosionManager.detonate(p,target,1,null);
        owner=domain(t,target,DomainRules.expandStartTick());check(!ExplosionManager.suppressIfDomainCovered(p)&&(boolean)field(seq,"exploded"),"Already released explosion is not retroactively cancelled");cleanup(owner);cleanup(p);
    }
}
