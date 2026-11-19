package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import static net.jackcooper.shapeShifterCurseAddon.entity.OrcafisLandAttacks.groundY;

import static net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity.Motion;

/** A committed attack keeps its origin, aim and victims even if the target moves or the chunk reloads. */
final class OrcafisHuntingAttack {
    static final int BITE_IMPACT = 8;
    static final double BITE_REACH = 5;
    private final Vec3d origin;
    private Vec3d focus, forward, right;
    private double dashRise, dashAimDistance = 1;
    private Vec3d priorMouth;
    private Vec3d lungeEnd;
    private Vec3d launch;
    private double dashScale = 1;
    private final Set<UUID> struck = new HashSet<>();
    private final Map<UUID,Integer> wallHits = new HashMap<>();
    private boolean strikeStarted;
    private int lastPulse = -1;
    private final Set<UUID> heardCrest = new HashSet<>();

    float waterline() { return (float) focus.y; }
    boolean hasDashRoute() { return dashScale > 0; }
    NbtCompound splashPoints() {
        NbtCompound points = new NbtCompound();
        points.putDouble("StartX", launch.x);
        points.putDouble("StartZ", launch.z);
        points.putDouble("EndX", focus.x);
        points.putDouble("EndZ", focus.z);
        points.putDouble("Y", focus.y + 2.3);
        return points;
    }

    private OrcafisHuntingAttack(Vec3d origin, Vec3d focus, Vec3d forward) {
        this.origin = origin;
        this.focus = focus;
        this.forward = forward;
        this.right = new Vec3d(forward.z, 0, -forward.x);
        this.launch = new Vec3d(origin.x, focus.y, origin.z);
    }

    static OrcafisHuntingAttack lock(OrcafisEntity boss, PlayerEntity target, Motion motion) {
        Vec3d origin = boss.getPos();
        Vec3d bearing = Vec3d.fromPolar(0, boss.getYaw());
        Vec3d focus = target == null ? origin.add(bearing.multiply(7)) : target.getPos();
        Vec3d moving = target == null ? Vec3d.ZERO : target.getVelocity().multiply(1, 0, 1);
        Vec3d toward = focus.subtract(origin).multiply(1, 0, 1);
        if (toward.lengthSquared() > .01) bearing = toward.normalize();
        if (motion == Motion.PLOW && moving.lengthSquared() > .009) bearing = moving.normalize();

        if (motion == Motion.PLOW && moving.lengthSquared() > .009)
            focus = focus.add(bearing.multiply(Math.min(2, moving.length() * 9)));
        if (motion == Motion.TIDE || motion == Motion.POUNCE) {
            if (motion == Motion.TIDE) focus = origin;
            BlockPos column = BlockPos.ofFloored(focus.x, focus.y - .1, focus.z);
            if (!boss.getWorld().getFluidState(column).isIn(FluidTags.WATER)) {
                column = BlockPos.ofFloored(origin.x, origin.y - .1, origin.z);
                focus = new Vec3d(origin.x, origin.y, origin.z);
            }
            if (boss.getWorld().getFluidState(column).isIn(FluidTags.WATER)) {
                while (column.getY()+1 < boss.getWorld().getTopY() && boss.getWorld().getFluidState(column.up()).isIn(FluidTags.WATER)) column = column.up();
                focus = new Vec3d(focus.x, column.getY() + 1 - (motion == Motion.TIDE ? 0 : 2.3), focus.z);
            }
        }
        if (motion == Motion.BREACH) focus = new Vec3d(focus.x,groundY(boss.getWorld(),focus.x,focus.z,focus.y),focus.z);
        var attack = new OrcafisHuntingAttack(origin, focus, bearing);
        if (motion == Motion.BREACH) {
            double reach = MathHelper.clamp(toward.length(),18,24);
            attack.launch = focus;
            for (int d=1; d<=Math.round(reach); d++) {
                Vec3d p=focus.subtract(bearing.multiply(d));
                double y=groundY(boss.getWorld(),p.x,p.z,attack.launch.y);
                if (boss.getWorld().getFluidState(BlockPos.ofFloored(p.x,y+.05,p.z)).isIn(FluidTags.WATER)) break;
                attack.launch=new Vec3d(p.x,y,p.z);
            }
        }
        if (motion == Motion.POUNCE) {
            double reach = MathHelper.clamp(toward.length(), 18, 24);
            attack.launch = focus;
            for (double d = 1; d <= reach; d = Math.min(reach, d + 1)) {
                Vec3d candidate = focus.subtract(bearing.multiply(d));
                if (!boss.getWorld().getFluidState(BlockPos.ofFloored(candidate)).isIn(FluidTags.WATER)
                        || !hasBodyRoom(boss,candidate)) break;
                attack.launch = candidate;
                if (d == reach) break;
            }
        }
        if (target != null && (motion == Motion.DASH || motion == Motion.WATER_LUNGE))
            attack.focus = target.getBoundingBox().getCenter();
        if (motion == Motion.DASH && target != null) {
            attack.planDash(boss,attack.focus);
        } else if (motion == Motion.DASH) {
            while (attack.dashScale > 0 && !attack.dashFits(boss)) attack.dashScale -= .125;
        }
        return attack;
    }

