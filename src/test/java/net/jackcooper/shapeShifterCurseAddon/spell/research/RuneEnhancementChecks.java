package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneRole.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers.Stat.*;

/** Public rules exercised through full formations, including physical ring boundaries. */
public final class RuneEnhancementChecks {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    public static void run(){
        for(int seed=0;seed<64;seed++){
            RuneLanguage language=RuneLanguage.generate(seed);
            check(new HashSet<>(Arrays.stream(language.meanings()).boxed().toList()).size()==18,"all glyphs unique");
            for(var recipe:SlottedSpellRecipes.all())for(int level=1;level<=5;level++){
                int[] slots=RuneLayout.foundation(recipe,level,language);
                var r=RuneBuildEvaluator.evaluate(level,slots,language,SlottedSpellRecipes.all());
                check(r.valid()&&r.recipe().equals(recipe)&&r.stability()==100,"foundation uniquely recognized, no forced modifiers");
                check(Arrays.stream(r.modifiers().values()).allMatch(v->v==0),"empty enhancement neutral");
            }
            var gainMerge=build(language,"flame_nova",2,GAIN,MERGE);
            check(gainMerge.contributions().get(3).modifiers().get(POWER)==13
                    &&gainMerge.contributions().get(4).modifiers().get(POWER)==15,"slot contributions show rounded synergy, not original bonuses");
            checkContributions(gainMerge);
            check(rulesValid(gainMerge)&&gainMerge.stability()==68,"partial draft diagnoses synergy stability extra consumption");
            check(gainMerge.modifiers().get(POWER)==28&&gainMerge.modifiers().get(CD)==8,"25 percent rounded per rune");
            check(gainMerge.modifiers().mana(25)==32&&gainMerge.modifiers().time(20)==25&&gainMerge.modifiers().cooldown(100)==108,"real quote formula");
            var sandwich=build(language,"flame_nova",2,MERGE,GAIN,MERGE);
            check(rulesValid(sandwich)&&sandwich.modifiers().get(POWER)==43&&sandwich.modifiers().get(CD)==16&&sandwich.stability()==48,"effect applies once, edge costs apply twice");
            var mixed=build(language,"flame_nova",2,MERGE,GAIN,DISABLE);
            check(mixed.contributions().get(4).modifiers().get(POWER)==6
                    &&mixed.contributions().get(5).modifiers().get(TIME)==-4,"slot contributions include simultaneous synergy and suppression");
            checkContributions(mixed);
            check(rulesValid(mixed)&&mixed.modifiers().get(POWER)==15&&mixed.modifiers().get(TIME)==1,"combine rational factors before one rounding");
            var soft=build(language,"flame_nova",2,BRIDGE,SPLIT);
            check(rulesValid(soft)&&soft.modifiers().get(AREA)==15&&soft.inactive().get(3),"soft conflict diagnoses missing distance as inactive");
            for(RuneRole[] pair:new RuneRole[][]{{SPLIT,MERGE},{STORE,STABLE},{DISABLE,STORE},{DISABLE,BRIDGE},{GAIN,GAIN}}){
                check(!rulesValid(build(language,"fire_bolt",2,pair)),"hard conflict diagnosed independently of ring completeness");
                check(!rulesValid(build(language,"fire_bolt",2,pair[1],pair[0])),"hard conflict symmetric");
                int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);slots[3]=language.glyph(pair[0]);slots[7]=language.glyph(pair[1]);
                check(!rulesValid(evaluate(2,slots,language)),"closed seam counts as adjacent");
                slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),4,language);slots[11]=language.glyph(pair[0]);slots[18]=language.glyph(pair[1]);
                check(!rulesValid(evaluate(4,slots,language)),"level four full outer ring closes across seam");
                slots[18]=-1;slots[13]=language.glyph(pair[1]);
                check(!evaluate(4,slots,language).valid()&&rulesValid(evaluate(4,slots,language)),"empty separators no longer permit construction despite no adjacency conflict");
            }
            check(rulesValid(build(language,"fire_bolt",2,STABLE,STABLE,STABLE)),"three stable runes have no run conflict");
            check(!rulesValid(build(language,"fire_bolt",2,STABLE,STABLE,STABLE,STABLE)),"four stable runes diagnosed independently of ring completeness");
            int[] stable=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);for(int i:new int[]{3,4,6,7})stable[i]=language.glyph(STABLE);
            check(!rulesValid(evaluate(2,stable,language)),"stable run across cyclic seam");
            int[] gap=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);gap[3]=gap[5]=language.glyph(STORE);
            check(!evaluate(2,gap,language).valid()&&rulesValid(evaluate(2,gap,language)),"empty socket cannot bypass the complete-ring gate");
            var inactive=build(language,"frost_armor",2,STORE);
            check(inactive.contributions().get(3).inactiveReason().equals("unsupported")
                    &&inactive.contributions().get(3).modifiers().get(MANA)==12
                    &&inactive.contributions().get(3).modifiers().get(TIME)==8,"inactive slot explains unsupported property and retains real costs");
            checkContributions(inactive);
            check(rulesValid(inactive)&&inactive.inactive().get(3)&&inactive.modifiers().get(DAMAGE)==0&&inactive.stability()==82&&inactive.modifiers().mana(25)==29,"inactive damage rune still pays all costs");
            var speed=build(language,"fire_bolt",2,SOURCE);
            check(speed.modifiers().scale(2,SPEED)==2.3,"actual projectile speed scale");
            var burn=build(language,"fire_bolt",2,FIRE);
            check(burn.modifiers().duration(100,BURN)==120,"matching element burn");
            var armor=build(language,"frost_armor",2,ICE);
            check(armor.modifiers().duration(400,SHIELD_DURATION)==460,"shield duration consumer");
            var disable=build(language,"flame_nova",2,DISABLE);
            check(rulesValid(disable)&&disable.modifiers().time(20)==12&&disable.modifiers().power(100,true,false)==88&&disable.modifiers().scale(10,AREA)==8.8,"disable changes preparation, power and existing area");
            var heal=build(language,"lunar_mend",2,GAIN,RECOVER,LUNAR);
            check(rulesValid(heal)&&heal.modifiers().power(100,false,true)==132,"healing and power combine once");
            var mark=build(language,"curse_mark",2,CURSE,INSIGHT);
            check(rulesValid(mark)&&mark.modifiers().duration(160,MARK,NEGATIVE)==216,"mark and negative durations add before scaling");
            var summon=build(language,"summon_lunar_spirit",2,SUMMON);
            check(rulesValid(summon)&&summon.modifiers().duration(600,SUMMON_DURATION)==690,"summon lifetime, no added contract slots");
            var wide=build(language,"meteor",2,BRIDGE,SPLIT);
            check(rulesValid(wide)&&wide.modifiers().get(DISTANCE)==15&&wide.modifiers().get(AREA)==15,"both applicable soft-conflict effects reduced");
            int[] threshold=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),5,language);
            RuneRole[] separated={STORE,GAIN,CONVERT,STORE,GAIN,CONVERT};
            for(int i=0;i<separated.length;i++)threshold[19+i*2]=language.glyph(separated[i]);
            check(rulesValid(evaluate(5,threshold,language))&&evaluate(5,threshold,language).stability()==20,"minimum stability is inclusive in partial diagnostics");
            threshold[20]=language.glyph(FIRE);
            check(!evaluate(5,threshold,language).valid()&&evaluate(5,threshold,language).stability()==10,"below threshold rejected");
            int[] overloaded=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),5,language);
            for(int i=19;i<31;i++)overloaded[i]=language.glyph(i%2==0?GAIN:STORE);
            check(!evaluate(5,overloaded,language).valid()&&evaluate(5,overloaded,language).stability()<20,"stability blocks trial and construction");
            overloaded[19]=language.glyph(STABLE);overloaded[20]=-1;overloaded[30]=-1;
            check(evaluate(5,overloaded,language).stability()>-80,"stable restores net ten without removing other costs");
            checkWholeRings(language);
            checkResonances(language);
            for(RuneRole role:RuneRole.values())checkContributions(build(language,"fire_bolt",2,role));
        }
        check(RuneModifiers.rounded(25,2)==13&&RuneModifiers.rounded(-5,2)==-3&&RuneModifiers.rounded(50,8)==6,"signed half up");
        check(RuneModifiers.NONE.time(0)==0&&RuneModifiers.NONE.time(2)==2,"unmodified instant and short spells unchanged");
        System.out.println("Rune enhancement checks passed: "+checks);
    }
    private static RuneBuildEvaluator.Result build(RuneLanguage l,String spell,int level,RuneRole... roles){
        int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get(spell),level,l);for(int i=0;i<roles.length;i++)slots[RuneLayout.baseSize(level)+i]=l.glyph(roles[i]);return evaluate(level,slots,l);
    }
    private static RuneBuildEvaluator.Result evaluate(int level,int[] slots,RuneLanguage language){return RuneBuildEvaluator.evaluate(level,slots,language,SlottedSpellRecipes.all());}
    /** Partial drafts still calculate effects for display, independently of the new completion gate. */
    private static boolean rulesValid(RuneBuildEvaluator.Result result){
        return result.recipe()!=null&&result.problems().stream().allMatch(p->p.rule().equals("outer_incomplete"))
                &&result.stability()>=RuneBuildEvaluator.MIN_STABILITY;
    }
    private static void checkContributions(RuneBuildEvaluator.Result result){
        int[] values=new int[RuneModifiers.Stat.values().length];int stability=100;
        for(var contribution:result.contributions().values()){
            for(var stat:RuneModifiers.Stat.values())values[stat.ordinal()]+=contribution.modifiers().get(stat);
            stability+=contribution.stability();
        }
        for(var stat:RuneModifiers.Stat.values())values[stat.ordinal()]+=result.interactionModifiers().get(stat);
        stability+=result.interactionStability();
        check(Arrays.equals(values,result.modifiers().values())&&stability==result.stability(),
                "server slot contributions and edge costs exactly reproduce actual formation totals");
    }
    private static void checkWholeRings(RuneLanguage language){
        for(int level=2;level<=5;level++){
            int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),level,language);
            int start=RuneLayout.baseSize(level);
            slots[start]=language.glyph(STABLE);
            var partial=evaluate(level,slots,language);
            check(RuneLayout.validDraft(level,slots)&&!partial.valid(),"partial ring remains a saveable draft but cannot run or complete");
            check(partial.problems().stream().filter(p->p.rule().equals("outer_incomplete")).count()==slots.length-start-1
                    &&partial.problems().get(0).amount()==slots.length-start-1,"every empty outer socket is diagnosed with total missing count");
            for(int i=start;i<slots.length;i++)slots[i]=language.glyph((i-start)%2==0?STABLE:SOURCE);
            check(evaluate(level,slots,language).valid(),"entire ring can be legally filled");
            for(int i=start;i<slots.length;i++){
                int[] missing=slots.clone();missing[i]=-1;
                check(!evaluate(level,missing,language).valid(),"any missing outer position blocks the otherwise legal full ring");
            }
            Arrays.fill(slots,start,slots.length,-1);
            check(evaluate(level,slots,language).valid()&&evaluate(level,slots,language).stability()==100,"clearing the whole ring restores neutral validity");
        }
        var burst=build(language,"flame_nova",2,GAIN,MERGE,STABLE,FIRE,STABLE);
        check(burst.valid()&&burst.stability()==80&&burst.modifiers().get(POWER)==28
                &&burst.modifiers().get(AREA)==-8&&burst.modifiers().get(BURN)==20
                &&burst.modifiers().get(MANA)==28&&burst.modifiers().get(FLAT_MANA)==5
                &&burst.modifiers().get(TIME)==9&&burst.modifiers().get(CD)==8,"wiki full-ring burst example");
        var stored=build(language,"fire_bolt",2,STORE,SOURCE,STABLE,STABLE,SOURCE);
        check(stored.valid()&&stored.stability()==80&&stored.modifiers().get(DAMAGE)==26
                &&stored.modifiers().get(SPEED)==35&&stored.modifiers().get(MANA)==28
                &&stored.modifiers().get(FLAT_MANA)==5&&stored.modifiers().get(TIME)==14&&stored.modifiers().get(CD)==10,
                "full-ring heavy projectile grants and charges once across two edges, speed caps at cast time");
        var quick=build(language,"fire_bolt",2,DISABLE,STABLE,DISABLE,STABLE,SOURCE);
        check(quick.valid()&&quick.stability()==104&&quick.modifiers().get(POWER)==-24
                &&quick.modifiers().get(SPEED)==15&&quick.modifiers().get(MANA)==10
                &&quick.modifiers().get(FLAT_MANA)==5&&quick.modifiers().get(TIME)==-12,"wiki full-ring quick-cast example");
        check(build(language,"fire_bolt",2,STABLE,STABLE,SOURCE,GAIN,STABLE).valid(),"three stable runes across seam allowed");
        check(!build(language,"fire_bolt",2,STABLE,STABLE,SOURCE,STABLE,STABLE).valid(),"four stable runes across seam rejected even with full ring");
        var exact=build(language,"fire_bolt",2,STORE,GAIN,MERGE,FIRE,BRIDGE);
        check(exact.valid()&&exact.stability()==20,"complete ring at exact stability threshold is allowed");
        check(!build(language,"fire_bolt",2,STORE,GAIN,MERGE,VOID,BRIDGE).valid(),"complete ring below stability threshold is blocked");
    }
    private static void checkResonances(RuneLanguage language){
        var burst=build(language,"fire_bolt",2,GAIN,STORE);
        check(rulesValid(burst)&&burst.modifiers().get(POWER)==10&&burst.modifiers().get(DAMAGE)==32
                &&burst.modifiers().get(TIME)==18&&burst.modifiers().get(CD)==15&&burst.stability()==62,
                "burst adds twelve damage points, eight preparation ticks, fifteen cooldown percent and eight load");
        var projectile=build(language,"fire_bolt",2,SOURCE,STORE);
        check(rulesValid(projectile)&&projectile.modifiers().get(DAMAGE)==26&&projectile.modifiers().get(SPEED)==20
                &&projectile.modifiers().get(TIME)==10&&projectile.modifiers().get(CD)==10&&projectile.stability()==68,
                "heavy projectile retains storage preparation while adding damage, speed and cooldown");
        var pressure=build(language,"frost_nova",2,SPLIT,CONVERT);
        check(rulesValid(pressure)&&pressure.modifiers().get(AREA)==28&&pressure.modifiers().get(NEGATIVE)==25
                &&pressure.modifiers().get(MANA)==28&&pressure.modifiers().get(CD)==12&&pressure.stability()==70,
                "pressure has real area and duration consumers with extra mana and cooldown");
        var mending=build(language,"lunar_mend",2,RECOVER,MERGE);
        check(rulesValid(mending)&&mending.modifiers().get(POWER)==12&&mending.modifiers().get(HEAL)==24
                &&mending.modifiers().get(TIME)==10&&mending.modifiers().get(CD)==10&&mending.stability()==70,
                "focused mending improves healing once and pays preparation and cooldown");
        for(var result:List.of(burst,projectile,pressure,mending))checkContributions(result);
        var repeated=build(language,"fire_bolt",2,GAIN,STORE,GAIN,STABLE,STABLE);
        check(repeated.valid()&&repeated.modifiers().get(DAMAGE)==32&&repeated.modifiers().get(POWER)==20
                &&repeated.modifiers().get(TIME)==22&&repeated.modifiers().get(CD)==15&&repeated.stability()==72,
                "repeated burst edges grant and charge once, protection discounts the package only once");
        check(repeated.resonances().size()==1&&repeated.resonances().get(0).edges()==2
                &&repeated.resonances().get(0).protection()==2,"all triggering edges are recorded without duplicate discounts");
        var repeatedHeal=build(language,"lunar_mend",2,RECOVER,MERGE,RECOVER,STABLE,STABLE);
        check(repeatedHeal.valid()&&repeatedHeal.modifiers().get(HEAL)==36&&repeatedHeal.modifiers().get(CD)==10
                &&repeatedHeal.stability()==80,"repeated healing pays one unprotected package");
        var repeatedPressure=build(language,"frost_nova",2,SPLIT,CONVERT,SPLIT,STABLE,STABLE);
        check(repeatedPressure.valid()&&repeatedPressure.modifiers().get(AREA)==48&&repeatedPressure.modifiers().get(NEGATIVE)==25
                &&repeatedPressure.modifiers().get(MANA)==42&&repeatedPressure.modifiers().get(CD)==12&&repeatedPressure.stability()==76,
                "repeated pressure edges do not duplicate mana, load or effects");
        var protectedBurst=build(language,"fire_bolt",2,STABLE,GAIN,STORE,INSIGHT,SOURCE);
        var distantStable=build(language,"fire_bolt",2,GAIN,STORE,INSIGHT,STABLE,SOURCE);
        check(protectedBurst.valid()&&distantStable.valid()&&protectedBurst.stability()==distantStable.stability()+2
                &&Arrays.equals(protectedBurst.modifiers().values(),distantStable.modifiers().values()),
                "stable placement only discounts local load and cannot remove any casting costs");
        var protectedHeal=build(language,"lunar_mend",2,RECOVER,MERGE,STABLE,LUNAR,STABLE);
        check(protectedHeal.valid()&&protectedHeal.resonances().get(0).protection()==2,"stable next to merge protects healing load");
        var unsupported=build(language,"frost_armor",2,GAIN,STORE);
        check(unsupported.resonances().size()==1&&!unsupported.resonances().get(0).active()
                &&unsupported.modifiers().get(DAMAGE)==0&&unsupported.modifiers().get(CD)==0
                &&unsupported.modifiers().get(TIME)==10&&unsupported.stability()==70,
                "unsupported burst retains rune costs but never charges extra resonance costs");
        check(build(language,"flame_nova",2,SOURCE,STORE).modifiers().get(CD)==0,
                "projectile resonance requires a real speed consumer");
        check(build(language,"fire_bolt",2,SPLIT,CONVERT).modifiers().get(MANA)==18
                &&build(language,"fire_bolt",2,SPLIT,CONVERT).modifiers().get(CD)==0,"unsupported pressure has no extra mana or cooldown");
        check(build(language,"fire_bolt",2,RECOVER,MERGE).modifiers().get(HEAL)==0
                &&build(language,"fire_bolt",2,RECOVER,MERGE).modifiers().get(CD)==0,"healing synergy cannot increase damage");
        for(var result:List.of(repeated,repeatedHeal,repeatedPressure,protectedBurst,protectedHeal,unsupported))checkContributions(result);
        checkRingSymmetry(language,"fire_bolt",new RuneRole[]{STABLE,GAIN,STORE,INSIGHT,SOURCE});
        checkRingSymmetry(language,"lunar_mend",new RuneRole[]{RECOVER,MERGE,STABLE,LUNAR,STABLE});
        checkRingSymmetry(language,"frost_nova",new RuneRole[]{SPLIT,CONVERT,STABLE,ICE,STABLE});
        var capped=build(language,"fire_bolt",4,GAIN,MERGE,STABLE,GAIN,STORE,SOURCE,STABLE,STABLE);
        check(capped.valid()&&capped.modifiers().rawPower(true,false)==76&&capped.modifiers().effectivePower(true,false)==60
                &&capped.modifiers().power(100,true,false)==160&&capped.modifiers().get(CD)==33&&capped.stability()==50,
                "different resonances combine through existing consumers, caps never erase casting costs");
        checkContributions(capped);
        for(var recipe:SlottedSpellRecipes.all()){
            var caps=RuneCapabilities.of(recipe.spell());
            for(var roles:new RuneRole[][]{{GAIN,STORE},{SOURCE,STORE},{SPLIT,CONVERT},{RECOVER,MERGE}}){
                var result=build(language,recipe.spell(),2,roles);
                var entry=result.resonances().get(0);
                check(entry.active()==entry.rule().supports(caps),"only registered capability consumers activate a package");
                checkContributions(result);
            }
        }
    }
    private static void checkRingSymmetry(RuneLanguage language,String spell,RuneRole[] roles){
        var reference=build(language,spell,2,roles);
        for(int rotation=0;rotation<roles.length;rotation++)for(int direction:new int[]{1,-1}){
            RuneRole[] shifted=new RuneRole[roles.length];
            for(int i=0;i<roles.length;i++)shifted[i]=roles[Math.floorMod(rotation+direction*i,roles.length)];
            var result=build(language,spell,2,shifted);
            check(result.valid()==reference.valid()&&result.stability()==reference.stability()
                    &&Arrays.equals(result.modifiers().values(),reference.modifiers().values())
                    &&Arrays.equals(result.baseModifiers().values(),reference.baseModifiers().values()),
                    "rotating or reversing the ring including its seam cannot change resonance benefits, costs or protection");
            checkContributions(result);
        }
    }
}
