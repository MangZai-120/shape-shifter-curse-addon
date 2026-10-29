package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Real native hit, channel/blueprint quotes, actual thorn lifespan/shatter and saved entities. */
public final class NeoFrostSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static void near(double a,double b,String reason){check(Math.abs(a-b)<1e-6,reason+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,Identifier form)throws Exception {
        var p=NeoFormCastingProbe.player(t,form,0);NeoRuntime.data(p).spellbookMasteryExpTen=120000;return p;
    }
    private static LivingEntity target(ServerPlayerEntity p){
        var pig=EntityType.PIG.create(p.getServerWorld());pig.setNoGravity(true);pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(p.getX(),p.getEyeY(),p.getZ()+3,0,0);p.getServerWorld().spawnEntity(pig);return pig;
    }
    private static void hit(ServerPlayerEntity p,LivingEntity target)throws Exception {
        var ball=new FrostBallEntity(p.getWorld(),p);
        var method=FrostBallEntity.class.getDeclaredMethod("onEntityHit",EntityHitResult.class);method.setAccessible(true);
        method.invoke(ball,new EntityHitResult(target));ball.discard();
    }
    private static void thorns(ServerPlayerEntity p,int count){for(int i=0;i<count;i++)FrostSpikeManager.refundThorn(p);}
    private static FormSpellStyle style(ServerPlayerEntity p,String path){return FormSpellStyle.plan(p,SpellRegistry.get(path),1,8,8);}
    private static boolean channel(ServerPlayerEntity p,String path,boolean solo,int[] effects){
        var spell=SpellRegistry.get(path);
        return SpellChannelManager.start(p,spell,ScrollData.create(path),1,solo,8821,100,100,()->true,
                at->{spell.cast(p,1,solo,1);effects[0]++;},cd->{});
    }
    private static void form(ServerPlayerEntity p,Identifier form){RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm=RegPlayerForms.playerForms.get(form);NeoRuntime.refreshForm(p);}
    private static NeoState.Experiment blueprint(ServerPlayerEntity p,Blueprint b){
        var d=NeoRuntime.data(p);d.known.addAll(NeoMagic.SAMPLES.keySet());
        var wand=new ItemStack(NeoMagic.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));p.setStackInHand(Hand.MAIN_HAND,wand);
        NeoRuntime.start(p,Hand.MAIN_HAND);return d.records.get(0);
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        var snow=player(t,FormIdentifiers.SNOW_FOX_SP);var enemy=target(snow);
        near(FrostSpellLink.multiplier(snow,true),1,"No free preparation before native hit");
        hit(snow,enemy);near(FrostSpellLink.multiplier(snow,true),.85,"Actual native frost ball status hit grants fifteen percent");
        long expiry=FrostSpellLink.readyUntil(snow);check(expiry==snow.getWorld().getTime()+60,"Preparation lasts sixty actual world ticks");
        hit(snow,enemy);near(FrostSpellLink.multiplier(snow,true),.85,"Repeated native hits do not stack");
        near(NeoLegacyCasting.quote(snow,SpellRegistry.get("frost_spike"),1,8,100).actual(),85,"Ice cost preview includes reduction");
        near(NeoLegacyCasting.quote(snow,SpellRegistry.get("fire_bolt"),1,8,100).actual(),100,"Nonice preview is unchanged");
        check(FrostSpellLink.readyUntil(snow)==expiry,"Cost previews do not consume preparation");
        check(style(snow,"fire_bolt").pay()&&FrostSpellLink.readyUntil(snow)==expiry,"Nonice acceptance does not consume preparation");
        var paid=style(snow,"frost_spike");check(paid.pay()&&paid.pay(),"Preparation pays idempotently");near(paid.collapseMultiplier,.85,"Accepted quote keeps prepared reduction");
        check(FrostSpellLink.readyUntil(snow)==0,"Preparation is consumed once");near(style(snow,"frost_spike").collapseMultiplier,1,"Next spell needs another native hit");