    private static double progress(int tick, int from, int to) {
        return MathHelper.clamp((double)(tick-from)/(to-from), 0, 1);
    }
    private static double ease(double t) { return t*t*(3-2*t); }

    // Keep momentum through re-entry. Probe wet space ahead before committing to a turn or descent.
    private Vec3d swimAfterSplash(ServerWorld world, OrcafisEntity boss, int t) {
        Vec3d here = boss.getPos();
        double speed = MathHelper.lerp(progress(t,66,90), .46, .23);
        double fall = Math.min(Math.max(0, here.y - (focus.y - 4.5)), t < 82 ? .28 : .12);
        for (int turn : SWIM_TURNS) {
            Vec3d direction = Vec3d.fromPolar(0, boss.getYaw() + turn);
            Vec3d ahead = here.add(direction.multiply(speed));
            for (double y : new double[]{here.y - fall, here.y, Math.min(focus.y, here.y + .18)}) {
                BlockPos cell = BlockPos.ofFloored(ahead.x,y,ahead.z);
                BlockPos look = BlockPos.ofFloored(ahead.x+direction.x*1.4,y,ahead.z+direction.z*1.4);
                Vec3d candidate = new Vec3d(ahead.x,y,ahead.z);
                if (openWater(world,cell) && openWater(world,look) && hasBodyRoom(boss,candidate)) return candidate;
            }
        }
        return here;
    }

    private static final int[] SWIM_TURNS = {0,12,-12,30,-30,60,-60,100,-100,150,-150,180};

    private static boolean openWater(ServerWorld world, BlockPos cell) {
        return world.getFluidState(cell).isIn(FluidTags.WATER) && world.getBlockState(cell).getCollisionShape(world,cell).isEmpty();
    }

    private static boolean hasBodyRoom(OrcafisEntity boss, Vec3d pos) {
        Box body = boss.getBoundingBox().offset(pos.subtract(boss.getPos())).contract(.001);
        return !boss.getWorld().getBlockCollisions(boss,body).iterator().hasNext();
    }

