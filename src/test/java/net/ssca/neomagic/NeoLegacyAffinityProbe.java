package net.ssca.neomagic;

import dev.emi.trinkets.api.TrinketsApi;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Real Trinkets, form components, blueprint effects, resource ledgers and stored/delayed effects. */
public final class NeoLegacyAffinityProbe {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){check(Math.abs(actual-expected)<1e-5,why+": "+actual+" != "+expected);}
    private static ServerPlayerEntity player(TestContext t,Identifier form,boolean equipped)throws Exception {
        var p=NeoFormCastingProbe.player(t,form,50);
        if(equipped)book(p,true);
        NeoRuntime.data(p).burden=100;NeoRuntime.data(p).recovery=120;
        return p;
    }
    private static void book(ServerPlayerEntity p,boolean equipped){
        var slot=TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook");
        slot.setStack(0,equipped?new ItemStack(SscAddon.MOON_DUST_SPELLBOOK):ItemStack.EMPTY);
        check((SpellCastManager.getEquippedBook(p)!=null)==equipped,"Real accessory slot eligibility");
    }
    private static Blueprint b(Core core){return new Blueprint("affinity",core,Carrier.LOCK,List.of(),Core.THERMAL,1,0,16);}
    private static LivingEntity target(ServerPlayerEntity p){
        var pig=EntityType.PIG.create(p.getServerWorld());pig.setNoGravity(true);pig.setAiDisabled(true);
        pig.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);pig.setHealth(200);
        pig.refreshPositionAndAngles(p.getX(),p.getEyeY()-pig.getHeight()*.5,p.getZ()+3,0,0);p.getServerWorld().spawnEntity(pig);return pig;
    }
    private static NeoRuntime.Active active(ServerPlayerEntity p,Blueprint b,LivingEntity target){
        var q=NeoLegacyCasting.blueprintQuote(p,b);var s=new Session(b,q);s.complete=true;s.paid=q.actual();
        var r=new NeoState.Experiment();r.blueprint=b;r.paid=s.paid;r.dimension=p.getWorld().getRegistryKey().getValue().toString();
        var a=new NeoRuntime.Active(s,r,new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND),Hand.MAIN_HAND,
                new NeoEffects.Anchor(target==null?p.getPos():target.getPos(),target==null?null:target.getUuid()),new Random(0));
        a.powerMultiplier=NeoLegacyCasting.blueprintPower(p,b);NeoLegacyCasting.snapshotBlueprintAffinity(p,a);return a;
    }
    private static void release(ServerPlayerEntity p,NeoRuntime.Active a){check(NeoEffects.release(p,a),"Blueprint releases");}
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        var fire=b(Core.THERMAL);var ice=b(Core.FROST);var lunar=b(Core.LUNAR);var curse=b(Core.CURSE);var space=b(Core.SPACE);
        var familiar=player(t,FormIdentifiers.FAMILIAR_FOX_SP,true);
        near(NeoLegacyCasting.blueprintQuote(familiar,fire).actual(),NeoRules.quote(fire,0).actual()*.85*1.05,"Legacy cost and new acceleration surcharge compose");
        book(familiar,false);
        near(NeoLegacyCasting.blueprintQuote(familiar,fire).actual(),NeoRules.quote(fire,0).actual()*1.05,"Body style remains; book-only affinity stops without book");
        book(familiar,true);
        var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(new Blueprint("real",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,1,20,16)));
        familiar.setStackInHand(Hand.MAIN_HAND,wand);NeoRuntime.data(familiar).known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());NeoRuntime.data(familiar).burden=0;
        net.ssca.neomagic.NeoTestFixtures.start(familiar,Hand.MAIN_HAND);var started=NeoRuntime.ACTIVE.get(familiar.getUuid());
        check(started!=null&&started.legacyBook,"Production start snapshots equipped-book eligibility");
        near(started.session.quote.actual(),NeoRules.quote(started.session.blueprint,0).actual()*.85*1.05,"Production quote retains both layers after native mana payment");
        NeoRuntime.cancel(familiar,"probe_cancel");check(started.refundCastId==null&&!started.legacyReleased,"Cancelled preparation never opens refund budget or grants release bonus");

        var spine=player(t,FormIdentifiers.SNOW_FOX_FROSTSPINE,true);for(int i=0;i<5;i++)FrostSpikeManager.refundThorn(spine);
        near(NeoLegacyCasting.blueprintQuote(spine,ice).actual(),NeoRules.quote(ice,0).actual()*.75,"New five-thorn discount retained");
        near(NeoLegacyCasting.blueprintPower(spine,ice),1.15,"Legacy ice power retained alongside thorn discount");
        var toIce=new Blueprint("converted",Core.THERMAL,Carrier.LOCK,List.of(Control.CONVERT),Core.FROST,1,0,16);
        near(NeoLegacyCasting.blueprintPower(spine,toIce),1.15,"Converted ice receives final-output power affinity");

        var wet=player(t,FormIdentifiers.AXOLOTL_SP,true);
        near(FormCastingStyle.tidalCostMultiplier(wet),1,"Dry form receives no tidal discount");
        BlockPos pos=wet.getBlockPos();for(BlockPos cell:BlockPos.iterate(pos.add(-1,-1,-1),pos.add(1,4,1)))wet.getServerWorld().setBlockState(cell,Blocks.WATER.getDefaultState());
        wet.baseTick();wet.baseTick();check(wet.isSubmergedInWater(),"Actual world water updates submerged state");
        near(NeoLegacyCasting.blueprintQuote(wet,fire).actual(),NeoRules.quote(fire,0).actual()*.85,"Book-wearing submerged blueprint receives legacy tidal discount");
        near(FormCastingStyle.applyTidalDiscount(wet,FormationElement.FIRE,100),85,"Named-spell integer tidal behavior retained");
        check(NeoNetwork.hudState(wet).getBoolean("BookEquipped"),"HUD synchronizes book gate for dynamic previews");
        book(wet,false);near(NeoLegacyCasting.blueprintQuote(wet,fire).actual(),NeoRules.quote(fire,0).actual(),"No book means no legacy tidal discount");
        check(!NeoNetwork.hudState(wet).getBoolean("BookEquipped"),"HUD clears book gate on unequip");

        var wind=player(t,FormIdentifiers.OCELOT_SP,true);
        var toSpace=new Blueprint("converted",Core.THERMAL,Carrier.LOCK,List.of(Control.CONVERT),Core.SPACE,1,0,16);
        check(NeoLegacyCasting.blueprintCooldown(wind,toSpace)==Math.round(NeoRules.cooldown(toSpace)*.9f),"Converted space CD uses final school");
        var fromSpace=new Blueprint("converted",Core.SPACE,Carrier.LOCK,List.of(Control.CONVERT),Core.THERMAL,1,0,16);
        check(NeoLegacyCasting.blueprintCooldown(wind,fromSpace)==NeoRules.cooldown(fromSpace),"Conversion out of space loses space-specific CD affinity");
        var warp=new Blueprint("stored",Core.SPACE,Carrier.SELF,List.of(Control.STORE),Core.FROST,1,0,16);
        var stored=active(wind,warp,wind);release(wind,stored);
        near(NeoRuntime.data(wind).burden,100,"Storing is not a successful-release refund");
        check(stored.refundCastId==null&&!stored.legacyReleased,"Stored spell opens no premature ledger");
        NeoEffects.trigger(wind);double afterWarp=100-Math.round((int)Math.ceil(stored.session.paid)*.15f);
        near(NeoRuntime.data(wind).burden,afterWarp,"Actual stored space release refunds fifteen percent of actual payment");
        NeoEffects.trigger(wind);NeoLegacyCasting.blueprintReleased(wind,stored);
        near(NeoRuntime.data(wind).burden,afterWarp,"Stored trigger and release notifications cannot duplicate refund");
        var lost=active(wind,space,null);check(!NeoEffects.release(wind,lost),"Lost lock target prevents release");
        near(NeoRuntime.data(wind).burden,afterWarp,"Failed lock never grants successful-release return");

        var fallen=player(t,FormIdentifiers.FALLEN_ALLAY_SP,true);var enemy=target(fallen);var first=active(fallen,curse,enemy);
        release(fallen,first);near(NeoRuntime.data(fallen).burden,100,"Own newly applied curse does not count as preexisting harmful effect");
        int baseTicks=(int)(9*first.session.quote.plan().retention()*20);
        check(enemy.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==Math.round(baseTicks*1.15f),"Book blueprint retains fallen curse duration");
        var cursed=target(fallen);cursed.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,200));
        var second=active(fallen,curse,cursed);release(fallen,second);
        near(NeoRuntime.data(fallen).burden,100-Math.round((int)Math.ceil(second.session.paid)*.25f),"Preexisting harmful effect grants legacy quarter-cost return");
        book(fallen,false);var plainTarget=target(fallen);var plain=active(fallen,curse,plainTarget);release(fallen,plain);
        check(plain.refundCastId==null&&plainTarget.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==baseTicks,"No book means ordinary curse duration and no ledger");
        var spider=player(t,FormIdentifiers.SPIDER_SALTICIDAE,true);var spiderTarget=target(spider);var spiderCast=active(spider,curse,spiderTarget);release(spider,spiderCast);
        check(spiderTarget.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==Math.round(baseTicks*1.10f),"Spider curse duration remains ten percent");

        var golden=player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP,true);var burning=target(golden);burning.setOnFireFor(10);var burn=active(golden,fire,burning);release(golden,burn);
        near(NeoRuntime.data(golden).burden,100-Math.round((int)Math.ceil(burn.session.paid)*.2f),"Already burning target grants twenty percent return");
        var killer=player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP,true);var fragile=target(killer);fragile.setHealth(1);var kill=active(killer,fire,fragile);release(killer,kill);
        near(NeoRuntime.data(killer).burden,100-Math.round((int)Math.ceil(kill.session.paid)*.5f),"Actual fire kill returns half actual payment");
        var immune=player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,true);immune.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var invulnerable=target(immune);invulnerable.setInvulnerable(true);var blocked=active(immune,b(Core.VOID),invulnerable);release(immune,blocked);
        near(NeoRuntime.data(immune).burden,100,"Rejected damage is not an effective hit refund");

        var dream=player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,true);var victim=target(dream);var drain=active(dream,b(Core.VOID),victim);release(dream,drain);
        double afterHit=100-Math.round((int)Math.ceil(drain.session.paid)*.5f);
        near(NeoRuntime.data(dream).burden,afterHit,"Nightmare successful hit retains half-cost return");
        UUID budget=drain.refundCastId;NeoLegacyCasting.beginBlueprintEffect(dream,drain);check(budget.equals(drain.refundCastId),"All branches retain one ledger ID");
        // A later hit passes the global 20t gate but cannot exceed this cast's half-cost total.
        t.waitAndRun(21,()->{double before=NeoRuntime.data(dream).burden;NeoLegacyCasting.blueprintHit(dream,drain,target(dream),false);near(NeoRuntime.data(dream).burden,before,"Delayed branch shares exhausted half-cost budget");});

        var detached=player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,false);var detachedCast=active(detached,b(Core.VOID),target(detached));
        book(detached,true);release(detached,detachedCast);near(NeoRuntime.data(detached).burden,100,"Equipping after acceptance cannot add a missing book-affinity ledger");
        var removed=player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,true);var removedCast=active(removed,b(Core.VOID),target(removed));book(removed,false);release(removed,removedCast);
        near(NeoRuntime.data(removed).burden,100,"Removing book before impact blocks hit refund");

        var delayed=player(t,FormIdentifiers.WILD_CAT_NIGHTMARE,true);var delayedTarget=target(delayed);
        var projectile=new Blueprint("delayed",Core.VOID,Carrier.PROJECTILE,List.of(),Core.FROST,1,0,16);
        var flight=active(delayed,projectile,delayedTarget);release(delayed,flight);
        near(NeoRuntime.data(delayed).burden,100,"Projectile launch alone is not an effective hit");
        t.waitAndRun(8,()->{
            check(flight.record.damage>0&&flight.record.pending==0,"Actual delayed projectile collision completes");
            near(NeoRuntime.data(delayed).burden,100-Math.round((int)Math.ceil(flight.session.paid)*.5f),"Delayed hit uses its original cost ledger during natural-recovery delay");
        });

        var bat=player(t,FormIdentifiers.BAT_DESMODUS,false);near(FormCastingStyle.lunarMendBonus(bat,8),10,"Intrinsic named LunarMend bonus still works without book");
        var allay=player(t,FormIdentifiers.ALLAY_SP,true);
        check(FormAffinity.bonusSpellLevel(allay,FormationElement.SUMMON,4)==5&&FormAffinity.manaCostLevel(allay,FormationElement.SUMMON,5)==4,"Native summon level bonus and highest-tier discount retained");
        var cat=player(t,FormIdentifiers.WILD_CAT_SP,true);
        near(NeoLegacyCasting.blueprintCostMultiplier(cat,Core.THERMAL),.75,"Wildcat cost affinity retained");
        check(NeoLegacyCasting.blueprintCooldown(cat,fire)==Math.round(NeoRules.cooldown(fire)*.75f),"Wildcat cooldown affinity retained");
        near(NeoLegacyCasting.blueprintPower(player(t,FormIdentifiers.SPIDER_MOON_WEAVER,true),lunar),1.15,"Moon weaver lunar power affinity retained");

        t.waitAndRun(24,()->{System.out.println("NEO_LEGACY_AFFINITY_PASS checks="+checks+" (book gating, additive styles, tidal, conversion, duration, valid hits, caps, storage, cancellation and native exceptions)");t.complete();});
    }
}
