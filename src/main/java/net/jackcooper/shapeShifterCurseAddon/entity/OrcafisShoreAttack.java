package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import static net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity.Motion;

final class OrcafisShoreAttack {
    private static final double CONE = Math.cos(Math.toRadians(35));
    private static final double TURN = Math.toRadians(35.0/20);
    static final int ROLL_RELEASE = 10, ROLL_LAND = 26;
    private Vec3d direction;
    private Vec3d rollOrigin;
    private boolean rollBase;
    private boolean released;
    private final Map<UUID,Integer> nextContact = new HashMap<>();

    private OrcafisShoreAttack(Vec3d direction) { this.direction = direction; }
    Vec3d direction() { return direction; }

    static OrcafisShoreAttack start(OrcafisEntity boss, Motion motion, PlayerEntity target) {
        switch (motion) {
            case ROCK_TOSS -> {
                if (target != null) boss.face(target.getPos().subtract(boss.getPos()),180);
            }
            case UNDERTOW, WATER_JET, ROLL_LEFT, ROLL_RIGHT -> { }
            default -> { return null; }
        }
        var attack = new OrcafisShoreAttack(Vec3d.fromPolar(0,boss.getYaw()));
        if (motion == Motion.ROLL_LEFT || motion == Motion.ROLL_RIGHT) {
            attack.rollOrigin = boss.getPos();
            attack.rollBase = true;
        }
        return attack;
    }

    static Motion afterProne(OrcafisEntity boss) {
        Vec3d f = Vec3d.fromPolar(0,boss.getYaw());
        PlayerEntity nearest = null;
        double best = 9;
        for (PlayerEntity player : boss.getWorld().getPlayers()) {
            if (!OrcafisEntity.isHuntTarget(player)) continue;
            double d = OrcafisRollFootprint.distanceSquared(player.getBoundingBox(),boss.getPos(),f,0,0);
            if (d <= best) { best = d; nearest = player; }
        }
        if (nearest == null) return Motion.RISE;
        Vec3d offset = nearest.getPos().subtract(boss.getPos());
        return offset.x*f.z-offset.z*f.x >= 0 ? Motion.ROLL_LEFT : Motion.ROLL_RIGHT;
    }