    /** Fit the posed snout to the interception point before validating the entire outgoing and return route. */
    private void planDash(OrcafisEntity boss, Vec3d aim) {
        Vec3d target = aim.subtract(origin);
        double distance = target.horizontalLength();
        if (distance < .01) { dashScale = 0; return; }
        BlockPos water = BlockPos.ofFloored(aim);
        if (!boss.getWorld().getFluidState(water).isIn(FluidTags.WATER)) water = water.down();
        if (!boss.getWorld().getFluidState(water).isIn(FluidTags.WATER)) { dashScale = 0; return; }
        BlockPos floor = water;
        while (floor.getY() > boss.getWorld().getBottomY()
                && boss.getWorld().getFluidState(floor.down()).isIn(FluidTags.WATER)) floor = floor.down();
        for (dashScale=1; dashScale>0; dashScale-=.125) {
            int hitTick = 13;
            Vec3d local = OrcafisDashPath.offset(hitTick).multiply(dashScale);
            Vec3d contact = local.add(OrcafisDashPath.mouth(hitTick));
            while (contact.horizontalLength() < distance && hitTick < 49) {
                local = OrcafisDashPath.offset(++hitTick).multiply(dashScale);
                contact = local.add(OrcafisDashPath.mouth(hitTick));
            }
            if (contact.horizontalLength()+2 < distance && local.horizontalLength()+2 < distance) break;
            // Near the bed the snout cannot dive below the floor. Drive the body through the swimmer instead.
            boolean useBody = aim.y-OrcafisDashPath.mouth(hitTick).y < floor.getY();
            if (useBody) {
                hitTick = 13;
                while (hitTick < 49 && OrcafisDashPath.offset(hitTick).horizontalLength()*dashScale < distance) hitTick++;
                local = OrcafisDashPath.offset(hitTick).multiply(dashScale);
                if (local.horizontalLength()+2 < distance) break;
                contact = local.add(0,MathHelper.clamp(aim.y-origin.y,.55,2.35),0);
            }
            double angle = Math.atan2(target.x,target.z)-Math.atan2(contact.x,contact.z);
            forward = new Vec3d(Math.sin(angle),0,Math.cos(angle));
            right = new Vec3d(forward.z,0,-forward.x);
            dashAimDistance = Math.max(1,local.z);
            dashRise = Math.max(floor.getY()+.03,aim.y-contact.y)-origin.y;
            if (dashFits(boss)) { focus = aim; return; }
        }
        dashScale = 0;
    }

    private void lockCharge(OrcafisEntity boss,Motion motion,int windup) {
        PlayerEntity player = boss.attackPlayer();
        if (player == null) return;
        Vec3d now = player.getBoundingBox().getCenter();
        Vec3d velocity = now.subtract(focus).multiply(1d/windup);
        double travel = motion == Motion.DASH ? Math.max(1,(origin.distanceTo(now)-4)/(1.5*Math.max(.125,dashScale)))
                : Math.min(12,origin.distanceTo(now)/.65);
        Vec3d lead = velocity.multiply(Math.min(12,travel));
        if (lead.lengthSquared()>9) lead = lead.normalize().multiply(3);
        Vec3d aim = now.add(lead);
        if (motion == Motion.WATER_LUNGE) {
            focus = aim;
            Vec3d delta = aim.subtract(origin);
            double range = delta.horizontalLength();
            if (range < .01) return;
            forward = delta.multiply(1,0,1).normalize();
            for (int tick=8; tick<=20; tick++) {
                Vec3d mouth = OrcafisDashPath.lungeMouth(tick);
                double angle = Math.atan2(delta.x,delta.z)-Math.asin(MathHelper.clamp(mouth.x/range,-1,1));
                Vec3d facing = new Vec3d(Math.sin(angle),0,Math.cos(angle));
                Vec3d side = new Vec3d(facing.z,0,-facing.x);
                Vec3d end = aim.subtract(facing.multiply(mouth.z)).subtract(side.multiply(mouth.x)).add(0,-mouth.y,0);
                if (end.distanceTo(origin) > .65*(tick-7) || !openWater((ServerWorld)boss.getWorld(),BlockPos.ofFloored(end))
                        || !hasBodyRoom(boss,end)) continue;
                forward = facing;
                lungeEnd = end;
                break;
            }
            return;
        }
        var plan = new OrcafisHuntingAttack(origin,now,forward);
        plan.planDash(boss,aim);
        if (!plan.hasDashRoute() && lead.lengthSquared()>.001) plan.planDash(boss,now);
        if (!plan.hasDashRoute()) return;
        focus=plan.focus; forward=plan.forward; right=plan.right;
        dashScale=plan.dashScale; dashAimDistance=plan.dashAimDistance; dashRise=plan.dashRise;
    }

    private Vec3d dashPosition(int tick) {
        Vec3d local = OrcafisDashPath.offset(tick).multiply(dashScale);
        return origin.add(right.multiply(local.x)).add(forward.multiply(local.z))
                .add(0,dashRise*MathHelper.clamp(local.z/dashAimDistance,0,1),0);
    }