        var friend=target(snow);snow.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+friend.getUuid());hit(snow,friend);
        check(FrostSpellLink.readyUntil(snow)==0,"Whitelisted hits do not grant preparation");
        var wrong=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);hit(wrong,target(wrong));near(FrostSpellLink.multiplier(wrong,true),1,"Shared native hit does not grant SP buff to frostspine");
        FrostSpellLink.onNativeHit(snow,enemy);form(snow,FormIdentifiers.FAMILIAR_FOX_SP);form(snow,FormIdentifiers.SNOW_FOX_SP);
        near(FrostSpellLink.multiplier(snow,true),1,"Leaving and returning to form clears preparation");

        var timed=player(t,FormIdentifiers.SNOW_FOX_SP);var timedTarget=target(timed);FrostSpellLink.onNativeHit(timed,timedTarget);
        t.waitAndRun(30,()->FrostSpellLink.onNativeHit(timed,timedTarget));
        t.waitAndRun(62,()->near(FrostSpellLink.multiplier(timed,true),.85,"Later native hit refreshes window"));
        t.waitAndRun(92,()->near(FrostSpellLink.multiplier(timed,true),1,"Refreshed preparation expires automatically"));

        var named=player(t,FormIdentifiers.SNOW_FOX_SP);FrostSpellLink.onNativeHit(named,target(named));int[] namedEffects={0};
        check(channel(named,"ice_barrage",false,namedEffects),"Prepared named barrage starts");
        var namedRecord=NeoRuntime.data(named).records.get(0);near(namedRecord.actual,85,"Named channel snapshots discount before consuming buff");
        check(namedRecord.snowReadyUsed&&FrostSpellLink.readyUntil(named)==0,"Named record tracks preparation payment");
        t.waitAndRun(20,()->{check(namedEffects[0]==1,"Barrage releases once");check(FrostSpellLink.readyUntil(named)==0,"Spell projectiles never refresh native-skill preparation");});

        var cancelled=player(t,FormIdentifiers.SNOW_FOX_SP);FrostSpellLink.onNativeHit(cancelled,target(cancelled));int[] stopped={0};
        check(channel(cancelled,"frost_armor",false,stopped),"Prepared ice channel can be interrupted");SpellChannelManager.onFullyControlled(cancelled);
        check(stopped[0]==0&&FrostSpellLink.readyUntil(cancelled)==0,"Interruption does not return paid preparation");

        var spine=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);thorns(spine,5);
        check(FrostSpikeManager.getHoverCount(spine)==5,"Five actual hovering thorns created");
        near(NeoLegacyCasting.quote(spine,SpellRegistry.get("frost_spike"),1,8,100).actual(),75,"Five reductions add to exactly twenty-five percent");
        var before=FrostSpikeManager.hoveringThorns(spine);check(style(spine,"fire_bolt").pay(),"Nonice cast accepts normally");
        check(before.stream().allMatch(e->e.getHoverTicks()==0),"Nonice spell and previews do not spend lifespan");
        for(int use=1;use<=4;use++){
            var payment=style(spine,"ice_barrage");near(payment.collapseMultiplier,.75,"Every current thorn contributes before spending");
            check(payment.pay()&&payment.pay(),"Thorn fee is paid once per spell, regardless of projectile count");
            if(use<4){final int expected=use*360;check(before.stream().allMatch(e->!e.isRemoved()&&e.getHoverTicks()==expected),"Each acceptance spends exactly eighteen seconds per thorn");}
        }
        check(before.stream().allMatch(e->e.isRemoved())&&FrostSpikeManager.getHoverCount(spine)==0,"Fourth use grants full discount then shatters all six-second remainders");
        near(style(spine,"frost_spike").collapseMultiplier,1,"No thorns means ordinary cost");

        var low=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);thorns(low,5);var lowThorns=FrostSpikeManager.hoveringThorns(low);lowThorns.forEach(e->e.restoreHoverTicks(1180));
        BlockPos water=BlockPos.ofFloored(FrostThornEntity.hoverTarget(low,lowThorns.get(0).getSlot()));low.getServerWorld().setBlockState(water,Blocks.WATER.getDefaultState());
        int[] lowEffects={0};check(channel(low,"ice_barrage",false,lowEffects),"Channel starts with nearly expired thorns");
        var lowRecord=NeoRuntime.data(low).records.get(0);near(lowRecord.actual,75,"Channel retains full discount after fee destroys all contributors");
        check(lowRecord.frostThornsSpent==5&&lowRecord.frostLifetimeTicksSpent==100,"Research records five thorns and their actual twenty-tick remainders");
        check(low.getServerWorld().getBlockState(water).isOf(Blocks.ICE),"Fee-triggered shatter preserves native freezing of nearby water");
        t.waitAndRun(20,()->{check(lowEffects[0]==1,"Discounted barrage still releases after thorns shatter");var shards=low.getServerWorld().getEntitiesByClass(SpellFrostSpikeEntity.class,low.getBoundingBox().expand(32),e->e.getOwner()==low);check(shards.size()==3,"Three spell projectiles share one five-thorn payment");});

        var saved=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);thorns(saved,1);var thorn=FrostSpikeManager.hoveringThorns(saved).get(0);thorn.spendHoverLifetime(720);
        NbtCompound nbt=thorn.writeNbt(new NbtCompound());var restored=new FrostThornEntity(saved.getWorld(),saved);restored.readNbt(nbt);
        check(restored.getHoverTicks()==720&&restored.getStage()==1,"Stored entity restores spent lifespan and appearance immediately");restored.discard();
        FrostSpikeManager.onDisconnect(saved);FrostSpikeManager.onJoin(saved);
        check(FrostSpikeManager.hoveringThorns(saved).size()==1&&FrostSpikeManager.hoveringThorns(saved).get(0).getHoverTicks()==720,"Disconnect/rejoin rebuilds thorns without refunding spent life");

        var solo=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);thorns(solo,1);int[] soloEffects={0};
        check(channel(solo,"frost_spike",true,soloEffects),"Standalone scroll shares frost payment");
        near(NeoRuntime.data(solo).records.get(0).actual,SpellRegistry.get("frost_spike").getManaCost()*.95,"Solo quote applies its own base cost and one-thorn reduction");
        check(FrostSpikeManager.hoveringThorns(solo).get(0).getHoverTicks()==360,"Standalone scroll spends lifetime once");

        var blue=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE);thorns(blue,5);
        var ice=new Blueprint("ice",Core.FROST,Carrier.PROJECTILE,List.of(),Core.THERMAL,1,0,20);var base=NeoRules.quote(ice,0);
        near(NeoLegacyCasting.blueprintQuote(blue,ice).actual(),base.actual()*.75,"Blueprint preview matches additive discount");
        var blueRecord=blueprint(blue,ice);near(blueRecord.actual,base.actual()*.75,"Actual blueprint keeps pre-payment quote");
        check(blueRecord.frostThornsSpent==5&&blueRecord.frostLifetimeTicksSpent==1800,"Blueprint spends all current thorns once");
        var converted=new Blueprint("convert",Core.THERMAL,Carrier.PROJECTILE,List.of(Control.CONVERT),Core.FROST,1,0,20);
        near(NeoLegacyCasting.blueprintQuote(blue,converted).actual(),NeoRules.quote(converted,0).actual()*.75,"Conversion into ice qualifies by final output");
        var fire=new Blueprint("not ice",Core.FROST,Carrier.PROJECTILE,List.of(Control.CONVERT),Core.THERMAL,1,0,20);
        near(NeoLegacyCasting.blueprintQuote(blue,fire).actual(),NeoRules.quote(fire,0).actual(),"Conversion out of ice does not receive ice reduction");
        var hud=NeoNetwork.hudState(blue);near(hud.getDouble("FrostSpellMultiplier"),.75,"Authoritative HUD carries current thorn multiplier");
        var buffHud=NeoNetwork.hudState(timed);near(buffHud.getDouble("FrostSpellMultiplier"),.85,"Authoritative HUD carries prepared snow multiplier");
        check(buffHud.getLong("FrostReadyUntil")==FrostSpellLink.readyUntil(timed),"HUD synchronizes absolute expiry without per-tick countdown payloads");
        var roundtrip=NeoState.JSON.fromJson(NeoState.JSON.toJson(lowRecord),NeoState.Experiment.class);
        check(roundtrip.frostThornsSpent==5&&roundtrip.frostLifetimeTicksSpent==100,"Research retains actual resource cost across persistence");

        t.waitAndRun(95,()->{System.out.println("NEO_FROST_LINK_PASS checks="+checks+" (native hit, whitelist, refresh/expiry, quotes, named/solo/blueprints, actual life, shatter/freezing, persistence and HUD)");t.complete();});
    }
}
