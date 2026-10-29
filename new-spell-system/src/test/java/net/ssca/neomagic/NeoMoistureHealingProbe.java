package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.compat.MoistureWaterRecovery;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** 实际游戏对象的治疗量、群体足额结算、持续去重、记录及原生湿润同步回归。 */
public final class NeoMoistureHealingProbe {
    private static int checks;
    public static int checks() {return checks;}
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){check(Math.abs(actual-expected)<1e-4,why+": "+actual+" != "+expected);}
    private static FormSpellStyle style(ServerPlayerEntity caster) {return FormSpellStyle.plan(caster,false,1,0,0,-1);}
    private static void heal(MoistureHealing ledger,LivingEntity target,double amount) {ledger.heal(target,amount,value->target.heal((float)value));}
    private static void reset(ServerPlayerEntity caster,List<PigEntity> allies,int air) {
        caster.setHealth(1);caster.setAir(air);allies.forEach(e->e.setHealth(1));
    }
    private static NeoRuntime.Active active(ServerPlayerEntity caster,Blueprint b) {
        var quote=NeoLegacyCasting.blueprintQuote(caster,b);var session=new Session(b,quote);session.complete=true;session.paid=quote.actual();
        var record=new NeoState.Experiment();record.blueprint=b;record.form=NeoRuntime.form(caster);record.profile=NeoRuntime.profile(caster);
        record.dimension=caster.getWorld().getRegistryKey().getValue().toString();
        var active=new NeoRuntime.Active(session,record,ItemStack.EMPTY,Hand.MAIN_HAND,new NeoEffects.Anchor(caster.getPos(),caster.getUuid()),new Random(7));
        active.style=style(caster);active.style.healing.onPayment(amount->record.moistureSpent+=amount);
        return active;
    }
    public static int run(TestContext t) throws Exception {
        var caster=NeoFormCastingProbe.player(t,FormIdentifiers.AXOLOTL_SP,0);
        var pos=t.getAbsolutePos(new net.minecraft.util.math.BlockPos(10,5,9)).toCenterPos();
        caster.refreshPositionAndAngles(pos.x,pos.y,pos.z,0,90);
        List<PigEntity> allies=new ArrayList<>();
        for(int i=0;i<3;i++) {
            var pig=EntityType.PIG.create(caster.getServerWorld());pig.setAiDisabled(true);pig.setNoGravity(true);
            pig.refreshPositionAndAngles(pos.x+i-1,pos.y,pos.z+2,0,0);caster.getServerWorld().spawnEntity(pig);
            caster.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+pig.getUuid());allies.add(pig);
        }
        reset(caster,allies,10);var self=style(caster);
        heal(self.healing,caster,4);near(caster.getHealth(),7,"Self healing gains fifty percent");
        check(caster.getAir()==0&&self.healing.spent==10,"Exactly ten wetness pays for self healing");
        reset(caster,allies,9);var poor=style(caster);heal(poor.healing,caster,4);
        near(caster.getHealth(),5,"Insufficient wetness falls back to ordinary healing");check(caster.getAir()==9&&poor.healing.spent==0,"Fallback does not partially debit");

        var group=new Blueprint("group heal",Core.LUNAR,Carrier.AREA,List.of(),Core.FROST,1,0,1);
        reset(caster,allies,25);var cast=active(caster,group);
        check(NeoEffects.release(caster,cast),"Actual area healing releases");
        near(caster.getHealth(),4.375,"Area caster receives share times one point five");
        for(var ally:allies)near(ally.getHealth(),4.375,"Each whitelist ally receives enhanced share");
        check(caster.getAir()==0&&cast.record.moistureSpent==25,"Three distinct allies cost ten plus three times five");
        near(cast.record.healing,13.5,"Research records actual enhanced group healing");

        reset(caster,allies,24);var fallback=active(caster,group);NeoEffects.release(caster,fallback);
        near(fallback.record.healing,9,"One point short makes the entire group ordinary");
        check(caster.getAir()==24&&fallback.record.moistureSpent==0,"Group fallback does not charge ten or pick only some allies");

        reset(caster,allies,25);var repeated=style(caster).healing;
        repeated.begin();heal(repeated,caster,1);
        for(var ally:allies){heal(repeated,ally,1);heal(repeated,ally,1);}
        repeated.end();check(caster.getAir()==0&&repeated.spent==25,"Repeated hits in one batch bill each ally once");
        repeated.begin();heal(repeated,caster,1);for(var ally:allies)heal(repeated,ally,1);repeated.end();
        check(caster.getAir()==0&&repeated.spent==25,"Later pulses of the same cast do not repay base or per-target fee");

        reset(caster,allies,40);caster.setHealth(caster.getMaxHealth());allies.forEach(e->e.setHealth(e.getMaxHealth()));
        var full=style(caster).healing;full.begin();heal(full,caster,4);for(var ally:allies)heal(full,ally,4);full.end();
        check(caster.getAir()==40&&full.spent==0,"Full-health actors do not consume wetness");
        reset(caster,allies,15);var delayed=style(caster).healing;heal(delayed,caster,1);
        check(caster.getAir()==5,"First real target pays base once");heal(delayed,allies.get(0),1);
        near(allies.get(0).getHealth(),2.5,"Later distinct ally gains enhancement");check(caster.getAir()==0&&delayed.spent==15,"Later distinct ally pays only five");
        heal(delayed,allies.get(1),1);near(allies.get(1).getHealth(),2,"Unaffordable late target heals ordinarily");
        check(caster.getAir()==0&&delayed.spent==15,"Late fallback never refunds already used enhancement or debits more");

        reset(caster,allies,20);var neutral=EntityType.PIG.create(caster.getServerWorld());neutral.setHealth(1);
        var friendlyOnly=style(caster).healing;heal(friendlyOnly,neutral,4);
        near(neutral.getHealth(),5,"Unlisted actor retains ordinary healing");check(caster.getAir()==20,"Unlisted actor has no enhancement fee");neutral.discard();
        caster.setHealth(1);caster.setAir(20);caster.heal(4);
        near(caster.getHealth(),5,"Native nonspell healing is unchanged");check(caster.getAir()==20,"Native healing does not consume wetness");
        for(var form:List.of(FormIdentifiers.UPGRADE_AXOLOTL,FormIdentifiers.AXOLOTL_FLUORESCENT,FormIdentifiers.AXOLOTL_ALING)) {
            var other=NeoFormCastingProbe.player(t,form,0);check(style(other).healing==null,"No unapproved healing trait on "+form);
        }
        // 用真实Apoli水呼吸power验证浅水恢复，触水标志仅由本隔离测试模拟。
        var source=new Identifier("ssca_neomagic","moisture_test");
        var powers=io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(caster);
        powers.addPower(io.github.apace100.apoli.power.PowerTypeRegistry.get(new Identifier("shape-shifter-curse","form_axolotl_3_water_breathing")),source);
        var touching=net.minecraft.entity.Entity.class.getDeclaredField("touchingWater");touching.setAccessible(true);touching.setBoolean(caster,true);
        caster.setAir(100);caster.setHealth(1);MoistureWaterRecovery.capture(caster);
        heal(style(caster).healing,caster,2);check(caster.getAir()==90,"Spell debits inside captured tick");
        MoistureWaterRecovery.restore(caster);check(caster.getAir()==94,"Shallow recovery restores four without refunding the ten-point spell fee");
        touching.setBoolean(caster,false);powers.removeAllPowersFromSource(source);
        allies.forEach(Entity::discard);

        var spell=SpellRegistry.get("lunar_mend");int[] seenAir={-1};var record=new NeoState.Experiment[1];
        check(SpellChannelManager.start(caster,spell,ScrollData.create("lunar_mend"),1,false,972,10,40,()->true,v->{
            caster.setHealth(5);caster.setAir(10);
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,200));
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,200));
            spell.cast(caster,4,false,1);near(caster.getHealth(),11,"Real named spell uses the scoped healing trait");seenAir[0]=caster.getAir();
            check(caster.getStatusEffects().stream().filter(e->e.getEffectType().getCategory()==net.minecraft.entity.effect.StatusEffectCategory.HARMFUL).count()==1,"Purification count does not gain the healing multiplier");
        },cd->{}),"Actual named healing channel starts");
        record[0]=NeoLegacyCasting.active(caster).record;
        check(record[0].moistureSpent==0,"Preparation has not paid wetness");
        t.waitAndRun(33,()->{
            check(seenAir[0]==0&&record[0].moistureSpent==10,"Named spell records exactly one actual fee");
        });
        // 游戏内真实出生保护为60tick，过后再验证伤害分支。
        t.waitAndRun(75,()->{
            var reverse=active(caster,new Blueprint("inverted",Core.LUNAR,Carrier.SELF,List.of(),Core.FROST,1,0,1));
            reverse.session.reversed=true;caster.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            caster.setHealth(20);caster.timeUntilRegen=0;caster.setAir(20);caster.getAbilities().invulnerable=false;
            NeoEffects.release(caster,reverse);near(caster.getHealth(),11,"Inverted healing keeps ordinary damage");
            check(caster.getAir()==20&&reverse.record.moistureSpent==0,"Inversion never consumes wetness or amplifies damage");caster.getAbilities().invulnerable=true;
            System.out.println("NEO_MOISTURE_HEAL_PASS checks="+checks+" (self, group, fallback, deduplication, deferred targets, shallow water, named spell, purification and inversion)");
        });
        return checks;
    }
}
