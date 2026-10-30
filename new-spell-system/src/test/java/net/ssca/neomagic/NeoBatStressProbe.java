package net.ssca.neomagic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Dense 100-root/300-entity test; timings isolate the propagation callback, not whole-server MSPT. */
public final class NeoBatStressProbe {
    private static final Map<PigEntity,Vec3d> fixtures=new LinkedHashMap<>();
    private static final List<PigEntity> roots=new ArrayList<>(),neighbors=new ArrayList<>();
    private static long idleNs,idleTicks,activeNs,activeTicks,quietQueries,initNs;
    private static double idleUs;
    private static boolean complete;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception{
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,0);var center=t.getAbsolutePos(new BlockPos(4,40,4)).toCenterPos();p.refreshPositionAndAngles(center.x,center.y,center.z,0,0);
        p.getServer().getGameRules().get(net.minecraft.world.GameRules.MAX_ENTITY_CRAMMING).set(0,p.getServer());
        ServerTickEvents.START_SERVER_TICK.register(server->{if(!complete)fixtures.forEach((mob,pos)->{mob.setPosition(pos);mob.setVelocity(Vec3d.ZERO);});});
        idleNs=BatSpellLink.profileNanos;idleTicks=BatSpellLink.profileTicks;
        var record=new NeoState.Experiment();var spell=SpellRegistry.get("curse_mark");record.blueprint=NeoLegacyCasting.descriptor(spell,1,0);record.spell=spell.getId().toString();record.form=NeoRuntime.form(p);record.profile=NeoRuntime.profile(p);record.dimension=p.getWorld().getRegistryKey().getValue().toString();record.actual=25;record.paid=25;record.result="released";NeoRuntime.data(p).add(record);
        t.waitAndRun(60,()->{
            idleUs=(BatSpellLink.profileNanos-idleNs)/1000.0/(BatSpellLink.profileTicks-idleTicks);
            for(int i=0;i<300;i++){
                PigEntity mob=EntityType.PIG.create(p.getServerWorld());mob.setAiDisabled(true);mob.setNoGravity(true);mob.noClip=true;
                Vec3d at=center.add((i%10)*.025,0,4+(i/10)*.025);mob.refreshPositionAndAngles(at.x,at.y,at.z,0,0);check(p.getServerWorld().spawnEntity(mob),"Dense fixture is loaded");fixtures.put(mob,at);(i<100?roots:neighbors).add(mob);
            }
            var style=FormSpellStyle.plan(p,spell,1,0,0);check(style.pay(),"Dense cast accepted");style.bindBat(record);long before=System.nanoTime();style.effect(()->roots.forEach(mob->check(mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,200,0),p),"Root debuff applied")));initNs=System.nanoTime()-before;
        });
        t.waitAndRun(70,()->{activeNs=BatSpellLink.profileNanos;activeTicks=BatSpellLink.profileTicks;BatSpellLink.profileMaxNanos=0;});
        t.waitAndRun(150,()->{check(record.sporeInfections==200,"One hundred initial roots each transmit to at most two of two hundred neighbors: infected="+record.sporeInfections+", roots alive="+roots.stream().filter(Entity::isAlive).count()+", neighbors alive="+neighbors.stream().filter(Entity::isAlive).count()+", queries="+BatSpellLink.queries+", attempts="+BatSpellLink.attempts);check(neighbors.stream().allMatch(mob->mob.hasStatusEffect(StatusEffects.WEAKNESS)),"All two hundred eligible neighbors actually infected");});
        t.waitAndRun(240,()->{
            var result=new LinkedHashMap<String,Object>();result.put("entities",300);result.put("initial_roots",100);result.put("additional_infections",record.sporeInfections);result.put("baseline_callback_mean_us",idleUs);result.put("active_callback_mean_us",(BatSpellLink.profileNanos-activeNs)/1000.0/(BatSpellLink.profileTicks-activeTicks));result.put("active_callback_max_us",BatSpellLink.profileMaxNanos/1000.0);result.put("initial_application_ms",initNs/1e6);result.put("local_queries",BatSpellLink.queries);result.put("attempts",BatSpellLink.attempts);
            try{Files.writeString(Path.of("bat-stress-result.json"),NeoState.JSON.toJson(result));}catch(Exception e){throw new RuntimeException(e);}System.out.println("NEO_BAT_STRESS "+NeoState.JSON.toJson(result));
        });
        t.waitAndRun(275,()->quietQueries=BatSpellLink.queries);
        t.waitAndRun(310,()->{check(BatSpellLink.queries==quietQueries,"Expired debuffs stop all entity queries");complete=true;System.out.println("NEO_BAT_STRESS_PASS");t.complete();});
    }
}
