package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** 两种形态的实际弹体、蓝图步长、远端碰撞、原版同步包和存档回归。 */
public final class NeoProjectileAffinityProbe {
    private static int checks;
    public static int checks(){return checks;}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){check(Math.abs(actual-expected)<1e-4,why+": "+actual+" != "+expected);}
    private static FormSpellStyle.HomingProjectile flight(ProjectileEntity entity){return (FormSpellStyle.HomingProjectile)entity;}
    public static void run(TestContext t) throws Exception {
        int elevation=18;
        for(var form:List.of(FormIdentifiers.AXOLOTL_FLUORESCENT,FormIdentifiers.AXOLOTL_ALING)) {
            var player=NeoFormCastingProbe.player(t,form,0);
            var origin=t.getAbsolutePos(new net.minecraft.util.math.BlockPos(112,elevation,2)).toCenterPos();elevation+=12;
            player.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);player.setAir(150);
            var fire=SpellRegistry.get("fire_bolt");var style=FormSpellStyle.plan(player,fire,1,8,8);
            near(style.projectileSpeed,1.5,"Native projectile speed affinity "+form);
            near(style.range,75,"Fifty-block spell gains seventy-five-block range "+form);
            near(style.collapseMultiplier,1.05,"Paid affinity adds five percent collapse");
            check(style.timing.duration()==8&&style.pay()&&player.getAir()==140,"Affinity costs ten wetness without changing preparation");
            check(style.pay()&&player.getAir()==140&&style.paidMoisture()==10,"Payment is idempotent per cast");
            var nonProjectile=FormSpellStyle.plan(player,SpellRegistry.get("lunar_mend"),1,30,30);
            near(nonProjectile.projectileRange,1,"Nonprojectile does not gain range");
            near(nonProjectile.collapseMultiplier,1,"Nonprojectile has no collapse surcharge");
            check(nonProjectile.pay()&&player.getAir()==140,"Nonprojectile does not spend wetness");
            player.setAir(9);var poor=FormSpellStyle.plan(player,fire,1,8,8);
            check(poor.pay()&&player.getAir()==9&&poor.paidMoisture()==0,"Insufficient wetness releases normally without partial debit");
            near(poor.projectileSpeed,1,"Insufficient wetness keeps ordinary speed");
            near(poor.range,50,"Insufficient wetness keeps ordinary range");
            near(poor.collapseMultiplier,1,"Fallback has no surcharge");
            near(NeoLegacyCasting.quote(player,fire,1,8,10).actual(),10,"Fallback preview has ordinary actual cost");
            player.setAir(10);
            near(NeoLegacyCasting.quote(player,fire,1,8,10).actual(),10.5,"Named preview includes actual surcharge");
            var meteor=SpellRegistry.get("meteor");near(meteor.getAimMaxRange(player,1),48,"Targeted meteor range uses shared server/client helper");
            var paidMeteor=FormSpellStyle.plan(player,meteor,1,40,40);
            check(paidMeteor.pay()&&player.getAir()==0,"Exactly ten wetness enables full bonus");
            paidMeteor.effect(()->near(meteor.getAimMaxRange(player,1),48,"Effect retains paid range after wetness reaches zero"));
            near(meteor.getAimMaxRange(player,1),32,"Next cast without wetness has ordinary preview range");

            player.setAir(10);var barrage=SpellRegistry.get("ice_barrage");var batch=FormSpellStyle.plan(player,barrage,1,8,8);
            check(batch.pay(),"Barrage pays one cast fee");batch.effect(()->barrage.cast(player,1,true,1));
            var spikes=player.getServerWorld().getEntitiesByClass(SpellFrostSpikeEntity.class,player.getBoundingBox().expand(8),e->e.getOwner()==player);
            check(spikes.size()==3&&player.getAir()==0&&batch.paidMoisture()==10,"Three real barrage projectiles share one ten-wetness payment");
            for(var spike:spikes){near(flight(spike).ssca$flightSpeedMultiplier(),1.5,"Every barrage projectile retains paid bonus");spike.discard();}

            var bolt=new SpellFireBoltEntity(player.getWorld(),player);bolt.setDirection(new Vec3d(0,0,1),1);
            Vec3d start=bolt.getPos();flight(bolt).ssca$flight(1.5,75);
            near(bolt.getVelocity().length(),1.2,"Actual base fire velocity multiplied once");
            flight(bolt).ssca$flight(1.5,75);near(bolt.getVelocity().length(),1.2,"Repeated setup does not stack");
            check(!flight(bolt).ssca$isHoming(),"Flight bonus does not add targeting");
            for(int i=0;i<43;i++)bolt.tick();
            check(!bolt.isRemoved()&&bolt.getPos().distanceTo(start)>50,"Actual projectile survives beyond original fifty-block cap");
            var saved=bolt.writeNbt(new NbtCompound());
            near(saved.getDouble("SscaHomingRemaining"),75-bolt.getPos().distanceTo(start),"Save settles exact remaining extended range");
            var restored=new SpellFireBoltEntity(player.getWorld(),player);restored.readNbt(saved);
            near(flight(restored).ssca$flightSpeedMultiplier(),1.5,"Saved flight factor restored");
            near(restored.getVelocity().length(),1.2,"Saved speed is not multiplied again");restored.discard();
            for(int i=0;i<20;i++)bolt.tick();near(bolt.getPos().distanceTo(start),75,"Final movement clips to exactly seventy-five blocks");
            bolt.tick();check(bolt.isRemoved(),"Projectile disappears at extended range");

            // 实际原版生成包限幅到3.9，额外快照通过原版实体元数据包补足。
            var fast=new SpellFireBoltEntity(player.getWorld(),player);fast.setVelocity(0,0,6);
            flight(fast).ssca$flight(1.5,75);var replica=new SpellFireBoltEntity(player.getWorld(),player);
            replica.onSpawnPacket(new EntitySpawnS2CPacket(fast));
            var velocityPacket=new EntityVelocityUpdateS2CPacket(fast);
            replica.setVelocityClient(velocityPacket.getVelocityX()/8000d,velocityPacket.getVelocityY()/8000d,velocityPacket.getVelocityZ()/8000d);
            check(replica.getVelocity().length()>3.8&&replica.getVelocity().length()<4,"Vanilla velocity packet demonstrates velocity clamp");
            var buffer=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            new EntityTrackerUpdateS2CPacket(fast.getId(),fast.getDataTracker().getChangedEntries()).write(buffer);
            var packet=new EntityTrackerUpdateS2CPacket(buffer);replica.getDataTracker().writeUpdatedEntries(packet.trackedValues());
            check(buffer.readableBytes()==0,"Actual metadata packet fully decoded");
            flight(replica).ssca$syncFlightVelocity();near(replica.getVelocity().length(),9,"Client correction restores true speed from synchronized snapshot");
            fast.discard();replica.discard();

            var falling=new SpellMeteorEntity(player.getWorld(),player);falling.setImpactTarget(origin.x,origin.y,origin.z);
            flight(falling).ssca$flight(1.5,48);double height=falling.getY();
            for(int i=0;i<10;i++)falling.tick();near(falling.getY(),height,"Meteor warning duration is unchanged");
            falling.tick();near(height-falling.getY(),3.75,"Meteor falling speed gains fifty percent");
            for(int i=0;i<5;i++)falling.tick();check(falling.isRemoved(),"Faster meteor detonates after six falling ticks");
            near(falling.getY(),origin.y,"Faster meteor still lands at original target height");

            var b=new Blueprint("fast bolt",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,1,0,20);
            var data=NeoRuntime.data(player);data.known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());
            player.setAir(10);var baseQuote=NeoRules.quote(b,data.mastery(b));
            near(NeoLegacyCasting.blueprintQuote(player,b).actual(),baseQuote.actual()*1.05,"Blueprint preview includes surcharge");
            var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));player.setStackInHand(Hand.MAIN_HAND,wand);
            Vec3d direction=Vec3d.fromPolar(player.getPitch(),player.getYaw());
            var victim=EntityType.PIG.create(player.getServerWorld());victim.setAiDisabled(true);victim.setNoGravity(true);
            Vec3d at=player.getEyePos().add(direction.multiply(25)).add(0,-victim.getHeight()*.5,0);
            victim.refreshPositionAndAngles(at.x,at.y,at.z,0,0);player.getServerWorld().spawnEntity(victim);
            net.ssca.neomagic.NeoTestFixtures.start(player,Hand.MAIN_HAND);var record=data.records.get(0);
            check(player.getAir()==0&&record.moistureSpent==10,"Production blueprint debits and records ten wetness once");
            near(record.actual,baseQuote.actual()*1.05,"Production blueprint retains surcharge after payment");
            for(int i=0;i<18;i++)NeoEffects.tick(player.getServer());
            check(record.damage>0&&record.pending==0,"Production blueprint travels faster and hits beyond base twenty-block range");victim.discard();

            // 普通施法入口中的实体生成钩子也必须实际应用加成。
            int[] effects={0};player.setAir(10);
            check(SpellChannelManager.start(player,fire,ScrollData.create("fire_bolt"),1,false,1130,10,40,()->true,v->{
                fire.cast(player,1,false,1);effects[0]++;
                var spawned=player.getServerWorld().getEntitiesByClass(SpellFireBoltEntity.class,player.getBoundingBox().expand(8),e->e.getOwner()==player);
                check(spawned.size()==1,"Named cast emitted one actual fire projectile");
                near(spawned.get(0).getVelocity().length(),1.2,"ENTITY_LOAD applies paid affinity to released named spell");
                var nbt=new NbtCompound();spawned.get(0).writeCustomDataToNbt(nbt);near(nbt.getDouble("SscaHomingRemaining"),75,"Named spell receives extended range budget");
                spawned.get(0).discard();
            },cd->{}),"Named projectile channel starts at zero mana");
            var named=data.records.get(0);
            check(player.getAir()==0&&named.moistureSpent==10,"Named cast debits and records wetness once");
            near(named.actual,10.5,"Named cast snapshots surcharge before spending wetness");
            near(meteor.getAimMaxRange(player,1),48,"Active server channel keeps its paid range snapshot");
            t.waitAndRun(11,()->check(effects[0]==1,"Named projectile released exactly once"));
        }
        t.waitAndRun(12,()->System.out.println("NEO_PROJECTILE_AFFINITY_PASS checks="+checks+" (both forms, actual speed/range, persistence, packet roundtrip, high speed, meteor and production blueprints)"));
    }
}
