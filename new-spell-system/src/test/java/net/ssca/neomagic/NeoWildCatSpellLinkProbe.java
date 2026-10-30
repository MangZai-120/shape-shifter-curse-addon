package net.ssca.neomagic;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.power.TrueInvisibilityAbilityPower;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Actual primary power, accepted native/solo/blueprint channels and exactly-once reveal. */
public final class NeoWildCatSpellLinkProbe {
    private static int checks;
    private static boolean finished;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static ServerPlayerEntity player(TestContext t)throws Exception {
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.WILD_CAT_SP,0);NeoRuntime.data(p).spellbookMasteryExpTen=120000;
        var type=PowerTypeRegistry.get(new Identifier("my_addon","true_invisibility"));check(type!=null,"Real primary power registered");
        PowerHolderComponent.KEY.get(p).addPower(type,new Identifier("ssc_addon","wild_cat_test"));return p;
    }
    private static TrueInvisibilityAbilityPower power(ServerPlayerEntity p){return PowerHolderComponent.getPowers(p,TrueInvisibilityAbilityPower.class).get(0);}
    private static void invisible(ServerPlayerEntity p){check(p.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Actual primary invisibility active");}
    private static final class FixedSpell extends Spell {
        private final int duration;
        FixedSpell(int ticks){super(new Identifier("ssc_addon","frost_armor"),SpellRarity.WHITE);duration=ticks;}
        @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity p,int level,boolean solo){return new SpellCastingRules.Profile(duration,.8,false);}
        @Override public SpellCastingRules.Mode getCastingMode(){return SpellCastingRules.Mode.AUTOMATIC;}
        @Override public void cast(ServerPlayerEntity p,float value,boolean solo){}
    }
    private static boolean cast(ServerPlayerEntity p,boolean solo,int[] effects){
        return SpellChannelManager.start(p,new FixedSpell(140),ScrollData.create("frost_armor"),1,solo,441,10,80,()->true,
                at->{check(!p.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Revealed before actual effect");effects[0]++;},cd->{});
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        var book=player(t);var solo=player(t);var interrupted=player(t);var blue=player(t);var ordinary=player(t);var stale=player(t);var rejected=player(t);
        // GameTest mock connections do not call ServerPlayerEntity.playerTick like a real network connection.
        ServerTickEvents.START_SERVER_TICK.register(server->{if(!finished)for(var p:List.of(book,solo,interrupted,blue,ordinary,stale,rejected))p.playerTick();});
        for(var p:List.of(book,solo,interrupted,blue,stale,rejected)){power(p).onUse();check(p.hasStatusEffect(SscAddon.PRE_INVISIBILITY),"Primary preparation accepted");}
        int[] bookEffects={0},soloEffects={0};
        FormSpellStyle[] oldCover={null};int[] primaryCd={0};
        t.waitAndRun(25,()->{
            for(var p:List.of(book,solo,interrupted,blue))invisible(p);
            check(cast(book,false,bookEffects),"Book channel accepted");check(cast(solo,true,soloEffects),"Solo channel accepted");check(cast(interrupted,false,new int[]{0}),"Interrupt fixture accepted");
            var b=new Blueprint("cloak",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.THERMAL,1,80,20);
            NeoRuntime.data(blue).known.addAll(NeoMagic.SAMPLES.keySet());var wand=new ItemStack(NeoMagic.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));blue.setStackInHand(Hand.MAIN_HAND,wand);NeoRuntime.start(blue,Hand.MAIN_HAND);check(NeoRuntime.ACTIVE.containsKey(blue.getUuid()),"Actual blueprint channel accepted");
            ordinary.addStatusEffect(new StatusEffectInstance(SscAddon.TRUE_INVISIBILITY,200,1));check(cast(ordinary,false,new int[]{0}),"Non-primary invisibility may cast normally");
            var invalid=new Spell(new Identifier("ssc_addon","missing_test_spell"),SpellRarity.WHITE){@Override public void cast(ServerPlayerEntity p,float value,boolean alone){}};
            check(!SpellChannelManager.start(rejected,invalid,ScrollData.create("missing_test_spell"),1,false,442,10,80,()->true,at->{},cd->{}),"Rejected preflight never reserves invisibility");
            oldCover[0]=FormSpellStyle.plan(stale,SpellRegistry.get("frost_armor"),1,0,0);check(oldCover[0].pay(),"Old cast cover accepted");
            power(stale).onUse();check(!stale.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)&&stale.hasStatusEffect(SscAddon.GUARANTEED_CRIT),"Manual primary cancellation keeps its original buffs");
            SkillCooldowns.reset(stale,power(stale).powerIdentifier());power(stale).onUse();check(stale.hasStatusEffect(SscAddon.PRE_INVISIBILITY),"A fresh primary cast begins after reset");
        });
        t.waitAndRun(45,()->{
            book.handSwinging=true;solo.handSwinging=true;ordinary.handSwinging=true;
            power(book).tick();power(solo).tick();power(ordinary).tick();invisible(book);invisible(solo);
            check(!ordinary.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Ink-style/non-primary invisibility gets no spell protection");
            SpellChannelManager.onPurified(interrupted);check(!interrupted.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Interrupted channel reveals immediately");
            primaryCd[0]=SkillCooldowns.remaining(interrupted,power(interrupted).powerIdentifier());check(primaryCd[0]>0,"Main skill cooldown starts on reveal");
            check(!cast(book,false,new int[]{0}),"A second cast cannot replace the active cover");
        });
        t.waitAndRun(47,()->{SpellChannelManager.onPurified(interrupted);check(SkillCooldowns.remaining(interrupted,power(interrupted).powerIdentifier())==primaryCd[0]-2,"Repeated spell cleanup does not restart primary cooldown");});
        t.waitAndRun(55,()->{invisible(stale);oldCover[0].endCloak();invisible(stale);});
        t.waitAndRun(65,()->invisible(blue));
        t.waitAndRun(130,()->{invisible(book);invisible(solo);check(!rejected.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Rejected cast does not extend the original five-second invisibility");check(!blue.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Blueprint releases and reveals after its valid eighty-tick preparation");check(SpellChannelManager.isCasting(book),"Long native preparation still running after original invisibility duration");});
        t.waitAndRun(175,()->{
            check(bookEffects[0]==1&&soloEffects[0]==1,"Book and solo each release exactly once");
            for(var p:List.of(book,solo,blue))check(!p.hasStatusEffect(SscAddon.TRUE_INVISIBILITY),"Completion removes covered invisibility");
            check(!NeoRuntime.ACTIVE.containsKey(blue.getUuid()),"Blueprint completes and releases its invisibility cover");
            finished=true;System.out.println("NEO_WILD_CAT_LINK_PASS checks="+checks);t.complete();
        });
    }
}
