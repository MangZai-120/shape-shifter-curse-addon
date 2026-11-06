package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import static net.ssca.neomagic.NeoModel.*;

/** Real equipped books, actual effect release, player XP and energy conversion. */
public final class NeoFormationAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double value,double expected,String why){check(Math.abs(value-expected)<1e-5,why+": "+value+" / "+expected);}
    private static Blueprint blueprint(Core core){
        return new Blueprint("audit",core,core==Core.LUNAR||core==Core.SPACE?Carrier.SELF:core==Core.SUMMON?Carrier.AREA:Carrier.PROJECTILE,java.util.List.of(),Core.FROST,1,0,8);
    }
    private static void clear(ItemStack book){for(int i=0;i<6;i++)SpellbookData.setFormation(book,i,ItemStack.EMPTY);}
    private static void prepare(ServerPlayerEntity p,Blueprint b){
        var d=NeoRuntime.data(p);d.burden=0;d.cooldowns.clear();d.cooldownStarted.clear();d.known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());
        var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));p.setStackInHand(Hand.MAIN_HAND,wand);
    }
    public static void run(TestContext t,ServerPlayerEntity p,ItemStack book)throws Exception {
        SpellbookData.setLevel(book,3);clear(book);var data=NeoRuntime.data(p);double base=NeoRuntime.profile(p).safety();
        for(int level=1;level<=5;level++){
            SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,level,FormationData.VARIANT_MANA));
            near(NeoRuntime.profile(p).safety(),base+10*level,"Capacity tier adds flat safety");
            near(NeoNetwork.hudState(p).getDouble("S"),base+10*level,"HUD uses the same capacity bonus");
            SpellbookData.setFormation(book,1,FormationData.create(FormationElement.UNIVERSAL,1,FormationData.VARIANT_MANA));
            near(NeoRuntime.profile(p).safety(),base+10*level,"Capacity formations retain highest-only stacking");
            SpellbookData.setFormation(book,1,ItemStack.EMPTY);
        }
        SpellbookData.setLevel(book,1);near(NeoRuntime.profile(p).safety(),base+50,"Capacity bonus does not depend on book level");
        SpellbookData.setLevel(book,3);clear(book);
        Core[] cores={Core.THERMAL,Core.FROST,Core.LUNAR,Core.CURSE,Core.SUMMON,Core.VOID};
        for(int i=0;i<cores.length;i++){
            var same=blueprint(cores[i]);var opposite=blueprint(cores[i^1]);
            for(int level=1;level<=5;level++){
                SpellbookData.setFormation(book,0,FormationData.create(NeoLegacyCasting.element(cores[i]),level));
                near(NeoLegacyCasting.blueprintPower(p,same),1+FormationData.damageBonusPerLevel()*level,"Same-element actual power quote");
                near(NeoLegacyCasting.blueprintPower(p,opposite),1-FormationData.damagePenaltyPerLevel()*level,"Opposing-element power penalty");
                near(NeoLegacyCasting.blueprintCostMultiplier(p,cores[i]),1+FormationData.manaCostPerLevel()*level,"Same-element collapse surcharge");
                near(NeoLegacyCasting.blueprintCostMultiplier(p,cores[i^1]),1+FormationData.manaCostPerLevel()*level,"Opposing-element collapse surcharge");
                near(NeoLegacyCasting.blueprintCostMultiplier(p,Core.SPACE),1+FormationData.manaCostOtherElementPerLevel()*level,"Other-element collapse surcharge");
                check(NeoLegacyCasting.blueprintCooldown(p,same)<NeoRules.cooldown(same),"Same-element cooldown reduction applies");
                check(NeoLegacyCasting.blueprintCooldown(p,opposite)>NeoRules.cooldown(opposite),"Opposing-element cooldown penalty applies");
            }
        }
        clear(book);double plain=NeoLegacyCasting.recoveryMultiplier(book);
        SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,3,FormationData.VARIANT_RECOVERY));
        SpellbookData.setFormation(book,1,FormationData.create(FormationElement.UNIVERSAL,4,FormationData.VARIANT_RECOVERY));
        near(NeoLegacyCasting.recoveryMultiplier(book)/plain,2.4,"Recovery formations add their levels before multiplying natural recovery");
        clear(book);
        data.spellbookMasteryExpTen=60000; // Keep actual effect tests safely below their live N.
        var heal=blueprint(Core.LUNAR);prepare(p,heal);p.setHealth(1);
        SpellbookData.setFormation(book,0,FormationData.create(FormationElement.LUNAR,3));
        net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);
        near(p.getHealth(),1+9*(1+FormationData.damageBonusPerLevel()*3),"Actual authored healing receives its elemental formation power");
        clear(book);prepare(p,heal);p.setHealth(1);int before=data.spellbookMasteryExpTen;
        double normal=NeoLegacyCasting.blueprintQuote(p,heal).normal();
        SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,5,FormationData.VARIANT_EXP));
        net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);
        int reward=SpellbookData.boostedExpTen((int)Math.round(normal*heal.tier()*FormationData.universalExpMultiplier(5)));
        check(data.spellbookMasteryExpTen-before==reward,"Actual authored release receives the experience formation exactly once");
        var stored=new Blueprint("stored audit",Core.LUNAR,Carrier.SELF,java.util.List.of(Control.STORE),Core.FROST,1,0,8);
        prepare(p,stored);before=data.spellbookMasteryExpTen;normal=NeoLegacyCasting.blueprintQuote(p,stored).normal();net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);
        check(NeoEffects.hasStored(p.getUuid())&&data.spellbookMasteryExpTen==before,"Stored effects defer XP until actual release");
        clear(book);NeoEffects.trigger(p);
        reward=SpellbookData.boostedExpTen((int)Math.round(normal*stored.tier()*FormationData.universalExpMultiplier(5)));
        check(data.spellbookMasteryExpTen-before==reward,"Stored effect retains its start-time experience formation snapshot");
        var space=blueprint(Core.SPACE);prepare(p,space);
        SpellbookData.setFormation(book,0,FormationData.create(FormationElement.SPACE,5));
        double multiplier=1+FormationData.spaceRangeBonusPerLevel()*5;
        near(NeoLegacyCasting.blueprintRangeMultiplier(p,space),multiplier,"Authored space range reads equipped formation");
        var blink=(net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceBlinkSpell)SpellRegistry.get("space_blink");
        double defaultRange=blink.getBlinkRange(1);
        near(RuneCastContext.with(RuneModifiers.NONE,multiplier,()->blink.getBlinkRange(1)),defaultRange*multiplier,"Named blink range includes formation independently of rune limits");
        near(blink.getBlinkRange(1),defaultRange,"Range context restores after a cast");
        var origin=t.getAbsolutePos(new net.minecraft.util.math.BlockPos(4,3,4)).toCenterPos();p.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
        net.ssca.neomagic.NeoTestFixtures.start(p,Hand.MAIN_HAND);
        near(p.getZ()-origin.z,4*multiplier,"Authored spatial release actually travels the enhanced distance");
        clear(book);prepare(p,space);
        net.onixary.shapeShifterCurseFabric.mana.ManaUtils.setManaTypeID(p,net.onixary.shapeShifterCurseFabric.mana.ManaRegistries.FAMILIAR_FOX_MANA);
        net.onixary.shapeShifterCurseFabric.mana.ManaUtils.setPlayerMana(p,50);
        SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,1,FormationData.VARIANT_REGEN));
        var tick=UniversalFormationManager.class.getDeclaredMethod("tickPlayer",ServerPlayerEntity.class);tick.setAccessible(true);
        double n=NeoRuntime.profile(p).safety(),threshold=n*(1-FormationData.universalThreshold(1));data.burden=threshold-1;tick.invoke(null,p);
        near(net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerMana(p),50,"Conversion does not drain energy below its collapse threshold");
        data.burden=threshold+1;double prior=data.burden;tick.invoke(null,p);
        near(data.burden,Math.max(0,prior-FormationData.universalBookManaPerSec()),"Conversion actually reduces collapse at its original exchange rate");
        near(net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerMana(p),50-FormationData.universalManaDrainPerSec(),"Conversion charges real form energy exactly once");
        clear(book);data.burden=0;p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var stop=UniversalFormationManager.class.getDeclaredMethod("clearCharging",ServerPlayerEntity.class);stop.setAccessible(true);stop.invoke(null,p);
        System.out.println("NEO_FORMATION_AUDIT_PASS: "+checks+" real book, capacity, damage, cost, cooldown, recovery, energy, XP and distance checks");
    }
}
