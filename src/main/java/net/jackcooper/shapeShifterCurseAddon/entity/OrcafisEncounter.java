package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import static net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity.Motion;

/** Only locomotion tracks the player. Each attack owns its aim until its recovery ends. */
final class OrcafisEncounter {
    private static final double INLAND_RANGE_SQUARED = 72 * 72;
    private static final int ATTACK_COOLDOWN = 8 * 20;
    private final EnumMap<Motion,Long> lastAttack = new EnumMap<>(Motion.class);
    private final OrcafisEntity boss;
    private final OrcafisHabitat habitat = new OrcafisHabitat();
    private UUID opponent;
    private boolean retreat, inland, surfacingForJet, tideAfterUndertow;
    private int healIn;

    OrcafisEncounter(OrcafisEntity boss) { this.boss = boss; }

    boolean returning() { return retreat; }

    private boolean onCoast(PlayerEntity player) {
        return habitat.withinCoast((ServerWorld)boss.getWorld(), player.getBlockPos());
    }

    private static boolean isWaterTarget(PlayerEntity player) {
        // Forms that stand on the water surface can have zero fluid intersection.
        return player.isTouchingWater() || player.getWorld().getFluidState(
                BlockPos.ofFloored(player.getX(),player.getY()-.25,player.getZ())).isIn(FluidTags.WATER);
    }

    void retaliate(PlayerEntity player) {
        if (retreat || opponent != null || !OrcafisEntity.isHuntTarget(player)) return;
        if (onCoast(player)) {
            engage(player, false);
        } else if (withinInlandRange(player)) {
            engage(player, true);
        }
    }

    private boolean withinInlandRange(PlayerEntity player) {
        double dx = player.getX() - boss.getX();
        double dz = player.getZ() - boss.getZ();
        return dx * dx + dz * dz <= INLAND_RANGE_SQUARED;
    }

    private void engage(PlayerEntity player, boolean inland) {
        this.inland = inland;
        tideAfterUndertow = false;
        opponent = player.getUuid();
        boss.setTarget(player);
        habitat.clearRoute();
        boss.begin(travel(player));
    }

    private long lastUse(Motion skill) {
        return lastAttack.getOrDefault(skill,Long.MIN_VALUE);
    }

    boolean ready(Motion motion) {
        Motion skill = motion.attackSkill();
        return skill == null || lastUse(skill) <= boss.getWorld().getTime() - ATTACK_COOLDOWN;
    }

    void recordAttack(Motion motion,long start) {
        Motion skill = motion.attackSkill();
        if (skill != null) lastAttack.put(skill,start);
    }

    NbtCompound saveAttackUses() {
        NbtCompound uses = new NbtCompound();
        lastAttack.forEach((skill,start) -> uses.putLong(skill.clip,start));
        return uses;
    }

    void loadAttackUses(NbtCompound uses) {
        lastAttack.clear();
        for (Motion skill : Motion.values()) {
            if (skill.attackSkill() == skill && uses.contains(skill.clip))
                lastAttack.put(skill,Math.min(uses.getLong(skill.clip),boss.getWorld().getTime()));
        }
    }

    private Motion travel(PlayerEntity player) {
        boolean wet = isWaterTarget(player);
        if (boss.waterAtFeet() && (wet || !boss.approachingBank(player.getPos()))) return Motion.SWIM;
        return !boss.waterAtFeet() && !wet && (boss.squaredDistanceTo(player) <= 49
                || ready(Motion.ROCK_TOSS) && rockRange(player))
                ? Motion.WALK : Motion.BURROW;
    }

    private void disengage() {
        tideAfterUndertow = false;
        opponent = null;
        boss.setTarget(null);
        retreat = true;
        healIn = 0;
        habitat.startRetreat();
    }

