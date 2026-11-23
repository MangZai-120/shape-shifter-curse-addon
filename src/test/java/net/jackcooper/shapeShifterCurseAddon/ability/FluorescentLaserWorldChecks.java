package net.jackcooper.shapeShifterCurseAddon.ability;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

final class FluorescentLaserWorldChecks {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static double angle(Vec3d a, Vec3d b) {
        return Math.toDegrees(Math.atan2(a.crossProduct(b).length(), a.dotProduct(b)));
    }
    private static void near(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 0.002, label + ": " + actual + " vs " + expected);
    }
    private static Vec3d direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3d(-Math.sin(y)*Math.cos(p), -Math.sin(p), Math.cos(y)*Math.cos(p));
    }
    private static PigEntity target(ServerWorld world, Vec3d center) {
        var pig = EntityType.PIG.create(world);
        pig.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500);
        pig.setHealth(500);
        pig.setNoGravity(true);
        pig.setAiDisabled(true);
        pig.setPosition(center.x, center.y - pig.getHeight()/2, center.z);
        check(world.spawnEntity(pig), "Spawn beam target");
        return pig;
    }
    private static void verifySteering() {
        Vec3d start = direction(179, 12);
        Vec3d goal = direction(-150, 26);
        var turn = new FluorescentLaserAim.Turn(start, Vec3d.ZERO);
        double lastSpeed = 0;
        boolean capped = false, braking = false;
        for (int tick=1; tick<=70; tick++) {
            var next = FluorescentLaserAim.advance(turn.direction(), goal, turn.angularVelocity(), 25);
            double step = angle(turn.direction(), next.direction());
            if (tick <= 20) near(step, tick * 0.0625, "Twenty-tick acceleration");
            check(step <= 1.25001 && Math.abs(step-lastSpeed) <= 0.06251, "Bounded angular speed and acceleration");
            check(angle(next.direction(), goal) <= angle(turn.direction(),goal)+1e-7, "Fixed diagonal target never overshoots");
            capped |= Math.abs(step-1.25)<1e-5;
            braking |= step<lastSpeed-0.01 && step>0.01;
            turn=next;lastSpeed=step;
        }
        check(capped && braking && turn.angularVelocity().lengthSquared()==0, "Reached cap, braked and settled");
        near(angle(turn.direction(),goal),0,"Stopped on the target across yaw wrap");
        turn = new FluorescentLaserAim.Turn(direction(0,0),Vec3d.ZERO);
        for (int i=0;i<20;i++) turn=FluorescentLaserAim.advance(turn.direction(),direction(60,0),turn.angularVelocity(),25);
        for (int i=0;i<40;i++) {
            var next=FluorescentLaserAim.advance(turn.direction(),direction(-55,0),turn.angularVelocity(),25);
            double signedStep=Math.toDegrees(Math.atan2(-next.direction().x,next.direction().z)-Math.atan2(-turn.direction().x,turn.direction().z));
            near(signedStep,1.25-(i+1)*0.0625,"Reversal brakes before accelerating the other way");
            turn=next;
        }
        var opposite=FluorescentLaserAim.advance(new Vec3d(0,1,0),new Vec3d(0,-1,0),Vec3d.ZERO,5);
        near(angle(new Vec3d(0,1,0),opposite.direction()),0.0125,"Finite initial acceleration at a pole");
    }

    private static void verifyTripleShots(ServerWorld world, ServerPlayerEntity owner, boolean aling) {
        var form=aling ? FormIdentifiers.AXOLOTL_ALING : FormIdentifiers.AXOLOTL_FLUORESCENT;
        RegPlayerFormComponent.PLAYER_FORM.get(owner).nowForm=RegPlayerForms.playerForms.get(form);
        var skill=new net.minecraft.util.Identifier(FluorescentLaserManager.SKILL_ID);
        var power=io.github.apace100.apoli.power.PowerTypeRegistry.get(skill);
        io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(owner).addPower(power,skill);
        var necklace=dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(owner).orElseThrow().getInventory().get("chest").get("necklace");
        necklace.setStack(0,aling ? net.minecraft.item.ItemStack.EMPTY : new net.minecraft.item.ItemStack(SscAddon.SEA_CRYSTAL_PENDANT));
        necklace.markDirty();
        net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.reset(owner,FluorescentLaserManager.SKILL_ID);
        owner.refreshPositionAndAngles(owner.getX()+48,owner.getY()+7,owner.getZ(),0,0);
        owner.setHeadYaw(0);
        var eye=owner.getEyePos();
        var pig=target(world,eye.add(0,0,14));
        var control=target(world,eye.add(8,0,14));
        check(control.damage(owner.getDamageSources().playerAttack(owner),10),"Physical hit control");
        double multiplier=(control.getMaxHealth()-control.getHealth())/10.0;
        float perShot=aling ? 20 : 15;
        near(FluorescentLaserManager.damagePerShot(aling,false),perShot,"Unmodified shot");
        near(FluorescentLaserManager.damagePerShot(aling,true),perShot*1.25,"Efficiency perk applies after base");
        FluorescentLaserManager.onKeyPress(owner);
        for (int shot=1;shot<=3;shot++) {
            pig.timeUntilRegen=0;
            FluorescentLaserManager.onKeyPress(owner);
            for(int tick=0;tick<8;tick++) FluorescentLaserManager.tick(owner);
            near(pig.getMaxHealth()-pig.getHealth(),perShot*multiplier*shot,"One hit per target per shot");
        }
        System.out.println("FLUORESCENT_TRIPLE_PASS aling="+aling+" base="+perShot+" three="+(3*perShot));
    }

    static void run(TestContext test) {
        verifySteering();
        Vec3d forward = new Vec3d(0, 0, 1);
        near(FluorescentLaserAim.limitLook(forward,0,0,12,0).y,12,"Free camera inside cone");
        near(FluorescentLaserAim.limitLook(forward,0,0,480,0).y,60,"Large flick stops on its own side");
        near(FluorescentLaserAim.limitLook(forward,60,0,-3,0).y,57,"Immediate reversal");
        var diagonal = FluorescentLaserAim.limitLook(forward,0,0,60,45);
        near(angle(forward,direction(diagonal.y,diagonal.x)),60,"Combined yaw and pitch limit");
        near(FluorescentLaserAim.limitLook(direction(179,0),179,0,8,0).y,187,"Unwrapped yaw at the seam");
        for (int pitch : new int[]{-90,-75,-30,0,30,75,90}) {
            Vec3d axis = direction(179,pitch);
            for (int dx=-360;dx<=360;dx+=30) for (int dy=-120;dy<=120;dy+=15) {
                var look=FluorescentLaserAim.limitLook(axis,179,pitch,dx,dy);
                check(angle(axis,direction(look.y,look.x)) <= 60.0001,"Tilted cone or pole exceeded 60 degrees");
            }
        }
        near(angle(forward,FluorescentLaserAim.turnTowards(forward,forward.negate(),Math.toRadians(1.25))),1.25,"Opposite aim is finite");
        near(angle(direction(179,0),FluorescentLaserAim.turnTowards(direction(179,0),direction(-179,0),Math.toRadians(1.25))),1.25,"Shortest arc across seam");
        var world = test.getWorld();
        var owner = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), "laser-probe"));
        world.getServer().getPlayerManager().onPlayerConnect(new ClientConnection(NetworkSide.SERVERBOUND), owner);
        RegPlayerFormComponent.PLAYER_FORM.get(owner).nowForm=RegPlayerForms.playerForms.get(FormIdentifiers.AXOLOTL_FLUORESCENT);
        var origin = test.getAbsolutePos(new BlockPos(8,6,8)).toCenterPos();
        owner.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
        var beam = new LaserBeamEntity(world, owner);
        var saved = new NbtCompound();
        beam.writeCustomDataToNbt(saved);
        owner.setYaw(60); owner.setHeadYaw(60);
        for (int[] phase : new int[][]{{0,0,250},{0,100,250},{0,120,150},{0,139,55},{1,0,50},{2,0,50}}) {
            saved.putInt("Phase",phase[0]); saved.putInt("PhaseTicks",phase[1]);
            beam.readCustomDataFromNbt(saved);
            beam.tick();
            double rate = phase[2] / 10.0;
            near(angle(forward,beam.getBeamAim(1)),rate/400,"Actual phase starts with one acceleration step");
            near(owner.getYaw(),60,"Beam never forces camera back");
        }
        saved.putInt("Phase",1); saved.putInt("PhaseTicks",0);
        beam.readCustomDataFromNbt(saved);
        Vec3d eye = owner.getEyePos();
        var inBeam = target(world,eye.add(forward.multiply(16)));
        var underCrosshair = target(world,eye.add(owner.getRotationVec(1).multiply(16)));
        float before = underCrosshair.getHealth();
        for(int i=0;i<10;i++) beam.tick();
        near(angle(forward,beam.getBeamAim(1)),0.6875,"Ten accelerating release ticks");
        check(inBeam.getHealth()<inBeam.getMaxHealth(),"Damage follows visible beam");
        near(underCrosshair.getHealth(),before,"Aiming ahead cannot hit early");
        beam.writeCustomDataToNbt(saved);
        var restored = new LaserBeamEntity(SscAddon.LASER_BEAM_ENTITY,world);
        restored.readCustomDataFromNbt(saved);
        near(angle(restored.getBeamAim(1),beam.getBeamAim(1)),0,"Reload retains heading");
        restored.tick(); beam.tick();
        near(angle(restored.getBeamAim(1),beam.getBeamAim(1)),0,"Reload resumes with the same angular velocity");
        saved.remove("BeamAim");
        var legacy = new LaserBeamEntity(SscAddon.LASER_BEAM_ENTITY,world);
        legacy.readCustomDataFromNbt(saved);legacy.tick();
        near(angle(legacy.getBeamAim(1),owner.getRotationVec(1)),0,"Older entities initialize from caster");
        owner.setYaw(0);owner.setHeadYaw(0);
        var ranged = new LaserBeamEntity(world,owner);
        var firing = new NbtCompound();
        ranged.writeCustomDataToNbt(firing);firing.putInt("Phase",1);ranged.readCustomDataFromNbt(firing);
        near(ranged.beamLength(),48,"Normal beam display and hit length");
        Vec3d muzzle = eye.add(forward.multiply(LaserBeamEntity.arrayDist()));
        var inside = target(world,muzzle.add(forward.multiply(47.9)));
        var outside = target(world,muzzle.add(forward.multiply(48.1)));
        var control = target(world,muzzle.add(8,0,0));
        var magic = owner.getDamageSources().create(net.minecraft.entity.damage.DamageTypes.MAGIC,owner,owner);
        control.damage(magic,20);
        float oldHit = control.getMaxHealth()-control.getHealth();
        check(oldHit>0,"Control receives the former 20-point hit with the same caster modifiers");
        for (int pulse=1;pulse<=6;pulse++) {
            // We advance the beam manually; the target's ordinary between-hit immunity has elapsed each half second.
            inside.timeUntilRegen=0;
            for (int tick=0;tick<10;tick++) ranged.tick();
            near(inside.getMaxHealth()-inside.getHealth(),2 * oldHit * pulse,"Double damage, one pulse per half second");
            near(outside.getHealth(),outside.getMaxHealth(),"Beyond the 48-block endpoint");
        }
        verifyTripleShots(world,owner,false);
        verifyTripleShots(world,owner,true);
        System.out.println("FLUORESCENT_LASER_AIM_WORLD_PASS range=48 pulses=6 control20="+oldHit+" damageAt47.9="+(inside.getMaxHealth()-inside.getHealth()));
    }
}