    private boolean dashFits(OrcafisEntity boss) {
        for (int t = 0; t <= 133; t++) {
            Vec3d at = dashPosition(t);
            BlockPos p = BlockPos.ofFloored(at);
            if (!boss.getWorld().getFluidState(p).isIn(FluidTags.WATER)
                    || !hasBodyRoom(boss,at)) return false;
        }
        return true;
    }

    private Vec3d path(ServerWorld world, Motion motion, int t) {
        if (motion == Motion.POUNCE || motion == Motion.BREACH) {
            if (t < 24) {
                Vec3d p=origin.lerp(launch, ease(progress(t,0,24)));
                return motion == Motion.BREACH ? new Vec3d(p.x,groundY(world,p.x,p.z,p.y),p.z) : p;
            }
            double flight = progress(t,24,66);
            return launch.lerp(focus, flight).add(0, 40 * flight * (1 - flight), 0);
        }
        if (motion == Motion.SWEEP) return origin;
        if (motion == Motion.DASH || motion == Motion.RETURN)
            return dashPosition(t + (motion == Motion.RETURN ? Motion.DASH.ticks : 0));
        if (motion == Motion.PLOW) {
            Vec3d start = focus.subtract(right.multiply(6)), end = focus.add(right.multiply(6));
            Vec3d p = t < 24 ? origin.lerp(start,ease(progress(t,0,24))) : start.lerp(end,ease(progress(t,34,52)));
            return new Vec3d(p.x,world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.x),MathHelper.floor(p.z)),p.z);
        }
        return origin.lerp(focus.subtract(0, OrcafisTidePulse.BODY_DEPTH, 0), ease(progress(t, 0, OrcafisTidePulse.START)))
                .add(0, OrcafisTidePulse.bob(t), 0);
    }

    void tick(ServerWorld world, OrcafisEntity boss, Motion motion, int t) {
        if (motion == Motion.LAND_BITE || motion == Motion.WATER_BITE) {
            bite(world,boss,motion == Motion.WATER_BITE,t);
            return;
        }
        if (motion == Motion.WAVE) {
            boss.setPosition(origin);
            boss.face(forward,180);
            tidalWall(world,boss,t);
            return;
        }
        if ((motion == Motion.DASH && t == 12) || (motion == Motion.WATER_LUNGE && t == 7))
            lockCharge(boss,motion,t);
        boolean swimmingOut = motion == Motion.POUNCE && t > 66;
        Vec3d prev = boss.getPos();
        Vec3d pos = motion == Motion.WATER_LUNGE ? shortLunge(world,boss,t)
                : swimmingOut ? swimAfterSplash(world,boss,t) : path(world,motion,t);
        boss.setPosition(pos.x,pos.y,pos.z);
        Vec3d heading = pos.subtract(prev);
        if (motion == Motion.DASH || motion == Motion.RETURN || motion == Motion.WATER_LUNGE
                || (motion == Motion.POUNCE && !swimmingOut) || motion == Motion.BREACH || motion == Motion.SWEEP) heading = forward;
        if (motion==Motion.PLOW && t>=24 && t<34) heading=right;
        if (heading.horizontalLengthSquared()>.0001) {
            float yaw=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));
            boss.setYaw(MathHelper.stepUnwrappedAngleTowards(boss.getYaw(),yaw, motion==Motion.PLOW && t<34 ? 23 : 15));
        }
        boss.setBodyYaw(boss.getYaw());
        boss.setHeadYaw(boss.getYaw());
        if (motion == Motion.BREACH || motion == Motion.SWEEP) {
            landStrike(world,boss,motion,t);
            return;
        }
        if (motion == Motion.TIDE) {
            tidePulse(world, boss, t);
            return;
        }
        effects(world,boss,motion,t,pos);
        if (motion == Motion.POUNCE && t > OrcafisSplashWave.IMPACT) {
            landingWaves(world,boss,t);
            return;
        }
        boolean dash = motion == Motion.DASH || motion == Motion.WATER_LUNGE;
        Vec3d lastMouth = priorMouth;
        if (dash) priorMouth = boss.bodyPose().mouth();
        boolean contact = switch (motion) {
            case POUNCE -> t==OrcafisSplashWave.IMPACT;
            case PLOW -> t>=34 && t<=52;
            case DASH -> dashScale > 0 && t>=13 && t<=49;
            case WATER_LUNGE -> t>=8 && t<=20;
            default -> false;
        };
        if (!contact || (!strikeStarted && !boss.mayStartStrike())) return;
        strikeStarted=true;
        boss.markStrike();
        double height = switch (motion) {
            case DASH, WATER_LUNGE -> MathHelper.clamp(focus.y - origin.y,.55,2.35);
            case PLOW -> .55;
            default -> 2;
        };
        Vec3d a = prev.add(0,height,0), b = pos.add(0,height,0);
        double radius = dash ? 2.0 : motion == Motion.POUNCE ? 1.9 : 1.5;
        double vertical = dash ? 1.5 : motion == Motion.PLOW ? .45 : 1.25;
        Box swept = new Box(a,b).expand(radius,vertical,radius);
        if (dash && lastMouth != null) swept = swept.union(new Box(lastMouth,priorMouth).expand(radius,vertical,radius));
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,swept,OrcafisEntity::isHuntTarget)) {
            Box body=player.getBoundingBox().expand(radius,vertical,radius);
            boolean hit = body.contains(a) || body.contains(b) || body.raycast(a,b).isPresent();
            if (!hit && dash && lastMouth != null)
                hit = body.contains(priorMouth) || body.contains(lastMouth) || body.raycast(lastMouth,priorMouth).isPresent();
            if (!hit || !struck.add(player.getUuid())) continue;
            player.damage(world.getDamageSources().mobAttack(boss),motion==Motion.PLOW ? 10 : 12);
        }
        if (motion == Motion.POUNCE) landingWaves(world,boss,t);
    }

    private void landingWaves(ServerWorld world, OrcafisEntity boss, int tick) {
        int age = tick-OrcafisSplashWave.IMPACT;
        if (age < 0 || age >= OrcafisSplashWave.MAIN.duration || (!strikeStarted && !boss.mayStartStrike())) return;
        Vec3d center = boss.splashPoint(true);
        if (center == null) return;
        strikeStarted=true;
        boss.markStrike();
        double reach = OrcafisSplashWave.MAIN.range;
        Box area = new Box(center,center).expand(reach,2,reach);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,area,OrcafisEntity::isHuntTarget)) {
            if (struck.contains(player.getUuid()) || !world.getFluidState(
                    BlockPos.ofFloored(player.getX(),center.y-.5,player.getZ())).isIn(FluidTags.WATER)) continue;
            Box relative = player.getBoundingBox().offset(-center.x,-center.y,-center.z);
            if (OrcafisSplashWave.MAIN.touches(age,relative) || OrcafisSplashWave.FOLLOW.touches(age,relative)) {
                struck.add(player.getUuid());
                player.damage(world.getDamageSources().mobAttack(boss),8);
            }
        }
    }

    private void bite(ServerWorld world, OrcafisEntity boss, boolean aquatic, int tick) {
        boss.face(forward,24);
        if (tick >= 4 && tick <= BITE_IMPACT && boss.getPos().subtract(focus).horizontalLengthSquared() > 2) {
            double speed = aquatic ? .18 : .11;
            boss.move(net.minecraft.entity.MovementType.SELF,forward.multiply(speed));
        }
        if (tick != BITE_IMPACT || strikeStarted || !boss.mayStartStrike()) return;
        strikeStarted = true;
        boss.markStrike();
        double height = MathHelper.clamp(focus.y+.55-boss.getY(),.4,aquatic ? 2.8 : 2);
        Vec3d start = boss.getPos().add(0,height,0);
        Vec3d intended = start.add(forward.multiply(BITE_REACH));
        Vec3d end = world.raycast(new RaycastContext(start,intended,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,boss)).getPos();
        if (aquatic) {
            Vec3d nose = start.lerp(end,.4);
            world.spawnParticles(ParticleTypes.BUBBLE,nose.x,nose.y,nose.z,14,.65,.23,.65,.08);
            world.spawnParticles(ParticleTypes.SPLASH,nose.x,nose.y,nose.z,7,.5,.12,.5,.1);
        }
        PlayerEntity victim = null;
        double nearest = Double.POSITIVE_INFINITY;
        Box reach = new Box(start,end).expand(1.05,.85,1.05);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,reach,OrcafisEntity::isHuntTarget)) {
            Box body = player.getBoundingBox().expand(.8,.55,.8);
            Vec3d contact = body.contains(start) ? start : body.raycast(start,end).orElse(null);
            if (contact == null) continue;
            if (world.raycast(new RaycastContext(start,player.getBoundingBox().getCenter(),RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,boss)).getType() != net.minecraft.util.hit.HitResult.Type.MISS) continue;
            double distance = contact.squaredDistanceTo(start);
            if (distance < nearest) {
                victim = player;
                nearest = distance;
            }
        }
        if (victim != null) victim.damage(world.getDamageSources().mobAttack(boss),8);
    }

    private Vec3d shortLunge(ServerWorld world, OrcafisEntity boss, int t) {
        Vec3d current = boss.getPos();
        if (t < 8 || t > 20) return current;
        Vec3d end = lungeEnd != null ? lungeEnd : focus.add(0,-.55,0);
        Vec3d delta = end.subtract(current);
        Vec3d next = current.add(delta.normalize().multiply(Math.min(delta.length(), .65)));
        if (openWater(world, BlockPos.ofFloored(next)) && hasBodyRoom(boss, next)) return next;
        Vec3d level = new Vec3d(next.x, current.y, next.z);
        return openWater(world, BlockPos.ofFloored(level)) && hasBodyRoom(boss, level) ? level : current;
    }

    private void tidalWall(ServerWorld world, OrcafisEntity boss, int tick) {
        int age = tick - OrcafisSounds.WALL_RELEASE;
        if (age < 0 || (!strikeStarted && !boss.mayStartStrike())) return;
        strikeStarted = true;
        boss.markStrike();
        Vec3d center = origin.add(forward.multiply(OrcafisSounds.wallDistance(age)));
        Box area = new Box(center,center).expand(8,5,8);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,area,OrcafisEntity::isHuntTarget)) {
            if (tick < wallHits.getOrDefault(player.getUuid(),0)) continue;
            Vec3d delta = player.getPos().subtract(origin);
            double radius = player.getWidth()*.5*(Math.abs(forward.x)+Math.abs(forward.z));
            if (!OrcafisWallProfile.touches(age,delta.dotProduct(right),delta.dotProduct(forward),radius,origin.y,player.getBoundingBox())) continue;
            wallHits.put(player.getUuid(),tick+20);
            player.damage(world.getDamageSources().mobAttack(boss),6);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,40,0));
        }
    }

    private void landStrike(ServerWorld world, OrcafisEntity boss, Motion motion, int t) {
        boolean leap = motion == Motion.BREACH;
        int age = t - OrcafisLandAttacks.IMPACT;
        if (leap && (t == OrcafisLandAttacks.LIFT || age == 0)) {
            Vec3d site = age == 0 ? focus : launch;
            var ground = world.getBlockState(BlockPos.ofFloored(site.x,site.y-.1,site.z));
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,ground),site.x,site.y+.12,site.z,
                    age == 0 ? 75 : 36,1.1,.14,1.1,.18);
        }
        if (leap ? age<0 || age>30 : t<18 || t>34) return;
        if (!strikeStarted && !boss.mayStartStrike()) return;
        strikeStarted = true;
        boss.markStrike();
        Vec3d center = leap ? focus : origin;
        double reach = leap ? OrcafisLandAttacks.SHOCK_RANGE+.6 : 6.8;
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,new Box(center,center).expand(reach,4,reach),OrcafisEntity::isHuntTarget)) {
            if (struck.contains(player.getUuid())) continue;
            Vec3d delta=player.getPos().subtract(center);
            float damage;
            if (leap) {
                double distance=delta.horizontalLength();
                if (age==0 && distance<=2.05 && delta.y<=2.8 && player.getBoundingBox().maxY>=center.y-.4) damage=16;
                else {
                    if (distance>OrcafisLandAttacks.SHOCK_RANGE || Math.abs(distance-OrcafisLandAttacks.shockRadius(age))>.65) continue;
                    double ground=groundY(world,player.getX(),player.getZ(),center.y);
                    if (player.getY()>ground+1.000001 || player.getBoundingBox().maxY<ground-.1) continue;
                    damage=8;
                }
            } else {
                if (delta.y>3.6 || player.getBoundingBox().maxY<origin.y+.3 || !sweptByTail(delta,t)) continue;
                damage=10;
            }
            struck.add(player.getUuid());
            player.damage(world.getDamageSources().mobAttack(boss),damage);
        }
    }

    private boolean sweptByTail(Vec3d delta, int tick) {
        double x=delta.dotProduct(right),z=delta.dotProduct(forward);
        if (z<-.35) return false;
        for (int step=0;step<=4;step++) {
            float at=tick-1+step*.25f;
            double tx=OrcafisLandAttacks.tailX(at),tz=OrcafisLandAttacks.tailZ(at);
            double along=MathHelper.clamp((x*tx+z*tz)/(tx*tx+tz*tz),.18,1);
            double dx=x-along*tx, dz=z-along*tz;
            if (dx*dx+dz*dz<=1.15*1.15) return true;
        }
        return false;
    }

    private void tidePulse(ServerWorld world, OrcafisEntity boss, int tick) {
        if (!OrcafisTidePulse.active(tick)) return;
        int age = tick - OrcafisTidePulse.START;
        int pulse = age / OrcafisTidePulse.PERIOD;
        int phase = age % OrcafisTidePulse.PERIOD;
        if (lastPulse != pulse) {
            lastPulse = pulse;
            struck.clear();
            heardCrest.clear();
        }
        if (phase == 0) {
            for (int side : new int[]{-1, 1}) {
                Vec3d hand = focus.add(right.multiply(side * 2.25)).add(forward.multiply(1.2));
                world.spawnParticles(ParticleTypes.SPLASH, hand.x, focus.y + .06, hand.z, 36, .45, .12, .55, .19);
            }
        }
        double crest = OrcafisTidePulse.radius(phase);
        if (phase > 0 && !boss.isSilent()) {
            double previous = OrcafisTidePulse.radius(phase-1);
            for (var listener : world.getPlayers()) {
                if (Math.abs(listener.getEyeY()-focus.y) > 6) continue;
                Vec3d offset = listener.getPos().subtract(focus).multiply(1,0,1);
                double distance = offset.horizontalLength();
                if (distance >= 5 && previous < distance && distance <= crest && heardCrest.add(listener.getUuid())) {
                    OrcafisSounds.passingCrest(listener,boss,focus.add(offset.multiply(1-1.4/distance)),pulse);
                }
            }
        }
        if (!strikeStarted && !boss.mayStartStrike()) return;
        strikeStarted = true;
        boss.markStrike();
        double far = Math.min(OrcafisTidePulse.RANGE, crest + OrcafisTidePulse.HALF_WIDTH);
        double near = Math.max(0, crest - OrcafisTidePulse.HALF_WIDTH);
        Box band = new Box(focus.x - far, focus.y - 1, focus.z - far, focus.x + far, focus.y + .12, focus.z + far);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, band, OrcafisEntity::isHuntTarget)) {
            double dx = player.getX() - focus.x, dz = player.getZ() - focus.z;
            double distance = dx*dx + dz*dz;
            if (distance < near*near || distance > far*far || !player.isTouchingWater()) continue;
            BlockPos surface = BlockPos.ofFloored(player.getX(), focus.y - .5, player.getZ());
            if (world.getFluidState(surface).isIn(FluidTags.WATER) && struck.add(player.getUuid()))
                player.damage(world.getDamageSources().mobAttack(boss), 3);
        }
    }

    private void effects(ServerWorld world, OrcafisEntity boss, Motion motion, int t, Vec3d p) {
        if (motion==Motion.POUNCE) {
            if (t==24 || t==66) {
                world.spawnParticles(ParticleTypes.SPLASH,p.x,focus.y+2.36,p.z,t==66 ? 75 : 34,1,.15,1,.25);
            }
        } else if (motion==Motion.PLOW) {
            if (t>=14 && t<=33 && t%3==0) {
                for (int i=-6;i<=6;i++) {
                    Vec3d mark=focus.add(right.multiply(i));
                    int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,MathHelper.floor(mark.x),MathHelper.floor(mark.z));
                    world.spawnParticles(ParticleTypes.SPLASH,mark.x,ground+.1,mark.z,2,.08,.04,.08,.02);
                }
            }
        }
    }

    NbtCompound save() {
        NbtCompound n=new NbtCompound();
        n.putDouble("X",origin.x); n.putDouble("Y",origin.y); n.putDouble("Z",origin.z);
        n.putDouble("AimX",focus.x); n.putDouble("AimY",focus.y); n.putDouble("AimZ",focus.z);
        n.putDouble("DirX",forward.x); n.putDouble("DirZ",forward.z);
        n.putDouble("LaunchX", launch.x); n.putDouble("LaunchY", launch.y); n.putDouble("LaunchZ", launch.z);
        n.putDouble("DashScale", dashScale);
        n.putDouble("DashRise",dashRise); n.putDouble("DashAimDistance",dashAimDistance);
        if (lungeEnd != null) {
            n.putDouble("LungeX",lungeEnd.x); n.putDouble("LungeY",lungeEnd.y); n.putDouble("LungeZ",lungeEnd.z);
        }
        if (priorMouth != null) {
            n.putDouble("MouthX",priorMouth.x); n.putDouble("MouthY",priorMouth.y); n.putDouble("MouthZ",priorMouth.z);
        }
        n.putBoolean("StrikeStarted",strikeStarted);
        n.putInt("LastPulse", lastPulse);
        NbtList victims=new NbtList();
        for(UUID id:struck) victims.add(NbtHelper.fromUuid(id));
        n.put("Victims",victims);
        NbtList contacts=new NbtList();
        wallHits.forEach((id,until) -> {
            NbtCompound hit=new NbtCompound();
            hit.putUuid("Player",id);
            hit.putInt("Next",until);
            contacts.add(hit);
        });
        n.put("WallHits",contacts);
        return n;
    }

    static OrcafisHuntingAttack load(NbtCompound n) {
        var run=new OrcafisHuntingAttack(new Vec3d(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z")),
                new Vec3d(n.getDouble("AimX"),n.getDouble("AimY"),n.getDouble("AimZ")),
                new Vec3d(n.getDouble("DirX"),0,n.getDouble("DirZ")));
        run.strikeStarted=n.getBoolean("StrikeStarted");
        run.lastPulse = n.contains("LastPulse") ? n.getInt("LastPulse") : -1;
        if (n.contains("LaunchX")) run.launch = new Vec3d(n.getDouble("LaunchX"), n.contains("LaunchY") ? n.getDouble("LaunchY") : run.focus.y, n.getDouble("LaunchZ"));
        if (n.contains("DashScale")) run.dashScale = MathHelper.clamp(n.getDouble("DashScale"), 0, 1);
        run.dashRise = n.getDouble("DashRise");
        run.dashAimDistance = Math.max(1,n.getDouble("DashAimDistance"));
        if (n.contains("LungeX")) run.lungeEnd = new Vec3d(n.getDouble("LungeX"),n.getDouble("LungeY"),n.getDouble("LungeZ"));
        if (n.contains("MouthX")) run.priorMouth = new Vec3d(n.getDouble("MouthX"),n.getDouble("MouthY"),n.getDouble("MouthZ"));
        for(NbtElement element:n.getList("WallHits",NbtElement.COMPOUND_TYPE)) {
            NbtCompound hit=(NbtCompound)element;
            if(hit.containsUuid("Player")) run.wallHits.put(hit.getUuid("Player"),hit.getInt("Next"));
        }
        for(NbtElement id:n.getList("Victims",NbtElement.INT_ARRAY_TYPE))
            if(id instanceof NbtIntArray arr && arr.size()==4) run.struck.add(NbtHelper.toUuid(id));
        return run;
    }
}