    /** Returns true when idle/retreat owns this tick, including its animation and footsteps. */
    boolean tick(boolean frozen) {
        if (retreat) return reset(frozen);
        ServerWorld world = (ServerWorld)boss.getWorld();
        PlayerEntity target = opponent == null ? null : world.getPlayerByUuid(opponent);
        if (opponent != null && (target == null || !OrcafisEntity.isHuntTarget(target))) {
            if (frozen) {
                disengage();
                return reset(true);
            }
            target = boss.nearbyTarget();
            if (target == null) { disengage(); return reset(false); }
            engage(target, !onCoast(target));
        }
        if (target == null) {
            if (frozen) return true;
            if ((boss.age == 1 || boss.age % 5 == 0) && (target = boss.nearbyTarget()) != null) engage(target, !onCoast(target));
            else {
                boolean moved = habitat.move(boss, false);
                Motion idle = boss.waterAtFeet() ? Motion.SWIM : moved ? Motion.WALK : Motion.REST;
                boss.idleMotion(idle);
                OrcafisSounds.tick(boss, idle, (int)boss.motionTick(0), moved);
                return true;
            }
        }
        if (inland ? !withinInlandRange(target) : !onCoast(target)) {
            disengage();
            return reset(frozen);
        }
        if (frozen) return true;
        if (boss.getTarget() != target) boss.setTarget(target);
        Motion motion = boss.motion();
        if (motion == Motion.WALK || motion == Motion.BURROW || motion == Motion.SWIM) pursue(target, motion);
        return false;
    }

    private boolean reset(boolean frozen) {
        if (healIn-- <= 0) {
            boss.heal(boss.getMaxHealth() * .2f);
            healIn = 19;
        }
        if (frozen || (boss.motion() == Motion.BREACH && boss.motionTick(0) >= 66 && boss.motionTick(0) < 166)) return true;
        if (inland) {
            // An inland encounter may be beyond any reachable ocean. Recover locally and resume normal idle movement.
            boss.idleMotion(boss.waterAtFeet() ? Motion.SWIM : Motion.REST);
            if (boss.getHealth() >= boss.getMaxHealth()) {
                retreat = false;
                inland = false;
            }
            return true;
        }
        boss.idleMotion(boss.waterAtFeet() ? Motion.SWIM : Motion.BURROW);
        if (boss.motion() == Motion.BURROW_ENTER) return true;
        boolean moved = habitat.move(boss, true);
        if (habitat.atRefuge(boss) && boss.getHealth() >= boss.getMaxHealth()) {
            retreat = false;
            habitat.clearRoute();
        }
        OrcafisSounds.tick(boss, boss.motion(), (int)boss.motionTick(0), moved);
        return true;
    }

    private void pursue(PlayerEntity target, Motion motion) {
        Vec3d goal = target.getPos(), delta = goal.subtract(boss.getPos());
        double range = delta.horizontalLength();
        boolean wet = isWaterTarget(target), swimming = motion == Motion.SWIM && boss.waterAtFeet();
        boolean visible = range <= 24 && clearAttackLine(target);
        ArrayList<Motion> choices = availableAttacks(target,swimming,wet,visible,range,Math.abs(delta.y));
        // Reserve the second offensive cast before movement or another ranged skill can consume this window.
        int tideSlot = choices.indexOf(Motion.TIDE);
        if (tideSlot >= 0 && tideSlot < 2 && rangedTarget(target,OrcafisTidePulse.RANGE * OrcafisTidePulse.RANGE)
                && OrcafisShoreAttack.wetTarget(boss.getWorld(),target) != null && boss.begin(Motion.UNDERTOW)) {
            tideAfterUndertow = true;
            return;
        }
        int age = (int)boss.motionTick(0);
        long nextUse = choices.isEmpty() ? Long.MAX_VALUE : lastUse(choices.get(0));
        boolean wantsRange = swimming
                ? olderAndReady(Motion.WATER_JET,nextUse) || target.isTouchingWater() && olderAndReady(Motion.UNDERTOW,nextUse)
                : !boss.waterAtFeet() && !wet && olderAndReady(Motion.ROCK_TOSS,nextUse);
        if (visible && range < 8 && Math.abs(delta.y) < 4 && age < 32 && wantsRange
                && makeSpace(delta,motion)) return;

        // Approach a firing window before committing to a longer charge as the player comes into view.
        if (visible && Math.abs(delta.y) < 4 && age < 32) {
            Motion ranged = swimming && wet && range > 12 && range <= 18 && target.isTouchingWater()
                    ? Motion.UNDERTOW : swimming ? Motion.WATER_JET : Motion.ROCK_TOSS;
            double reach = ranged == Motion.UNDERTOW ? 12 : 18;
            if (range > reach && ready(ranged) && lastUse(ranged) <= nextUse
                    && (swimming || !wet && !boss.waterAtFeet())
                    && (choices.isEmpty() || choices.get(0) != Motion.WATER_JET)) {
                if (swimming) chaseFromWater(target,goal,wet);
                else if (motion == Motion.BURROW) boss.burrowTowards(goal,.58);
                else { boss.face(delta,10); walk(delta,.2); }
                return;
            }
        }

        for (Motion skill : choices) {
            if (skill == Motion.BREACH || skill == Motion.PLOW) {
                if (motion != Motion.BURROW) { boss.begin(Motion.BURROW); return; }
                if (age < 18) { boss.face(delta,14); return; }
            } else if (motion == Motion.BURROW) {
                boss.begin(Motion.WALK);
                return;
            }
            boss.face(delta,16);
            if (boss.begin(skill == Motion.WAVE ? Motion.SURFACE : skill)) return;
        }
        Motion terrain = travel(target);
        if ((terrain == Motion.SWIM) != (motion == Motion.SWIM)) { boss.begin(terrain); return; }
        if (swimming) chaseFromWater(target,goal,wet);
        else if (motion == Motion.BURROW) boss.burrowTowards(goal,.58);
        else if (range > 10 || age >= 59 || wet) boss.begin(Motion.BURROW);
        else {
            boss.face(delta,7);
            if (range > 3.5) walk(delta,.115);
        }
    }

