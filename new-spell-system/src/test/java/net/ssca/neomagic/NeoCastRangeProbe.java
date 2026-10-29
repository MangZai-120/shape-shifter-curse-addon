package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Actual channel/range termination, native explosion suppression, entity locks and released bolts. */
public final class NeoCastRangeProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t)throws Exception {
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.OCELOT_SP,0);NeoRuntime.data(p).spellbookMasteryExpTen=120000;return p;
    }
    @SuppressWarnings("unchecked")
    private static Map<Object,Object> map(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return (Map<Object,Object>)f.get(null);}
    private static Object channel(ServerPlayerEntity p)throws Exception{return map(SpellChannelManager.class,"ACTIVE").get(p.getUuid());}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static boolean validate(ServerPlayerEntity p)throws Exception {
        Object c=channel(p);var method=SpellChannelManager.class.getDeclaredMethod("ensureValid",c.getClass());method.setAccessible(true);return (boolean)method.invoke(null,c);
    }
    private static int[] start(ServerPlayerEntity p,Spell spell,boolean solo)throws Exception {
        int[] outcome={0,0,0,0};
        check(SpellChannelManager.start(p,spell,ScrollData.create(spell.getId().getPath()),1,solo,607,100,100,()->true,
                v->outcome[0]++,cd->{outcome[1]++;outcome[2]=cd;},()->outcome[3]++),"Real channel starts "+spell.getId());return outcome;
    }
    private static final class FixedSpell extends Spell {
        Vec3d locked;double range;boolean protectedPhase;
        FixedSpell(Vec3d point,double range){super(new Identifier("ssc_addon","frost_armor"),SpellRarity.WHITE);locked=point;this.range=range;}
        @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity p,int level,boolean solo){return new SpellCastingRules.Profile(80,.8,false);}
        @Override public SpellCastingRules.Mode getCastingMode(){return SpellCastingRules.Mode.RELEASE;}
        @Override public boolean requiresTargetBeforeChannel(){return true;}
        @Override public Vec3d captureCastTarget(ServerPlayerEntity p,int level){return locked;}
        @Override public double getCastingLockRange(ServerPlayerEntity p,int level){return range;}
        @Override public int getLockInTick(){return 40;}
        @Override public boolean isLockedIn(ServerPlayerEntity p){return protectedPhase;}
        @Override public void cast(ServerPlayerEntity p,float power,boolean solo){}
    }
    private static void phaseDomain(ServerPlayerEntity p)throws Exception {
        Map<Object,Object> fields=map(DomainManager.class,"FIELDS");Object old=fields.get(p.getUuid());var type=old.getClass();var components=type.getRecordComponents();Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        for(int i=0;i<values.length;i++){types[i]=components[i].getType();var get=components[i].getAccessor();get.setAccessible(true);values[i]=components[i].getName().equals("startTick")?p.getServer().getTicks()-DomainRules.expandStartTick():get.invoke(old);}
        var constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);fields.put(p.getUuid(),constructor.newInstance(values));
    }
    private static LivingEntity pig(ServerPlayerEntity p,double distance){
        var e=EntityType.PIG.create(p.getServerWorld());e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);e.setHealth(200);e.setNoGravity(true);e.setAiDisabled(true);
        e.refreshPositionAndAngles(p.getX(),p.getEyeY()-e.getHeight()*.5,p.getZ()+distance,0,0);check(p.getServerWorld().spawnEntity(e),"Actual living target spawns");return e;
    }
    private static NeoState.Experiment blueprint(ServerPlayerEntity p,Carrier carrier,int prep){
        var b=new Blueprint("range",carrier==Carrier.LOCK?Core.CURSE:Core.THERMAL,carrier,List.of(),Core.FROST,1,prep,8);
        NeoRuntime.data(p).known.addAll(NeoMagic.SAMPLES.keySet());var wand=new ItemStack(NeoMagic.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));p.setStackInHand(Hand.MAIN_HAND,wand);
        NeoRuntime.start(p,Hand.MAIN_HAND);return NeoRuntime.data(p).records.get(0);
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        check(!CastRangeGuard.exceeds(Vec3d.ZERO,new Vec3d(15,0,0),10),"Exact 1.5x boundary remains valid");
        check(CastRangeGuard.exceeds(Vec3d.ZERO,new Vec3d(15.001,0,0),10),"Strictly beyond 1.5x fails");
        check(CastRangeGuard.exceeds(Vec3d.ZERO,new Vec3d(0,15.001,0),10),"Vertical displacement counts in three dimensions");
        check(!CastRangeGuard.exceeds(Vec3d.ZERO,new Vec3d(100,0,0),0),"No finite lock range does not create an arbitrary zero-distance limit");

        var p=player(t);Vec3d point=p.getPos().add(0,0,10);var spell=new FixedSpell(point,10);var outcome=start(p,spell,true);
        var legacy=NeoLegacyCasting.active(p);for(int age=1;age<=6;age++)legacy.advance(age);double paid=NeoRuntime.data(p).burden;
        check(paid>0,"Some collapse is actually paid before interruption");spell.range=1;
        p.requestTeleport(point.x+15,point.y,point.z);check(validate(p),"Accepted effective range is frozen despite later configuration changes");
        spell.protectedPhase=true;p.requestTeleport(point.x+15.001,point.y,point.z);
        check(!validate(p)&&!SpellChannelManager.isCasting(p),"Distance overrides actual special release lock");
        check(outcome[0]==0&&outcome[1]==1&&outcome[2]==80&&outcome[3]==0,"No release/use consumption and exactly one eighty-percent cooldown");
        near(NeoRuntime.data(p).burden,paid,"Paid collapse is retained, without extra charge or refund");
        check(legacy.record.result.equals("failed")&&legacy.record.failureReason.equals("range_exceeded"),"Failure reason and result agree");
        SpellChannelManager.onFullyControlled(p);check(outcome[1]==1,"Repeated termination cannot settle twice");
        p.requestTeleport(p.getX()+1000,p.getY(),p.getZ());

        var shift=player(t);start(shift,SpellRegistry.get("frost_armor"),false);shift.setSneaking(true);
        check(!shift.isSprinting()&&OcelotSpellLink.sneakRush(shift),"Mapped crouch-rush is distinct from vanilla sprint");
        check(!validate(shift)&&!SpellChannelManager.isCasting(shift),"Mapped crouch-rush interrupts preparation");shift.setSneaking(false);
        var hungry=player(t);hungry.getHungerManager().setFoodLevel(5);start(hungry,SpellRegistry.get("frost_armor"),false);hungry.setSneaking(true);
        check(!OcelotSpellLink.rushing(hungry)&&validate(hungry),"Hungry ordinary crouch is not falsely treated as rush");SpellChannelManager.onFullyControlled(hungry);hungry.setSneaking(false);
        var locked=player(t);start(locked,SpellRegistry.get("domain"),false);phaseDomain(locked);locked.setSneaking(true);
        check(validate(locked)&&!locked.isSneaking()&&SpellChannelManager.isCasting(locked),"Protected stage disables mapped rush instead of breaking protection");SpellChannelManager.onFullyControlled(locked);

        for(String id:List.of("frost_armor","fire_bolt","domain")) {
            var free=player(t);start(free,SpellRegistry.get(id),false);free.requestTeleport(free.getX()+1000,free.getY(),free.getZ());
            check(validate(free),"No locked target means no cast-origin tether: "+id);SpellChannelManager.onFullyControlled(free);
        }

        var boom=player(t);boom.setPitch(45);BlockPos floor=boom.getBlockPos().add(0,-4,5);
        for(BlockPos pos:BlockPos.iterate(floor.add(-4,0,-6),floor.add(4,0,20)))boom.getServerWorld().setBlockState(pos,Blocks.STONE.getDefaultState());
        var boomResult=start(boom,SpellRegistry.get("explosion"),false);var boomLegacy=NeoLegacyCasting.active(boom);
        Object seq=map(ExplosionManager.class,"SEQUENCES").values().stream().filter(s->{try{return field(s,"owner").equals(boom.getUuid());}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
        Vec3d center=(Vec3d)field(seq,"center");UUID seqId=(UUID)field(seq,"id");
        for(int age=1;age<=6;age++)boomLegacy.advance(age);double boomPaid=NeoRuntime.data(boom).burden;
        boom.requestTeleport(center.x+192,center.y,center.z);check(validate(boom),"Actual explosion remains valid at 1.5 times its 128-block lock range");
        var clock=seq.getClass().getDeclaredField("startTime");clock.setAccessible(true);clock.setLong(seq,boom.getWorld().getTime()-620);
        check(ExplosionManager.isBallCharging(boom),"Actual explosion is already locked in theme stage");
        boom.requestTeleport(center.x+192.001,center.y,center.z);check(!validate(boom),"Out-of-range explosion fails even during protected stage");
        near(NeoRuntime.data(boom).burden,boomPaid,"Explosion retains previously paid collapse");
        check(boomResult[0]==0&&boomResult[1]==1&&boomResult[2]==80,"Explosion fails with exactly one interrupted cooldown");
        check((int)field(seq,"suppressedAt")>=0&&!(boolean)field(seq,"exploded")&&((List<?>)field(seq,"pendingTargets")).isEmpty(),"Suppressed explosion retains only inert finale and never damage");
        ExplosionManager.detonate(boom,center,1000,null);check(!(boolean)field(seq,"exploded"),"Late release cannot revive suppressed explosion");

        var lunar=player(t);Vec3d lunarPos=t.getAbsolutePos(new BlockPos(4,4,4)).toCenterPos();lunar.requestTeleport(lunarPos.x,lunarPos.y,lunarPos.z);lunar.setYaw(0);lunar.setPitch(0);var lunarTarget=pig(lunar,3);
        t.waitAndRun(2,()->{try{
            var r=start(lunar,SpellRegistry.get("lunar_phase"),false);Object c=channel(lunar);var tether=(CastRangeGuard.Lock)field(c,"targetLock");
            check(tether!=null&&tether.entity()==lunarTarget,"Native lunar entity spell exposes the accepted living lock");
            lunarTarget.requestTeleport(lunar.getX(),lunar.getY(),lunar.getZ()+tether.range()*1.5+.001);
            check(!validate(lunar)&&r[0]==0&&r[2]==80,"Moving locked entity triggers unified range failure without moving caster");
            lunar.requestTeleport(lunar.getX()+1000,lunar.getY(),lunar.getZ());
        }catch(Exception e){throw new RuntimeException(e);}});

        var blue=player(t);Vec3d bluePos=t.getAbsolutePos(new BlockPos(8,4,2)).toCenterPos();blue.requestTeleport(bluePos.x,bluePos.y,bluePos.z);blue.setYaw(0);blue.setPitch(0);var blueTarget=pig(blue,4);
        t.waitAndRun(3,()->{try{
            var record=blueprint(blue,Carrier.LOCK,40);var a=NeoRuntime.ACTIVE.get(blue.getUuid());check(a!=null&&a.targetLock!=null&&a.targetLock.entity()==blueTarget,"Actual blueprint snapshots living lock");
            var advance=NeoRuntime.class.getDeclaredMethod("advance",ServerPlayerEntity.class,NeoRuntime.Active.class);advance.setAccessible(true);
            for(int age=1;age<=6;age++){a.age=age;advance.invoke(null,blue,a);}double before=NeoRuntime.data(blue).burden;
            blueTarget.requestTeleport(blue.getX(),blue.getY(),blue.getZ()+12.001);a.age=7;advance.invoke(null,blue,a);
            check(!NeoRuntime.ACTIVE.containsKey(blue.getUuid())&&record.result.equals("failed")&&record.failureReason.equals("range_exceeded"),"Blueprint target movement uses same forced failure");
            near(NeoRuntime.data(blue).burden,before,"Blueprint retains paid collapse without backlash");
            check(NeoRuntime.data(blue).cooldowns.get(a.session.blueprint.identity())==SpellCastingRules.interruptedCooldown(a.fullCooldown),"Blueprint forced failure uses same eighty-percent rule");
            check(record.damage==0&&record.hits==0,"Failed blueprint has not emitted an effect");
            blue.requestTeleport(blue.getX()+1000,blue.getY(),blue.getZ());
        }catch(Exception e){throw new RuntimeException(e);}});

        var fired=player(t);Vec3d firePos=t.getAbsolutePos(new BlockPos(2,4,8)).toCenterPos();fired.requestTeleport(firePos.x,firePos.y,firePos.z);fired.setYaw(0);fired.setPitch(0);var victim=pig(fired,3);
        t.waitAndRun(5,()->{
            var record=blueprint(fired,Carrier.PROJECTILE,0);check(!NeoRuntime.ACTIVE.containsKey(fired.getUuid())&&record.pending>0,"Projectile has already been successfully emitted");
            fired.requestTeleport(fired.getX()+1000,fired.getY(),fired.getZ());
            t.waitAndRun(5,()->check(victim.getHealth()<200&&record.result.equals("resolved"),"Moving away after launch retains actual hit: health="+victim.getHealth()+", result="+record.result));
        });
        t.waitAndRun(45,()->{try{check(!map(ExplosionManager.class,"SEQUENCES").containsKey(seqId),"Suppressed explosion disappears after the same inert finale");System.out.println("NEO_CAST_RANGE_PASS checks="+checks+" (sneak-rush, actual locks, boundary, protected stages, native explosion, moving entities, blueprints, paid C, CD and launched effects)");t.complete();}catch(Exception e){throw new RuntimeException(e);}});
    }
}
