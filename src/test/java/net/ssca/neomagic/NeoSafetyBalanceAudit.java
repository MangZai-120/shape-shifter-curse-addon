package net.ssca.neomagic;

import dev.emi.trinkets.api.TrinketsApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.BeepSheepFormManager;
import net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager;
import net.jackcooper.shapeShifterCurseAddon.resource.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import static net.ssca.neomagic.NeoModel.*;

/** Uses production quotes, profiles, timing, formations and Session; not a second balance formula. */
public final class NeoSafetyBalanceAudit {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" != "+b);}
    private static final Random FORCE_FAILURE=new Random(0){@Override public double nextDouble(){return 0;}@Override public int nextInt(int bound){return 0;}};
    private static boolean failureReachable(Blueprint b,Quote quote,int duration,int lock,double initial,Profile profile){
        var session=new Session(b,quote,duration,lock);double current=initial;
        for(int tick=0;tick<=duration;tick++){
            current+=session.advance(tick,current,profile,FORCE_FAILURE);
            if(session.failed)return true;
        }
        return false;
    }
    private static ItemStack book(ServerPlayerEntity p){
        var book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setLevel(book,3);
        TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook").setStack(0,book);return book;
    }
    private static void clear(ItemStack book){for(int i=0;i<6;i++)SpellbookData.setFormation(book,i,ItemStack.EMPTY);}
    private static void sheep(ServerPlayerEntity p,ItemStack book){
        for(var id:List.of(new Identifier("shape-shifter-curse:axolotl_0"),FormIdentifiers.FAMILIAR_FOX_RED,FormIdentifiers.GOLDEN_SANDSTORM_SP)){
            FormUtils._setForm(p,RegPlayerForms.playerForms.get(id));NeoRuntime.refreshForm(p);clear(book);
            NeoRuntime.data(p).spellbookMasteryExpTen=6000;
            SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,5,FormationData.VARIANT_MANA));
            double base=NeoRules.profile(id.toString()).safety(),before=NeoRuntime.profile(p).safety();near(before,base+100,"Growth and capacity counted once");
            check(p.addStatusEffect(new StatusEffectInstance(SscAddon.SHEEP_FORM,200)),"Real sheep effect accepted");NeoRuntime.refreshForm(p);
            check(NeoRuntime.form(p).equals(FormIdentifiers.SHEEP_FORM.toString()),"Effect actually switches form");
            near(NeoRuntime.bodyProfile(p).safety(),base,"Sheep inherits pre-transform base");near(NeoRuntime.profile(p).safety(),before,"Sheep retains safety without duplicate bonuses");
            near(NeoNetwork.hudState(p).getDouble("BodyS"),base,"HUD marker uses inherited body");near(NeoNetwork.hudState(p).getDouble("S"),before,"HUD total agrees");
            near(NeoRuntime.bodyProfile(p).failure(),NeoRules.profile(FormIdentifiers.SHEEP_FORM.toString()).failure(),"Only safety inherits, no original casting traits");
            var saved=new NbtCompound();BeepSheepFormManager.writePlayerNbt(p,saved);BeepSheepFormManager.onPlayerRemoved(p);BeepSheepFormManager.readPlayerNbt(p,saved);
            near(NeoRuntime.profile(p).safety(),before,"Persisted sheep snapshot restores inherited safety");
            var slot=TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook");slot.setStack(0,ItemStack.EMPTY);
            near(NeoRuntime.profile(p).safety(),base+50,"Removing book removes only its formation bonus");slot.setStack(0,book);
            p.removeStatusEffect(SscAddon.SHEEP_FORM);NeoRuntime.refreshForm(p);near(NeoRuntime.profile(p).safety(),before,"Original safety restored after real sheep expiration");
        }
        NeoRuntime.data(p).spellbookMasteryExpTen=0;clear(book);
    }
    private static void resources(ServerPlayerEntity p,boolean full){
        ResourceBars.set(p,BarKeys.VANILLA_MANA,full?1000:0);p.setAir(full?p.getMaxAir():0);
        if(ResourceBars.has(p,BarKeys.BAT_BLOOD))ResourceBars.set(p,BarKeys.BAT_BLOOD,full?1000:0);
    }
    private static void run(TestContext t)throws Exception{
        if(io.github.apace100.apoli.Apoli.config==null)io.github.apace100.apoli.Apoli.config=new io.github.apace100.apoli.util.ApoliConfig();
        var p=NeoFormCastingProbe.player(t,new Identifier("shape-shifter-curse:original_shifter"),0);var book=book(p);sheep(p,book);
        List<Map<String,Object>> rows=new ArrayList<>();List<Map<String,Object>> bodies=new ArrayList<>();
        for(var form:RegPlayerForms.playerForms.values()){
            String formId=form.getFormID().toString();if(formId.equals(FormIdentifiers.SHEEP_FORM.toString()))continue;
            FormUtils._setForm(p,form);NeoRuntime.refreshForm(p);NeoRuntime.data(p).spellbookMasteryExpTen=0;clear(book);
            double n=NeoRuntime.profile(p).safety();near(n,NeoRules.profile(formId).safety(),"Loaded capacity matches configured form "+formId);
            bodies.add(Map.of("form",formId,"safety",n,"failure",NeoRuntime.profile(p).failure(),"offset",NeoRuntime.profile(p).offset()));
            var thorns=new ArrayList<net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity>();
            for(String scenario:List.of("plain","resource","five_formations")){
                clear(book);boolean full=scenario.equals("resource");resources(p,full);FrostSpellLink.clear(p);
                if(full&&form.getFormID().equals(FormIdentifiers.SNOW_FOX_SP)){
                    var pig=EntityType.PIG.create(p.getWorld());p.getServerWorld().spawnEntity(pig);FrostSpellLink.onNativeHit(p,pig);pig.discard();
                    check(FrostSpellLink.multiplier(p,true)<1,"Actual native-hit readiness enabled");
                }
                if(full&&form.getFormID().equals(FormIdentifiers.SNOW_FOX_FROSTSPINE)){
                    for(int i=0;i<5;i++)FrostSpikeManager.refundThorn(p);thorns.addAll(FrostSpikeManager.hoveringThorns(p));
                    check(thorns.size()==5,"Five real hovering thorns prepared");
                }
                for(var spell:SpellRegistry.all())for(int level=1;level<=spell.getMaxLevel();level++){
                    clear(book);if(scenario.equals("five_formations"))for(int slot=0;slot<5;slot++)SpellbookData.setFormation(book,slot,FormationData.create(spell.getElement(),5));
                    String key="spell:"+spell.getId();NeoRuntime.data(p).progress.remove(key);
                    var scroll=ScrollData.create(spell.getId().getPath(),level);
                    int original=spell.getCastingProfile(p,level,false).ticks();
                    var modifiers=net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme.modifiers(p,scroll,level);
                    var time=FormSpellStyle.timing(p,original,modifiers.time(original),spell.getLockInTick());
                    int normal=SpellNumbers.finalManaCost(spell,book,p,level,scroll);
                    var quote=NeoLegacyCasting.quote(p,spell,level,time.duration(),normal);
                    NeoRuntime.data(p).progress.put(key,new double[]{100000,100000,100000,100000});
                    var mastered=NeoLegacyCasting.quote(p,spell,level,time.duration(),normal);NeoRuntime.data(p).progress.remove(key);
                    var b=NeoLegacyCasting.descriptor(spell,level,time.duration());var profile=NeoRuntime.profile(p);
                    double predicted=failureChance(b,quote.actual(),quote.actual(),profile);
                    double actual=predicted>0&&failureReachable(b,quote,time.duration(),time.lockTick(),0,profile)?predicted:0;
                    double crowded=failureChance(b,n*.8+quote.actual(),quote.actual(),profile);
                    if(crowded>0&&!failureReachable(b,quote,time.duration(),time.lockTick(),n*.8,profile))crowded=0;
                    int cd=SpellNumbers.finalCooldownTicks(spell,level,1,FormationData.sumCooldownMultiplier(book,spell.getElement()),FormAffinity.cooldownMultiplier(p,spell.getElement()));
                    double regen=NeoRules.recoveryRate*NeoLegacyCasting.recoveryMultiplier(book);
                    var row=new LinkedHashMap<String,Object>();row.put("form",formId);row.put("spell",spell.getId().getPath());row.put("level",level);row.put("scenario",scenario);
                    row.put("safety",n);row.put("cost",quote.actual());row.put("mastered",mastered.actual());row.put("floor",quote.floor());row.put("element",spell.getElement().name());
                    row.put("duration",time.duration());row.put("lock",time.lockTick());row.put("forecast_failure",predicted);row.put("actual_failure_from_empty",actual);row.put("failure_from_80pct",crowded);
                    row.put("stage_after",CollapsePenalties.stage(quote.actual(),n));row.put("cooldown",cd);row.put("regen_per_second",regen*20);
                    row.put("net_per_cycle",Math.max(0,quote.actual()-Math.max(0,cd-NeoRules.recoveryDelay)*regen));
                    row.put("mastered_net_per_cycle",Math.max(0,mastered.actual()-Math.max(0,cd-NeoRules.recoveryDelay)*regen));
                    row.put("recovery_seconds",NeoRules.recoveryDelay/20.0+quote.actual()/(regen*20));
                    check(Double.isFinite(quote.actual())&&quote.actual()>=0&&mastered.actual()<=quote.actual(),"Actual quote and mastery remain valid");
                    rows.add(row);
                }
                for(var thorn:thorns)thorn.discard();thorns.clear();FrostSpellLink.clear(p);
            }
        }
        Files.writeString(Path.of("safety-balance-audit61.json"),NeoState.JSON.toJson(Map.of("forms",bodies,"rows",rows,"checks",checks,"rules",NeoRules.version())));
        clear(book);NeoRuntime.data(p).burden=0;
        System.out.println("Safety balance audit: "+checks+" checks passed, "+bodies.size()+" body profiles, "+rows.size()+" production quote scenarios; real sheep lifecycle/persistence verified.");t.complete();
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
}