    private ArrayList<Motion> availableAttacks(PlayerEntity target,boolean swimming,boolean wet,
                                               boolean visible,double range,double height) {
        // Narrow firing windows win ties. A used skill goes behind every older eligible skill.
        ArrayList<Motion> choices = new ArrayList<>(6);
        if (swimming && visible) {
            boolean surfaceTarget = wet && target.getWorld().getFluidState(target.getBlockPos().up(2)).isEmpty();
            if (!surfaceTarget && rangedTarget(target,12*12) && OrcafisShoreAttack.wetTarget(boss.getWorld(),target) != null)
                choices.add(Motion.UNDERTOW);
            if (rangedTarget(target,OrcafisEntity.JET_RANGE_SQUARED)) choices.add(Motion.WATER_JET);
            if (range <= OrcafisHuntingAttack.BITE_REACH && height <= 2.8) choices.add(Motion.WATER_BITE);
            if (wet && height < 12) {
                if (surfaceTarget) {
                    if (range >= 8) choices.add(Motion.TIDE);
                    choices.add(Motion.POUNCE);
                    if (range <= 20) choices.add(Motion.WAVE);
                    if (range < 8) choices.add(Motion.TIDE);
                }
                choices.add(Motion.DASH);
            }
        } else if (!boss.waterAtFeet()) {
            if (!wet && visible && rockRange(target)) choices.add(Motion.ROCK_TOSS);
            if (!wet && visible && range <= OrcafisHuntingAttack.BITE_REACH && height <= 2.2) choices.add(Motion.LAND_BITE);
            if (range <= 6.5 && height < 4) choices.add(Motion.SWEEP);
            if (!wet && range <= 19) {
                choices.add(Motion.BREACH);
                choices.add(Motion.PLOW);
            }
        }
        choices.removeIf(skill -> !ready(skill));
        choices.sort(Comparator.comparingLong(this::lastUse));
        return choices;
    }

    private boolean rockRange(PlayerEntity target) {
        double distance = boss.squaredDistanceTo(target);
        return distance >= 8*8 && distance <= 18*18;
    }

    private boolean olderAndReady(Motion skill,long nextUse) {
        return ready(skill) && lastUse(skill) < nextUse;
    }

    private boolean makeSpace(Vec3d delta,Motion motion) {
        Vec3d away = delta.horizontalLengthSquared() < .001 ? Vec3d.fromPolar(0,boss.getYaw()).negate()
                : delta.multiply(-1,0,-1).normalize();
        Vec3d before = boss.getPos();
        if (motion == Motion.SWIM) {
            Vec3d step = away.multiply(.4);
            BlockPos feet = BlockPos.ofFloored(before.add(step).add(0,-.1,0));
            if (!boss.getWorld().getFluidState(feet).isIn(FluidTags.WATER)) return false;
            boss.face(delta,12);
            boss.move(MovementType.SELF,step);
        } else if (motion == Motion.BURROW) boss.burrowTowards(before.add(away),.55);
        else {
            boss.face(delta,12);
            walk(away,.32);
        }
        return boss.getPos().squaredDistanceTo(before) > .005;
    }

