package net.ssca.neomagic;

import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Actual server ticks, concurrent simulated players. No real-client/network acceptance implied. */
public final class NeoStabilityWorldProbe {
    private static final List<String> failures=new ArrayList<>();
    private static int checks,casts,peaksActive,peaksBolts,peaksSummons,frame;
    private static volatile int reloads;
    private static int reconnects,respawns,formSwitches;
    private static long begun;
    private static final List<ServerPlayerEntity> players=new ArrayList<>();
    private static final List<Vec3d> positions=new ArrayList<>();
    private static NeoRuntime.Active clock;
    private static int clockStart;
    private static Method dispatch;
    private static Field open,last;
    private static boolean complete,initialized;
    private static int lastStepTick=Integer.MIN_VALUE;
    private static void check(boolean value,String label){checks++;if(!value){failures.add(label);System.err.println("STABILITY REGRESSION: "+label);}}
    public static void start(TestContext test) {
        if(Boolean.getBoolean("neomagic.resume")){resume(test);return;}
        test.waitAndRun(70,()->{
            try {
                if(initialized)return;initialized=true;
                begun=System.nanoTime();
                dispatch=net.ssca.neomagic.NeoResearchFixtures.class.getDeclaredMethod("handle",ServerPlayerEntity.class,NbtCompound.class);dispatch.setAccessible(true);
                open=net.ssca.neomagic.NeoResearchFixtures.class.getDeclaredField("OPEN");open.setAccessible(true);last=net.ssca.neomagic.NeoResearchFixtures.class.getDeclaredField("LAST");last.setAccessible(true);
                Vec3d base=test.getAbsolutePos(new BlockPos(3,2,3)).toCenterPos();
                for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(base).add(-10,-1,-10),BlockPos.ofFloored(base).add(32,-1,18)))test.getWorld().setBlockState(pos,Blocks.STONE.getDefaultState());
                for(int i=0;i<8;i++) {
                    var p=test.createMockCreativeServerPlayerInWorld();Vec3d at=base.add((i%4)*6,0,(i/4)*8);
                    p.refreshPositionAndAngles(at.x,at.y,at.z,0,-20);p.setNoGravity(true);p.getAbilities().invulnerable=true;
                    players.add(p);positions.add(at);NeoRuntime.refreshForm(p);NeoRuntime.data(p).known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());
                }
                check(players.stream().map(ServerPlayerEntity::getUuid).distinct().count()==8,"Eight independent player identities");
                var p=players.get(0);bind(p,new Blueprint("clock",Core.LUNAR,Carrier.SELF,List.of(),Core.VOID,.5,20,4));
                clockStart=p.getServer().getTicks();net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);clock=NeoRuntime.ACTIVE.get(p.getUuid());check(clock!=null,"Clock cast begins");
                frame=0;test.runAtEveryTick(()->step(test));
            }catch(Exception error){throw new RuntimeException(error);}
        });
    }
    private static void bind(ServerPlayerEntity p,Blueprint b) {
        ItemStack wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));p.setStackInHand(Hand.MAIN_HAND,wand);
    }
    private static void step(TestContext test) {
        if(complete)return;
        try {
            var server=test.getWorld().getServer();
            if(lastStepTick==server.getTicks())return;lastStepTick=server.getTicks();
            var first=players.get(0);int elapsed=server.getTicks()-clockStart;
            if(elapsed==1)check(clock.age==0,"Starting tick must not advance preparation by one extra tick");
            if(elapsed==2)invalidNotes(first);
            if(elapsed==20)check(NeoRuntime.ACTIVE.containsKey(first.getUuid()),"20-tick cast must survive until its real final tick");
            if(elapsed==21)check(!NeoRuntime.ACTIVE.containsKey(first.getUuid())&&clock.record.checks.size()==3,"0/10/20 checks finish exactly once");
            if(elapsed==22){var p=players.get(1);bind(p,new Blueprint("instant clock",Core.LUNAR,Carrier.SELF,List.of(),Core.VOID,.5,0,4));net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);}
            if(elapsed==23){var p=players.get(1);var d=NeoRuntime.data(p);check(d.cooldowns.getOrDefault(net.ssca.neomagic.NeoResearchFixtures.bound(p.getMainHandStack()).identity(),0)==80,"Instant cast keeps its full cooldown during starting tick");check(d.recovery==NeoRules.recoveryDelay,"Instant cast keeps its full recovery delay during starting tick");}
            if(elapsed==30&&!failures.isEmpty())throw new AssertionError(String.join("; ",failures));
            if(elapsed<30)return;
            frame=elapsed-30;int duration=Integer.getInteger("neomagic.soakTicks",2400);
            if(frame<duration) {
                for(int i=0;i<players.size();i++) {
                    ServerPlayerEntity p=players.get(i);var d=NeoRuntime.data(p);p.setHealth(p.getMaxHealth());
                    if(frame%20==0) {
                        Vec3d at=positions.get(i);p.refreshPositionAndAngles(at.x,at.y,at.z,0,i%3==2?40:-25);
                        if(NeoEffects.hasStored(p.getUuid()))NeoEffects.trigger(p);
                        if(!NeoRuntime.ACTIVE.containsKey(p.getUuid())&&!NeoEffects.hasStored(p.getUuid())) {
                            Blueprint b=spell(i,(frame/20)%3);bind(p,b);
                            if(d.cooldowns.getOrDefault(b.identity(),0)==0&&!NeoRuntime.blocked(p)){net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);casts++;}
                        }
                    }
                    check(Double.isFinite(d.burden)&&d.burden>=0&&d.records.size()<=32,"Finite burden and bounded history for player "+i);
                }
                if(frame%200==0) {
                    NeoState.get(server).markDirty();test.getWorld().getPersistentStateManager().save();
                    for(var p:players)check(NeoRuntime.data(p).progress.values().stream().allMatch(v->v.length==4&&Arrays.stream(v).allMatch(Double::isFinite)),"Finite independent adaptation arrays");
                }
                if(frame==400||frame==1400)server.reloadResources(server.getDataPackManager().getEnabledNames()).whenComplete((unused,error)->{if(error!=null)failures.add("Datapack reload: "+error);else reloads++;});
                if(frame==300)crossPlayerToken();
                if(frame==800)reconnect(test);
                if(frame==1100)respawn(test);
                if(frame==1400)switchForm();
            }
            peaksActive=Math.max(peaksActive,NeoRuntime.ACTIVE.size());peaksBolts=Math.max(peaksBolts,size(NeoEffects.class,"BOLTS"));peaksSummons=Math.max(peaksSummons,size(NeoEffects.class,"SUMMONS"));
            if(frame%400==0)System.out.println("Neomagic stability progress: ticks="+frame+" casts="+casts+" active="+NeoRuntime.ACTIVE.size()+" bolts="+size(NeoEffects.class,"BOLTS")+" summons="+size(NeoEffects.class,"SUMMONS"));
            if(frame>=duration+320)finish(test);
        }catch(Throwable error){complete=true;write(false);error.printStackTrace();throw new RuntimeException(error);}
    }
    private static void crossPlayerToken()throws Exception {
        var a=players.get(0);var b=players.get(1);BlockPos table=a.getBlockPos().add(2,0,0);a.getWorld().setBlockState(table,net.ssca.neomagic.NeoTestFixtures.TABLE.getDefaultState());net.ssca.neomagic.NeoResearchFixtures.open(a,table);net.ssca.neomagic.NeoResearchFixtures.open(b,null);
        Object context=((Map<?,?>)open.get(null)).get(a.getUuid());Method method=context.getClass().getDeclaredMethod("token");method.setAccessible(true);
        String before=NeoRuntime.data(b).hypothesis;var n=new NbtCompound();n.putUuid("Token",(UUID)method.invoke(context));n.putString("Action","notes");n.putString("Hypothesis","other-player-token");dispatch.invoke(null,b,n);
        check(NeoRuntime.data(b).hypothesis.equals(before),"Another player's UI token cannot mutate progress");net.ssca.neomagic.NeoResearchFixtures.close(a);net.ssca.neomagic.NeoResearchFixtures.close(b);
    }
    private static void place(ServerPlayerEntity p,int index) {
        var at=positions.get(index);p.refreshPositionAndAngles(at.x,at.y,at.z,0,-20);p.setNoGravity(true);p.getAbilities().invulnerable=true;
    }
    private static void reconnect(TestContext test) {
        var old=players.get(0);var server=old.getServer();var d=NeoRuntime.data(old);d.burden=37.25;d.recovery=43;String progress=NeoState.JSON.toJson(d.progress);
        old.networkHandler.onDisconnected(net.minecraft.text.Text.literal("stability reconnect"));
        var fresh=new ServerPlayerEntity(server,test.getWorld(),old.getGameProfile());
        server.getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),fresh);
        players.set(0,fresh);place(fresh,0);var restored=NeoRuntime.data(fresh);
        check(restored.burden==37.25&&restored.recovery==43,"Reconnect preserves burden and waiting time");check(NeoState.JSON.toJson(restored.progress).equals(progress),"Reconnect preserves adaptation");
        check(!NeoRuntime.ACTIVE.containsKey(fresh.getUuid())&&!NeoEffects.hasStored(fresh.getUuid()),"Disconnect removes pending casts and storage");reconnects++;
    }
    private static void respawn(TestContext test) {
        var old=players.get(3);var d=NeoRuntime.data(old);d.burden=80;String progress=NeoState.JSON.toJson(d.progress);old.getAbilities().invulnerable=false;old.timeUntilRegen=0;
        old.damage(old.getDamageSources().outOfWorld(),10000);check(!old.isAlive(),"Death transition actually occurs");
        var fresh=old.getServer().getPlayerManager().respawnPlayer(old,false);players.set(3,fresh);place(fresh,3);var restored=NeoRuntime.data(fresh);
        check(restored.burden==0&&restored.recovery==0,"Respawn resets only the burden resource");check(NeoState.JSON.toJson(restored.progress).equals(progress),"Respawn retains adaptation");
        check(!NeoRuntime.ACTIVE.containsKey(fresh.getUuid())&&!NeoEffects.hasStored(fresh.getUuid()),"Respawn has no stale casting session");respawns++;
    }
    private static void switchForm() {
        var p=players.get(4);var d=NeoRuntime.data(p);d.burden=15;
        for(var effect:List.of(NeoMagic.DISORDER,NeoMagic.DISINTEGRATION,NeoMagic.BREAKDOWN))p.removeStatusEffect(effect);
        Blueprint spell=new Blueprint("form transition",Core.LUNAR,Carrier.SELF,List.of(Control.CONSTRAIN),Core.VOID,.5,40,4);d.cooldowns.remove(spell.identity());bind(p,spell);net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);
        var cast=NeoRuntime.ACTIVE.get(p.getUuid());check(cast!=null,"Prepare a cast across a form change");
        var component=net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.get(p);var original=component.nowForm;String progress=NeoState.JSON.toJson(d.progress);
        component.nowForm=net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.AXOLOTL_3;NeoRuntime.refreshForm(p);
        check(d.burden==0&&NeoRules.profile(d.form).safety()==80,"Form component change resets C and replaces safety profile");check(!NeoRuntime.ACTIVE.containsKey(p.getUuid())&&cast.record.result.equals("form_changed"),"Form change interrupts the current preparation");
        check(NeoState.JSON.toJson(d.progress).equals(progress),"Form changes preserve per-spell adaptation");component.nowForm=original;NeoRuntime.refreshForm(p);formSwitches++;
    }
    private static Blueprint spell(int player,int variant) {
        return switch(player%4) {
            case 0->new Blueprint("soak heal",Core.LUNAR,Carrier.SELF,List.of(),Core.VOID,.5,variant*10,4);
            case 1->new Blueprint("soak bolt",Core.THERMAL,Carrier.PROJECTILE,variant==0?List.of():variant==1?List.of(Control.SPLIT):List.of(Control.MERGE),Core.FROST,.5,variant*10,16);
            case 2->new Blueprint("soak summon",Core.SUMMON,Carrier.AREA,variant==0?List.of():List.of(Control.SPLIT),Core.FROST,.5,10,8);
            default->new Blueprint("soak store",Core.LUNAR,Carrier.SELF,List.of(Control.STORE),Core.VOID,.5,variant*10,4);
        };
    }
    private static void invalidNotes(ServerPlayerEntity p)throws Exception {
        BlockPos pos=p.getBlockPos().add(-2,0,0);p.getWorld().setBlockState(pos,net.ssca.neomagic.NeoTestFixtures.TABLE.getDefaultState());net.ssca.neomagic.NeoResearchFixtures.open(p,pos);
        Object context=((Map<?,?>)open.get(null)).get(p.getUuid());Method method=context.getClass().getDeclaredMethod("token");method.setAccessible(true);
        var d=NeoRuntime.data(p);d.hypothesis="original-h";d.conclusion="original-c";
        NbtCompound request=new NbtCompound();request.putUuid("Token",(UUID)method.invoke(context));request.putString("Action","notes");request.putString("Hypothesis","must-not-save");request.putString("Conclusion","must-not-save");request.putString("Citations","x".repeat(257));
        dispatch.invoke(null,p,request);check(d.hypothesis.equals("original-h")&&d.conclusion.equals("original-c"),"Rejected notes request must not partially mutate saved text");
        ((Map<?,?>)last.get(null)).remove(p.getUuid());
        var valid=request.copy();valid.putString("Hypothesis","original-h");valid.putString("Conclusion","original-c");valid.putString("Citations","");dispatch.invoke(null,p,valid);
        var close=new NbtCompound();close.putUuid("Token",request.getUuid("Token"));close.putString("Action","close");dispatch.invoke(null,p,close);
        check(!((Map<?,?>)open.get(null)).containsKey(p.getUuid()),"UI close is not dropped by the same-tick request throttle");
        d.hypothesis="original-h";d.conclusion="original-c";net.ssca.neomagic.NeoResearchFixtures.close(p);
    }
    private static int size(Class<?> type,String name)throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);Object collection=field.get(null);return collection instanceof Map<?,?> m?m.size():((Collection<?>)collection).size();
    }
    private static void finish(TestContext test)throws Exception {
        complete=true;check(NeoRuntime.ACTIVE.isEmpty(),"No live casts after drain");check(size(NeoEffects.class,"BOLTS")==0,"No leaked projectiles after drain");check(size(NeoEffects.class,"SUMMONS")==0,"No leaked summons after drain");check(size(NeoEffects.class,"STORED")==0,"No leaked stored structures after drain");check(reloads==2,"Two asynchronous datapack reloads complete");
        var state=NeoState.get(test.getWorld().getServer());
        for(var p:players){net.ssca.neomagic.NeoResearchFixtures.close(p);var d=NeoRuntime.data(p);d.hypothesis="restart:"+p.getUuid();}
        check(size(net.ssca.neomagic.NeoResearchFixtures.class,"OPEN")==0&&size(net.ssca.neomagic.NeoResearchFixtures.class,"LAST")==0,"UI contexts and throttle entries are released");state.markDirty();test.getWorld().getPersistentStateManager().save();
            Files.writeString(Path.of("saved-players.json"),NeoState.JSON.toJson(players.stream().map(p->p.getUuid().toString()).toList()));
        write(failures.isEmpty());if(!failures.isEmpty())throw new AssertionError(String.join("; ",failures));
        System.out.println("Neomagic stability passed: players=8 ticks="+frame+" casts="+casts+" checks="+checks);test.complete();
    }
    private static void write(boolean passed) {
        try {var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("simulated_players",8);result.put("server_ticks",frame);result.put("casts_attempted",casts);result.put("checks",checks);result.put("peak_active",peaksActive);result.put("peak_bolts",peaksBolts);result.put("peak_summons",peaksSummons);result.put("reloads",reloads);result.put("reconnects",reconnects);result.put("respawns",respawns);result.put("form_switches",formSwitches);result.put("wall_seconds",(System.nanoTime()-begun)/1e9);result.put("failures",failures);Files.writeString(Path.of(passed?(frame>10000?"world-long-soak.json":"world-soak.json"):"world-baseline-failures.json"),NeoState.JSON.toJson(result));}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void resume(TestContext test) {
        try {
            var ids=NeoState.JSON.fromJson(Files.readString(Path.of("saved-players.json")),String[].class);var state=NeoState.get(test.getWorld().getServer());
            for(String id:ids){var d=state.player(UUID.fromString(id));check(d.hypothesis.equals("restart:"+id),"Actual server restart retains player research");check(Double.isFinite(d.burden)&&d.records.size()<=32,"Actual restart retains bounded valid state");}
            Files.writeString(Path.of("restart-check.json"),"{\"passed\":"+failures.isEmpty()+",\"players\":"+ids.length+",\"checks\":"+checks+"}");
            if(!failures.isEmpty())throw new AssertionError(failures.toString());System.out.println("Neomagic restart passed: "+checks+" checks");test.complete();
        }catch(Exception error){throw new RuntimeException(error);}
    }
}
