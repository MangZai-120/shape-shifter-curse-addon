package net.ssca.neomagic;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.ability.NineLivesManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Real attributes, native nine-lives resource/revival, channel clocks and actual special-spell effects. */
public final class NeoOcelotSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,Identifier form)throws Exception {
        var p=NeoFormCastingProbe.player(t,form,50);NeoRuntime.data(p).spellbookMasteryExpTen=120000;return p;
    }
    private static void novaResource(ServerPlayerEntity p,int lives){
        var id=FormIdentifiers.OCELOT_NOVA_NINE_LIVES;
        PowerHolderComponent.KEY.get(p).addPower(PowerTypeRegistry.get(id),new Identifier("ssc_addon","probe_nine_lives"));
        PowerUtils.setResourceValueAndSync(p,id,lives);
        check(PowerUtils.getResourceValue(p,id)==lives,"Real loaded nine-lives resource is attached");
    }
    private static ServerPlayerEntity revived(TestContext t)throws Exception {
        var p=player(t,FormIdentifiers.OCELOT_NOVA);novaResource(p,3);p.setHealth(1);
        check(NineLivesManager.tryRevive(p),"Successful native nine-lives revival");
        check(PowerUtils.getResourceValue(p,FormIdentifiers.OCELOT_NOVA_NINE_LIVES)==2&&p.getHealth()>1,"Revival consumes one life and restores health");
        check(OcelotSpellLink.quickUntil(p)==p.getWorld().getTime()+100,"Revival opens exactly five seconds");return p;
    }
    private static int[] channel(ServerPlayerEntity p,String id,boolean solo,int token){
        var spell=SpellRegistry.get(id);var scroll=ScrollData.create(id);int[] result={0,0,0};
        check(SpellChannelManager.start(p,spell,scroll,1,solo,token,40,100,()->true,
                target->{result[0]++;spell.castAtTarget(p,10,solo,1,scroll,target);},cd->{result[1]++;result[2]=cd;},()->result[2]++),"Production channel accepts "+id);
        return result;
    }
    private static boolean validate(ServerPlayerEntity p)throws Exception {
        var f=SpellChannelManager.class.getDeclaredField("ACTIVE");f.setAccessible(true);Object c=((Map<?,?>)f.get(null)).get(p.getUuid());
        var m=SpellChannelManager.class.getDeclaredMethod("ensureValid",c.getClass());m.setAccessible(true);return (boolean)m.invoke(null,c);
    }
    @SuppressWarnings("unchecked")
    private static void lockDomain(ServerPlayerEntity p)throws Exception {
        var f=DomainManager.class.getDeclaredField("FIELDS");f.setAccessible(true);var fields=(Map<Object,Object>)f.get(null);Object old=fields.get(p.getUuid());
        var type=old.getClass();var components=type.getRecordComponents();Object[] args=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        for(int i=0;i<args.length;i++){types[i]=components[i].getType();var getter=components[i].getAccessor();getter.setAccessible(true);args[i]=components[i].getName().equals("startTick")?p.getServer().getTicks()-DomainRules.expandStartTick():getter.invoke(old);}
        var ctor=type.getDeclaredConstructor(types);ctor.setAccessible(true);fields.put(p.getUuid(),ctor.newInstance(args));
    }
    private static NeoState.Experiment blueprint(ServerPlayerEntity p,int preparation){
        var b=new Blueprint("ocelot",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,1,preparation,16);
        NeoRuntime.data(p).known.addAll(NeoMagic.SAMPLES.keySet());var wand=new ItemStack(NeoMagic.WAND);
        wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));p.setStackInHand(Hand.MAIN_HAND,wand);
        NeoRuntime.start(p,Hand.MAIN_HAND);return NeoRuntime.data(p).records.get(0);
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        var wind=player(t,FormIdentifiers.OCELOT_SP);double walking=wind.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        channel(wind,"domain",false,1);near(wind.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking*.3,"Wind has exactly thirty percent walking attribute");
        check(!SpellChannelManager.isImmobile(wind),"Special spell no longer forbids wind movement or jump");
        wind.requestTeleport(wind.getX()+6,wind.getY(),wind.getZ());check(DomainManager.canContinue(wind)&&validate(wind),"Wind domain tolerates more than three blocks of movement");
        check(SpellChannelManager.onSkillAction(wind)&&!SpellChannelManager.isCasting(wind),"Skill action interrupts unlocked wind channel");
        near(wind.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking,"Skill interruption removes casting modifier");

        var sprint=player(t,FormIdentifiers.OCELOT_SP);channel(sprint,"frost_armor",true,2);sprint.setSprinting(true);
        check(!validate(sprint)&&!SpellChannelManager.isCasting(sprint),"Sprint interrupts even a normally self-uninterruptible scroll");sprint.setSprinting(false);
        near(sprint.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking,"Sprint interruption leaves no slow modifier");

        var locked=player(t,FormIdentifiers.OCELOT_SP);channel(locked,"domain",false,3);lockDomain(locked);
        check(SpellChannelManager.releaseLocked(locked)&&OcelotSpellLink.windLocked(locked),"Wind domain is genuinely in protected stage");
        check(!SpellChannelManager.onSkillAction(locked)&&SpellChannelManager.isCasting(locked),"Protected phase rejects skill instead of interrupting spell");
        var skill=SkillCastManager.get(locked.getServerWorld());var cfg=new SkillCastManager.ResolvedConfig(100,0,SkillCastManager.START_ON_CAST);
        check(skill.begin(locked,"ssc_addon:probe_wind_skill",cfg)<0,"Direct skill service cannot bypass protected wind phase");
        locked.setSprinting(true);check(validate(locked)&&!locked.isSprinting()&&SpellChannelManager.isCasting(locked),"Protected phase disables sprint on server and retains channel");
        near(locked.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking*.3,"Protected phase still permits thirty-percent walking");
        SpellChannelManager.onFullyControlled(locked);

        var finaleWind=player(t,FormIdentifiers.OCELOT_SP);channel(finaleWind,"death_finale",false,4);
        finaleWind.requestTeleport(finaleWind.getX()+7,finaleWind.getY()-4,finaleWind.getZ());
        SpellRegistry.get("death_finale").tickChannel(finaleWind,1,ItemStack.EMPTY,2);
        check(DeathFinaleManager.canContinue(finaleWind)&&validate(finaleWind),"Wind death finale accepts horizontal and vertical displacement");SpellChannelManager.onFullyControlled(finaleWind);
        var ordinary=player(t,FormIdentifiers.FAMILIAR_FOX_SP);channel(ordinary,"death_finale",false,5);
        check(SpellChannelManager.isImmobile(ordinary),"Other forms retain special-spell immobilization");ordinary.requestTeleport(ordinary.getX()+6,ordinary.getY(),ordinary.getZ());
        check(!DeathFinaleManager.canContinue(ordinary),"Other forms retain death-finale anchor restriction");SpellChannelManager.onFullyControlled(ordinary);

        var blueWind=player(t,FormIdentifiers.OCELOT_SP);var blueRecord=blueprint(blueWind,20);
        near(blueWind.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking*.3,"Blueprint applies the same wind walking multiplier");
        check(SpellChannelManager.onSkillAction(blueWind)&&!NeoRuntime.ACTIVE.containsKey(blueWind.getUuid()),"Skill action interrupts wind blueprint too");
        check(blueRecord.result.equals("interrupted"),"Blueprint interruption is recorded");near(blueWind.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking,"Blueprint cancellation clears movement modifier");
        var blueSprint=player(t,FormIdentifiers.OCELOT_SP);blueprint(blueSprint,20);blueSprint.setSprinting(true);
        t.waitAndRun(2,()->{check(!NeoRuntime.ACTIVE.containsKey(blueSprint.getUuid()),"Server tick interrupts sprinting blueprint");blueSprint.setSprinting(false);near(blueSprint.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking,"Sprinting blueprint has no leftover modifier");});
        var blueFinish=player(t,FormIdentifiers.OCELOT_SP);blueprint(blueFinish,4);
        t.waitAndRun(6,()->{check(!NeoRuntime.ACTIVE.containsKey(blueFinish.getUuid()),"Ordinary wind blueprint completes");near(blueFinish.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),walking,"Successful blueprint removes wind slowdown");});

        var noLife=player(t,FormIdentifiers.OCELOT_NOVA);check(!NineLivesManager.tryRevive(noLife)&&!OcelotSpellLink.quickCast(noLife),"No life means no revival shortcut");
        var fresh=player(t,FormIdentifiers.OCELOT_NOVA);check(FormSpellStyle.timing(fresh,40,40,-1).duration()==40,"Normal spawn/respawn is not a nine-lives shortcut");
        var nova=revived(t);long until=OcelotSpellLink.quickUntil(nova);
        check(!NineLivesManager.tryRevive(nova)&&OcelotSpellLink.quickUntil(nova)==until,"Rejected repeat revival does not refresh window");
        var instant=channel(nova,"fire_bolt",false,6);check(instant[0]==1&&!SpellChannelManager.isCasting(nova),"Basic named spell is actually released at tick zero");
        var record=NeoRuntime.data(nova).records.get(0);check(record.preparation==0&&record.novaQuickCast,"Research records instantaneous nine-lives cast");
        near(record.paid,record.actual,"Instant named spell still pays full actual collapse");
        check(record.failureDraws==1,"Instant cast still uses one collapse failure draw");
        check(SpellChannelManager.getNextCastAt(nova)>nova.getWorld().getTime(),"Instant spell retains normal GCD");
        check(NeoNetwork.hudState(nova).getLong("NovaQuickCastUntil")==until,"HUD sends absolute authoritative revival expiry");
        var solo=revived(t);var soloOutcome=channel(solo,"frost_spike",true,7);check(soloOutcome[0]==1&&soloOutcome[1]==1,"Standalone scroll also releases and settles cooldown once");

        var novaBlue=revived(t);var instantBlue=blueprint(novaBlue,40);
        check(instantBlue.preparation==0&&instantBlue.novaQuickCast&&!NeoRuntime.ACTIVE.containsKey(novaBlue.getUuid()),"Production blueprint skips preparation");
        near(instantBlue.paid,instantBlue.actual,"Instant blueprint still pays full actual Q");

        var domainNova=revived(t);int[] domainResult=channel(domainNova,"domain",false,8);
        check(SpellChannelManager.animationTiming(domainNova).duration()==100&&SpellChannelManager.animationTiming(domainNova).lockTick()==0,"Nova domain starts at expansion and keeps hundred-tick protected tail");
        check(DomainManager.isExpanding(domainNova),"Actual domain expansion begins immediately");
        SpellChannelManager.onDamaged(domainNova);check(SpellChannelManager.isCasting(domainNova),"Nova domain retains immediate release protection");
        t.waitAndRun(102,()->check(domainResult[0]==1&&!SpellChannelManager.isCasting(domainNova),"Domain releases after original protected animation duration"));

        var explosion=revived(t);
        // Keep this delayed blast outside the simultaneously expanding test domain.
        explosion.requestTeleport(domainNova.getX()+100,domainNova.getY(),domainNova.getZ()+100);
        explosion.getServerWorld().getChunk(explosion.getBlockPos());
        BlockPos floor=explosion.getBlockPos().add(0,-4,5);
        for(BlockPos pos:BlockPos.iterate(floor.add(-3,0,-6),floor.add(3,0,18)))explosion.getServerWorld().setBlockState(pos,Blocks.STONE.getDefaultState());
        explosion.setPitch(45);var explosionResult=channel(explosion,"explosion",false,9);var explosionTiming=SpellChannelManager.animationTiming(explosion);
        check(explosionTiming.duration()==160&&explosionTiming.preparation()==0&&explosionTiming.logical(0)==540,"Explosion begins at tick 540 and retains full 160-tick tail");
        check(ExplosionManager.isBallCharging(explosion),"Actual explosion is locked from acceptance");
        t.waitAndRun(102,()->check(!OcelotSpellLink.quickCast(explosion)&&SpellChannelManager.isCasting(explosion),"Accepted protected cast survives expiry without regrowing preparation"));
        t.waitAndRun(162,()->check(explosionResult[0]==1&&!SpellChannelManager.isCasting(explosion),"Actual explosion completes once after its original animation tail"));

        var finale=revived(t);
        var testCenter=t.getAbsolutePos(new BlockPos(2,4,2)).toCenterPos();
        finale.requestTeleport(testCenter.x,testCenter.y,testCenter.z);
        var pig=EntityType.PIG.create(finale.getServerWorld());pig.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);pig.setHealth(200);pig.setNoGravity(true);pig.setAiDisabled(true);pig.refreshPositionAndAngles(finale.getX()+2,finale.getY(),finale.getZ(),0,0);finale.getServerWorld().spawnEntity(pig);
        // Newly loaded distant sections become queryable after the world's entity-management tick.
        t.waitAndRun(3,()->{
            check(finale.getWorld().getEntitiesByClass(net.minecraft.entity.LivingEntity.class,finale.getBoundingBox().expand(13),e->e==pig).contains(pig),"Finale target is indexed in its actual world section");
            var finaleResult=channel(finale,"death_finale",false,10);
            check(finaleResult[0]==1&&!SpellChannelManager.isCasting(finale)&&pig.getHealth()<200,
                    "Zero-time death finale actually releases: effects="+finaleResult[0]+", casting="+SpellChannelManager.isCasting(finale)+", health="+pig.getHealth());
        });

        var changed=revived(t);RegPlayerFormComponent.PLAYER_FORM.get(changed).nowForm=RegPlayerForms.playerForms.get(FormIdentifiers.OCELOT_SP);NeoRuntime.refreshForm(changed);
        RegPlayerFormComponent.PLAYER_FORM.get(changed).nowForm=RegPlayerForms.playerForms.get(FormIdentifiers.OCELOT_NOVA);NeoRuntime.refreshForm(changed);
        check(!OcelotSpellLink.quickCast(changed),"Leaving and returning to Nova clears shortcut");
        t.waitAndRun(99,()->check(OcelotSpellLink.quickCast(nova),"Window remains active through its last tick"));
        t.waitAndRun(100,()->check(!OcelotSpellLink.quickCast(nova)&&FormSpellStyle.timing(nova,40,40,-1).duration()==40,"Window ends exactly at hundred ticks"));

        t.waitAndRun(165,()->{System.out.println("NEO_OCELOT_LINK_PASS checks="+checks+" (real revival, named/solo/blueprints, movement, skill/sprint gates, special spell clocks, payment, expiry and cleanup)");t.complete();});
    }
}