    private void walk(Vec3d delta,double speed) {
        Vec3d step = delta.multiply(1,0,1).normalize().multiply(speed);
        double y = OrcafisLandAttacks.groundY(boss.getWorld(),boss.getX()+step.x,boss.getZ()+step.z,boss.getY());
        boss.move(MovementType.SELF,step.add(0,Math.max(-.4,Math.min(.4,y-boss.getY())),0));
    }

    private void chaseFromWater(PlayerEntity target,Vec3d goal,boolean wet) {
        if (!wet) {
            habitat.chaseSwimmer(boss,new Vec3d(goal.x,Math.min(goal.y-.25,boss.swimmingSurface()-.25),goal.z));
        } else {
            // Keep the pursuit point above the seabed. A one-block pool has no place to dive.
            double bottom = OrcafisLandAttacks.groundY(boss.getWorld(),goal.x,goal.z,goal.y);
            double depth = Math.min(goal.y,Math.max(bottom+.04,goal.y-(target.isSubmergedInWater() ? 0 : 1.35)));
            habitat.chaseSwimmer(boss,new Vec3d(goal.x,depth,goal.z));
        }
    }

    boolean rangedTarget(PlayerEntity target,double maxSquared) {
        Vec3d delta = target.getPos().subtract(boss.getPos());
        return delta.horizontalLengthSquared() >= 64 && delta.lengthSquared() <= maxSquared;
    }

    private boolean clearAttackLine(PlayerEntity target) {
        Vec3d end = target.getBoundingBox().getCenter();
        return clearAttackLine(boss.getPos().add(0,.65,0),end) || clearAttackLine(boss.getPos().add(0,1.45,0),end);
    }

    private boolean clearAttackLine(Vec3d start, Vec3d end) {
        return boss.getWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,boss)).getType() == HitResult.Type.MISS;
    }

    Motion after(Motion motion) {
        PlayerEntity target = (PlayerEntity)boss.getTarget();
        return switch (motion) {
            case UNDERTOW -> {
                boolean followUp = tideAfterUndertow;
                tideAfterUndertow = false;
                yield followUp && boss.waterAtFeet() && isWaterTarget(target)
                        && boss.squaredDistanceTo(target) <= OrcafisTidePulse.RANGE * OrcafisTidePulse.RANGE
                        && clearAttackLine(target) ? Motion.TIDE : travel(target);
            }
            case BREACH -> OrcafisShoreAttack.afterProne(boss);
            case ROCK_TOSS -> travel(target);
            case DASH -> Motion.RETURN;
            case WAVE, TIDE -> Motion.DIVE;
            case SURFACE -> {
                boolean jet = surfacingForJet;
                surfacingForJet = false;
                if (jet) yield boss.waterAtFeet() && rangedTarget(target,OrcafisEntity.JET_RANGE_SQUARED)
                        ? Motion.WATER_JET : travel(target);
                yield isWaterTarget(target) ? Motion.WAVE : travel(target);
            }
            case SWEEP -> Motion.BURROW;
            case PLOW, BURROW -> isWaterTarget(target) ? travel(target) : Motion.WALK;
            default -> travel(target);
        };
    }

    NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        if (opponent != null) nbt.putUuid("Opponent",opponent);
        nbt.putBoolean("Retreat",retreat);
        nbt.putBoolean("Inland",inland);
        nbt.putBoolean("SurfacingForJet",surfacingForJet);
        if (tideAfterUndertow) nbt.putBoolean("TideAfterUndertow",true);
        nbt.putInt("HealIn",healIn);
        habitat.write(nbt);
        return nbt;
    }

    void load(NbtCompound nbt) {
        opponent = nbt.containsUuid("Opponent") ? nbt.getUuid("Opponent") : null;
        retreat = nbt.getBoolean("Retreat");
        inland = nbt.getBoolean("Inland");
        surfacingForJet = nbt.getBoolean("SurfacingForJet");
        tideAfterUndertow = nbt.getBoolean("TideAfterUndertow");
        healIn = nbt.getInt("HealIn");
        habitat.read(nbt);
    }
}