    void tick(OrcafisEntity boss, Motion motion, int t) {
        ServerWorld world = (ServerWorld)boss.getWorld();
        switch (motion) {
            case ROCK_TOSS -> {
                PlayerEntity target = boss.attackPlayer();
                if (target == null || released) return;
                boss.face(target.getPos().subtract(boss.getPos()),20);
                Vec3d mouth = boss.bodyPose().mouth();
                if (t >= 18 && t <= OrcafisRockEntity.PICKUP && t % 3 == 0) {
                    double floor = OrcafisLandAttacks.groundY(world,mouth.x,mouth.z,boss.getY());
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,Blocks.COARSE_DIRT.getDefaultState()),
                            mouth.x,floor+.08,mouth.z,7,.25,.12,.25,.06);
                }
                if (t >= OrcafisRockEntity.RELEASE) {
                    released = true;
                    OrcafisRockEntity.toss(boss,mouth,target.getPos());
                    boss.playSound(SoundEvents.BLOCK_ROOTED_DIRT_BREAK,2,.7f);
                }
            }
            case ROLL_LEFT, ROLL_RIGHT -> {
                if (t < ROLL_RELEASE || t > ROLL_LAND) return;
                if (t == ROLL_RELEASE) {
                    boss.playSound(SoundEvents.ENTITY_RAVAGER_STEP,2,.65f);
                    return;
                }
                double step = 2.7*Math.sin(Math.PI*(t-ROLL_RELEASE-.5)/(ROLL_LAND-ROLL_RELEASE))
                        * Math.sin(Math.PI/(2*(ROLL_LAND-ROLL_RELEASE)));
                if (motion == Motion.ROLL_RIGHT) step = -step;
                Vec3d side = new Vec3d(direction.z,0,-direction.x);
                if (!rollBase) { // Reconstruct the start of an in-progress roll saved by an older version.
                    double travel = 1.35*(1-Math.cos(Math.PI*(t-1-ROLL_RELEASE)/(ROLL_LAND-ROLL_RELEASE)));
                    rollOrigin = boss.getPos().subtract(side.multiply(motion == Motion.ROLL_LEFT ? travel : -travel));
                    rollBase = true;
                }
                double previous = boss.getPos().subtract(rollOrigin).dotProduct(side);
                boss.move(MovementType.SELF,side.multiply(step));
                double current = boss.getPos().subtract(rollOrigin).dotProduct(side);
                for (PlayerEntity p : world.getPlayers()) {
                    if (!OrcafisEntity.isHuntTarget(p) || nextContact.containsKey(p.getUuid())) continue;
                    if (OrcafisRollFootprint.distanceSquared(p.getBoundingBox(),rollOrigin,direction,previous,current) <= 1e-8
                            && p.damage(boss.getDamageSources().mobAttack(boss),12))
                        nextContact.put(p.getUuid(),motion.ticks);
                }
            }
            case UNDERTOW -> undertow(boss,world,t);
            case WATER_JET -> jet(boss,world,t);
            default -> { }
        }
    }

    private void undertow(OrcafisEntity boss, ServerWorld world, int t) {
        int end = OrcafisEntity.UNDERTOW_DRAW_TICKS;
        if (t > end) return;
        PlayerEntity target = boss.attackPlayer();
        Vec3d aim = target == null ? null : wetTarget(world,target);
        if (aim != null) boss.aimIntake(aim);
        Vec3d mouth = boss.bodyPose().mouth();
        if (aim != null && aim.squaredDistanceTo(mouth) > .01) {
            Vec3d facing = Vec3d.fromPolar(-boss.jetElevation()*MathHelper.DEGREES_PER_RADIAN,boss.bodyYaw);
            direction = turnTowards(facing,aim.subtract(mouth),Math.toRadians(65));
        }
        boolean wet = inWater(world,mouth);
        double reach = aim == null ? 8 : MathHelper.clamp(aim.distanceTo(mouth)+.5,8,OrcafisTidePulse.RANGE);
        boss.jetLength((float)reach);
        boss.drawingWater(wet && t < end);
        boss.trackMouth(mouth.subtract(boss.getPos()),direction);
        if (!wet) return;
        double strength = Math.min(1,t/(double)(end-1));
        if (t < end) {
            Vec3d side = new Vec3d(direction.z,0,-direction.x).normalize();
            Vec3d up = direction.crossProduct(side);
            var random = boss.getRandom();
            for (int i = 0; i < 8; i++) {
                double along = .5 + random.nextDouble()*(reach-.7);
                double angle = random.nextDouble()*Math.PI*2;
                double spread = Math.sqrt(random.nextDouble())*along*.68;
                Vec3d point = mouth.add(direction.multiply(along))
                        .add(side.multiply(spread*Math.cos(angle))).add(up.multiply(spread*Math.sin(angle)));
                if (point.squaredDistanceTo(mouth) > reach*reach || !inWater(world,point) || !clear(boss,mouth,point)) continue;
                Vec3d flow = mouth.subtract(point).normalize().multiply(.07+strength*.087);
                world.spawnParticles(ParticleTypes.BUBBLE,point.x,point.y,point.z,0,flow.x,flow.y,flow.z,1);
            }
        } else {
            OrcafisSounds.mouthSound(boss,mouth,OrcafisSounds.UNDERTOW_BITE,1.35f);
        }
        for (PlayerEntity p : world.getPlayers()) {
            if (!OrcafisEntity.isHuntTarget(p)) continue;
            Vec3d point = wetTarget(world,p);
            if (point == null) continue;
            Vec3d delta = point.subtract(mouth);
            double d = delta.length();
            if (d > (t == end ? 2 : reach) || delta.dotProduct(direction) < CONE*d || !clear(boss,mouth,point)) continue;
            if (t == end) p.damage(boss.getDamageSources().mobAttack(boss),10);
            else if (d > .35) {
                // Close the remaining distance during the draw, including players caught at the far end.
                double inward = -p.getVelocity().dotProduct(delta)/d;
                double desired = Math.min(d-.35, Math.max(.06,1.35*(d-.35)/(end-t)));
                double impulse = MathHelper.clamp(desired-inward,0,.24+.16*strength);
                Vec3d pull = delta.multiply(-impulse/d);
                p.addVelocity(pull.x,pull.y,pull.z);
                p.velocityModified = true;
            }
        }
    }

    static Vec3d wetTarget(World world, PlayerEntity p) {
        Vec3d point = p.getBoundingBox().getCenter();
        if (inWater(world,point)) return point;
        point = p.getPos().add(0,.001,0);
        return inWater(world,point) ? point : null;
    }

    private static boolean inWater(World world, Vec3d point) {
        BlockPos pos = BlockPos.ofFloored(point);
        var fluid = world.getFluidState(pos);
        return fluid.isIn(FluidTags.WATER) && point.y < pos.getY()+fluid.getHeight(world,pos);
    }

    private static boolean clear(OrcafisEntity boss, Vec3d a, Vec3d b) {
        return boss.getWorld().raycast(new RaycastContext(a,b,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,boss)).getType() == HitResult.Type.MISS;
    }

    static Vec3d turnTowards(Vec3d current, Vec3d desired) {
        return turnTowards(current,desired,TURN);
    }

    private static Vec3d turnTowards(Vec3d current, Vec3d desired,double limit) {
        if (desired.lengthSquared() < 1e-8) return current;
        Vec3d next = desired.normalize();
        double dot = MathHelper.clamp(current.dotProduct(next),-1,1);
        if (dot >= Math.cos(limit)) return next;
        Vec3d tangent = next.subtract(current.multiply(dot));
        if (tangent.lengthSquared() < 1e-12)
            tangent = current.crossProduct(Math.abs(current.y)<.9 ? new Vec3d(0,1,0) : new Vec3d(1,0,0));
        return current.multiply(Math.cos(limit)).add(tangent.normalize().multiply(Math.sin(limit))).normalize();
    }

    private void jet(OrcafisEntity boss, ServerWorld world, int t) {
        if (t >= OrcafisEntity.JET_END) {
            boss.relaxJet(direction,t);
            if (t == OrcafisEntity.JET_END) OrcafisSounds.mouthSound(boss,boss.bodyPose().mouth(),OrcafisSounds.JET_RELEASE,1.06f);
            boss.jetLength(0);
            return;
        }
        PlayerEntity target = boss.attackPlayer();
        Vec3d mouth = boss.bodyPose().mouth();
        if (target != null && boss.squaredDistanceTo(target) <= OrcafisEntity.JET_RANGE_SQUARED) {
            direction = turnTowards(direction,target.getBoundingBox().getCenter().subtract(mouth));
            boss.aimJet(direction);
            mouth = boss.bodyPose().mouth();
        }
        if (t == 1) OrcafisSounds.mouthSound(boss,mouth,OrcafisSounds.JET_CHARGE,1.12f);
        if (t < OrcafisEntity.JET_START) { boss.trackMouth(mouth.subtract(boss.getPos()),direction); return; }
        if (t == OrcafisEntity.JET_START && !boss.canReleaseJet()) { boss.recoverJet(); return; }
        Vec3d offset = mouth.subtract(boss.getPos());
        double along = offset.dotProduct(direction);
        double length = Math.max(0,Math.sqrt(Math.max(0,along*along+OrcafisEntity.JET_RANGE_SQUARED-offset.lengthSquared()))-along);
        Vec3d end = world.raycast(new RaycastContext(mouth,mouth.add(direction.multiply(length)),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,boss)).getPos();
        boss.jetRay(offset,direction,(float)mouth.distanceTo(end));
        for (PlayerEntity p : world.getPlayers()) {
            if (!OrcafisEntity.isHuntTarget(p) || boss.squaredDistanceTo(p) > OrcafisEntity.JET_RANGE_SQUARED || t < nextContact.getOrDefault(p.getUuid(),0)) continue;
            Box box = p.getBoundingBox().expand(.18);
            if (box.contains(mouth) || box.raycast(mouth,end).isPresent()) {
                nextContact.put(p.getUuid(),t+10);
                p.damage(boss.getDamageSources().mobAttack(boss),3);
            }
        }
    }

    NbtCompound save() {
        NbtCompound tag = new NbtCompound(), contacts = new NbtCompound();
        tag.putDouble("X",direction.x); tag.putDouble("Y",direction.y); tag.putDouble("Z",direction.z);
        tag.putBoolean("Released",released);
        if (rollOrigin != null) {
            tag.putBoolean("RollBase",rollBase); tag.putDouble("RollX",rollOrigin.x); tag.putDouble("RollY",rollOrigin.y); tag.putDouble("RollZ",rollOrigin.z);
        }
        nextContact.forEach((id,t) -> contacts.putInt(id.toString(),t));
        tag.put("Contacts",contacts);
        return tag;
    }

    static OrcafisShoreAttack load(NbtCompound tag) {
        var attack = new OrcafisShoreAttack(new Vec3d(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z")).normalize());
        attack.released = tag.getBoolean("Released");
        attack.rollBase = tag.getBoolean("RollBase");
        if (tag.contains("RollX")) attack.rollOrigin = new Vec3d(tag.getDouble("RollX"),tag.getDouble("RollY"),tag.getDouble("RollZ"));
        var contacts = tag.getCompound("Contacts");
        for (String key : contacts.getKeys()) attack.nextContact.put(UUID.fromString(key),contacts.getInt(key));
        return attack;
    }
}
