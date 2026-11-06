package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.balance.*;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.mana.*;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.util.*;

/** 真正的游戏世界、共同施法入口、原生mana和投射体同步数据回归。 */
public final class NeoFormCastingProbe {
    private static int checks,index;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){check(Math.abs(actual-expected)<1e-5,why+": "+actual+" != "+expected);}
    static ServerPlayerEntity player(TestContext t,Identifier form,double mana) throws Exception {
        var p=t.createMockCreativeServerPlayerInWorld();p.setNoGravity(true);p.getAbilities().invulnerable=true;
        int cell=index++;
        var origin=t.getAbsolutePos(new net.minecraft.util.math.BlockPos(2,4,2)).toCenterPos().add((cell%5)*16,0,(cell/5)*64);
        p.getServerWorld().getChunk(net.minecraft.util.math.BlockPos.ofFloored(origin));
        p.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
        RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm=RegPlayerForms.playerForms.get(form);
        check(RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm!=null,"Registered test form "+form);
        ManaUtils.setManaTypeID(p,ManaRegistries.FAMILIAR_FOX_MANA);ManaUtils.setPlayerMana(p,mana);
        var f=BalanceIntegration.class.getDeclaredField("server");f.setAccessible(true);
        var state=(ServerBalanceState)f.get(null);state.buildSendSequence(p.getUuid(),"{}");state.playerAcked(p.getUuid(),state.revision());
        NeoRuntime.refreshForm(p);return p;
    }
    private static boolean start(ServerPlayerEntity p,String path,int cost,Runnable effect) {
        var spell=SpellRegistry.get(path);
        return SpellChannelManager.start(p,spell,ScrollData.create(path),1,false,901,cost,40,()->true,v->effect.run(),cd->{});
    }
    private static PigEntity pig(ServerPlayerEntity p,double x,double z) {
        var pig=EntityType.PIG.create(p.getServerWorld());pig.setNoGravity(true);pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(p.getX()+x,p.getEyeY()-pig.getHeight()*.5,p.getZ()+z,0,0);
        check(p.getServerWorld().spawnEntity(pig),"Target spawned");return pig;
    }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void run(TestContext t) throws Exception {
        Set<String> projectile=Set.of("fire_bolt","frost_spike","ice_barrage","moonlight_arrow","curse_mark","beep_sheep","meteor");
        for(var spell:SpellRegistry.all())check(spell.getConfig().projectile==projectile.contains(spell.getId().getPath()),"Actual JSON projectile classification "+spell.getId());
        var domainTime=FormSpellStyle.Timing.of(300,300,200,.75);
        check(domainTime.duration()==250&&domainTime.lockTick()==150,"Domain saves fifty ticks; expansion retains one hundred");
        var explosionTime=FormSpellStyle.Timing.of(700,700,540,.75);
        check(explosionTime.duration()==565&&explosionTime.lockTick()==405,"Explosion saves 135 ticks; final 160 unchanged");
        for(int tick=405;tick<565;tick++)check(explosionTime.logical(tick+1)-explosionTime.logical(tick)==1,"Post-lock animation never accelerates");

        var fast=player(t,FormIdentifiers.FAMILIAR_FOX_SP,10);
        int[] released={0};check(start(fast,"fire_bolt",20,()->released[0]++),"SP starts actual common channel");
        var cast=NeoLegacyCasting.active(fast);
        check(cast.duration==6,"Eight tick preparation becomes six");near(cast.session.quote.actual(),21,"Collapse quote includes exactly five percent");
        near(ManaUtils.getPlayerMana(fast),0,"Exactly ten native mana paid at start");
        check(!start(fast,"fire_bolt",20,()->{}),"Duplicate start is rejected");near(ManaUtils.getPlayerMana(fast),0,"Duplicate does not debit mana");
        t.waitAndRun(5,()->check(released[0]==0,"No early effect"));
        t.waitAndRun(7,()->{check(released[0]==1,"Accelerated effect released exactly once");near(cast.session.paid,21,"Full accelerated collapse paid");});

        var slow=player(t,FormIdentifiers.FAMILIAR_FOX_SP,9);
        check(start(slow,"fire_bolt",20,()->{}),"Insufficient mana still allows ordinary SP casting");
        check(NeoLegacyCasting.active(slow).duration==8,"Insufficient mana preserves normal duration");
        near(NeoLegacyCasting.active(slow).session.quote.actual(),20,"No surcharge when acceleration inactive");near(ManaUtils.getPlayerMana(slow),9,"No partial mana debit");
        var red=player(t,FormIdentifiers.FAMILIAR_FOX_RED,10);
        check(start(red,"fire_bolt",20,()->{}),"Red acceleration accepted");check(NeoLegacyCasting.active(red).duration==6,"Red shares the acceleration rule");
        var evolved=player(t,FormIdentifiers.UPGRADE_FAMILIAR_FOX,50);
        check(start(evolved,"fire_bolt",20,()->{}),"Other forms cast normally");check(NeoLegacyCasting.active(evolved).duration==8,"Unrequested evolved form does not gain acceleration");near(ManaUtils.getPlayerMana(evolved),50,"Other forms do not pay optional fee");

        var owner=player(t,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,20);
        var middle=pig(owner,0,10);var closer=pig(owner,.4,4);
        check(FormSpellStyle.findTarget(owner,50)==middle,"Crosshair angle takes priority over world distance; found="+FormSpellStyle.findTarget(owner,50)
                +" legal="+FormSpellStyle.legalTarget(owner,middle)+" visible="+owner.canSee(middle)+" eye="+owner.getEyePos()+" center="+middle.getBoundingBox().getCenter()
                +" look="+owner.getRotationVec(1)+" candidates="+owner.getWorld().getEntitiesByClass(PigEntity.class,owner.getBoundingBox().expand(50),e->true).size());
        check(FormSpellStyle.findTarget(owner,5)==closer,"Outside-range target excluded");
        var far=pig(owner,0,51);check(FormSpellStyle.findTarget(owner,50)!=far,"Beyond maximum range never selected");far.discard();
        closer.discard();
        var outside=pig(owner,5,10);middle.discard();check(FormSpellStyle.findTarget(owner,50)==null,"Outside fifteen degree cone not acquired");outside.discard();
        var target=pig(owner,0,12);final SpellFireBoltEntity[] fired={null};
        check(start(owner,"fire_bolt",10,()->{
            var spell=SpellRegistry.get("fire_bolt");spell.cast(owner,1,false,1);
            fired[0]=owner.getServerWorld().getEntitiesByClass(SpellFireBoltEntity.class,owner.getBoundingBox().expand(5),e->e.getOwner()==owner).get(0);
        }),"Homing channel starts");near(ManaUtils.getPlayerMana(owner),10,"Homing debits once per cast");
        t.waitAndRun(9,()->{
            check(fired[0]!=null,"Real projectile spawned through released effect");
            var homing=(FormSpellStyle.HomingProjectile)fired[0];check(homing.ssca$isHoming(),"ENTITY_LOAD attached authoritative target");
            target.setPosition(target.getPos().add(2,0,0));
            check(homing.ssca$guidedVelocity(new Vec3d(0,0,.8)).x>0,"Tracking steers to moving target");
            NbtCompound nbt=new NbtCompound();fired[0].writeCustomDataToNbt(nbt);
            check(nbt.containsUuid("SscaHomingTarget")&&nbt.getDouble("SscaHomingRemaining")>0,"Target and remaining range persist");
            var restored=new SpellFireBoltEntity(owner.getWorld(),owner);restored.readCustomDataFromNbt(nbt);restored.tick();
            check(((FormSpellStyle.HomingProjectile)restored).ssca$isHoming(),"Reload resolves UUID to current target ID");restored.discard();
            var limited=new SpellFireBoltEntity(owner.getWorld(),owner);limited.setPosition(owner.getEyePos());limited.setVelocity(0,0,.8);
            ((FormSpellStyle.HomingProjectile)limited).ssca$home(target,1);
            limited.tick();
            NbtCompound partial=new NbtCompound();limited.writeCustomDataToNbt(partial);
            near(partial.getDouble("SscaHomingRemaining"),.2,"Saving settles movement from the current tick without granting extra range");
            limited.tick();near(limited.getPos().distanceTo(owner.getEyePos()),1,"Flight cannot extend past paid range");
            limited.tick();check(limited.isRemoved(),"Projectile disappears at maximum traveled distance");
        });
        var empty=player(t,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,0);
        check(start(empty,"fire_bolt",10,()->{}),"No target permits free straight shot even at zero mana");near(ManaUtils.getPlayerMana(empty),0,"No target no mana");
        var poor=player(t,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,9);var poorTarget=pig(poor,0,10);
        check(!start(poor,"fire_bolt",10,()->{}),"Target present but insufficient mana rejects channel");
        check(!SpellChannelManager.isCasting(poor)&&NeoLegacyCasting.active(poor)==null,"Rejected cast has no active state");near(NeoRuntime.data(poor).burden,0,"Rejected cast adds no collapse");near(ManaUtils.getPlayerMana(poor),9,"Rejected cast does not partially debit");poorTarget.discard();
        check(start(poor,"lunar_mend",10,()->{}),"Nonprojectile remains usable with low mana");near(ManaUtils.getPlayerMana(poor),9,"Nonprojectile has no tracking fee");

        var domain=player(t,FormIdentifiers.FAMILIAR_FOX_SP,30);
        check(start(domain,"domain",0,()->SpellRegistry.get("domain").cast(domain,0,false)),"Real domain begins");
        check(NeoLegacyCasting.active(domain).duration==250,"Real domain channel uses split timing");
        t.waitAndRun(148,()->check(!DomainManager.isExpanding(domain),"Domain remains interruptible before shortened lock"));
        t.waitAndRun(151,()->check(DomainManager.isExpanding(domain),"Domain expansion starts at shortened lock"));
        t.waitAndRun(249,()->check(SpellChannelManager.isCasting(domain),"Expansion retains full original duration"));
        t.waitAndRun(252,()->{check(!SpellChannelManager.isCasting(domain),"Domain finishes at new total duration");DomainManager.remove(domain);});
        t.waitAndRun(280,()->{
            try{NeoMoistureHealingProbe.run(t);NeoPortableBeaconProbe.run(t);NeoTransformCleanupProbe.run(t);NeoCountdownSyncProbe.run(t);NeoScaleWriteProbe.run(t);}catch(Exception failure){throw new RuntimeException(failure);}
        });
        t.waitAndRun(370,()->{
            try{NeoProjectileAffinityProbe.run(t);}catch(Exception failure){throw new RuntimeException(failure);}
        });
        var explosion=player(t,FormIdentifiers.FAMILIAR_FOX_RED,10);explosion.setPitch(60);
        // This case verifies uninterrupted timing; domain suppression has its own world matrix.
        Vec3d isolated=domain.getPos().add(DomainRules.outerRadius()+32,0,0);
        explosion.getServerWorld().getChunk(net.minecraft.util.math.BlockPos.ofFloored(isolated));
        explosion.refreshPositionAndAngles(isolated.x,isolated.y,isolated.z,0,60);
        for(var pos:net.minecraft.util.math.BlockPos.iterate(net.minecraft.util.math.BlockPos.ofFloored(isolated).add(-2,-2,0),
                net.minecraft.util.math.BlockPos.ofFloored(isolated).add(2,-2,8)))
            explosion.getServerWorld().setBlockState(pos,net.minecraft.block.Blocks.STONE.getDefaultState());
        int[] exploded={0};
        check(start(explosion,"explosion",0,()->{exploded[0]++;ExplosionManager.cancelCharge(explosion);}),"Real explosion begins with valid ground target");
        check(NeoLegacyCasting.active(explosion).duration==565,"Real explosion has shortened preparation and full finale");
        var barrage=player(t,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,10);var barrageTarget=pig(barrage,0,12);
        check(start(barrage,"ice_barrage",10,()->SpellRegistry.get("ice_barrage").cast(barrage,1,false,1)),"Actual barrage accepts one tracking fee");
        near(ManaUtils.getPlayerMana(barrage),0,"Three-shot barrage charges ten mana total");
        t.waitAndRun(18,()->{
            var spikes=barrage.getServerWorld().getEntitiesByClass(SpellFrostSpikeEntity.class,barrage.getBoundingBox().expand(8),e->e.getOwner()==barrage);
            check(spikes.size()==3&&spikes.stream().allMatch(e->((FormSpellStyle.HomingProjectile)e).ssca$isHoming()),"All three projectiles share the paid tracking effect");
            spikes.forEach(net.minecraft.entity.Entity::discard);barrageTarget.discard();
        });
        t.waitAndRun(403,()->check(!ExplosionManager.isBallCharging(explosion),"Explosion remains interruptible before shortened sound boundary"));
        t.waitAndRun(406,()->check(ExplosionManager.isBallCharging(explosion),"Theme and lock begin at shortened boundary"));
        t.waitAndRun(564,()->check(exploded[0]==0,"Explosion final animation retains all 160 ticks"));
        t.waitAndRun(568,()->{
            check(exploded[0]==1&&!SpellChannelManager.isCasting(explosion),"Explosion releases exactly once after full finale");
            check(!SpellChannelManager.isCasting(fast)&&!SpellChannelManager.isCasting(slow),"Completed casts leave no channel state");
            checks+=NeoMoistureHealingProbe.checks();
            checks+=NeoProjectileAffinityProbe.checks();
            System.out.println("NEO_FORM_CASTING_PASS checks="+checks+" (actual channels, mana, collapse, phase timing, projectile metadata, moving target, persistence and range)");
            t.complete();
        });
    }
}
