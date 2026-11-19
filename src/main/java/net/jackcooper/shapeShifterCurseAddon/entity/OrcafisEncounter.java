package net.jackcooper.shapeShifterCurseAddon.entity;

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
    private final OrcafisEntity boss;
    private final OrcafisHabitat habitat = new OrcafisHabitat();
    private UUID opponent;
    private boolean retreat, inland, lastAttackWasBite;
    private int healIn, landAttack, seaAttack;

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
        opponent = player.getUuid();
        boss.setTarget(player);
        habitat.clearRoute();
        lastAttackWasBite = false;
        boss.begin(travel(player));
    }

    private Motion travel(PlayerEntity player) {
        boolean wet = isWaterTarget(player);
        if (boss.waterAtFeet() && (wet || !boss.approachingBank(player.getPos()))) return Motion.SWIM;
        return !boss.waterAtFeet() && !wet && boss.squaredDistanceTo(player) <= 49
                ? Motion.WALK : Motion.BURROW;
    }

    private void disengage() {
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
        Motion terrain = travel(target);
        int age = (int)boss.motionTick(0);
        if ((terrain == Motion.SWIM) != (motion == Motion.SWIM)) { boss.begin(terrain); return; }
        if (motion == Motion.SWIM) {
            huntFromWater(target,goal,delta,range,age);
            return;
        }
        if (motion == Motion.WALK) {
            if (!isWaterTarget(target) && age >= 5 && range <= OrcafisHuntingAttack.BITE_REACH
                    && Math.abs(delta.y) <= 2.2 && !lastAttackWasBite && boss.mayStartStrike() && clearAttackLine(target)) {
                boss.begin(Motion.LAND_BITE);
                lastAttackWasBite = true;
            } else if (age >= 18 && range <= 6.5 && Math.abs(delta.y) < 4 && boss.mayStartStrike()) {
                boss.begin(Motion.SWEEP);
                lastAttackWasBite = false;
            } else if (range > 10 || age >= 59 || isWaterTarget(target)) boss.begin(Motion.BURROW);
            else {
                boss.face(delta, 7);
                if (range > 3.5) {
                    Vec3d step = delta.multiply(1,0,1).normalize().multiply(.115);
                    double y = OrcafisLandAttacks.groundY(boss.getWorld(),boss.getX()+step.x,boss.getZ()+step.z,boss.getY());
                    boss.move(MovementType.SELF,step.add(0,Math.max(-.4,Math.min(.4,y-boss.getY())),0));
                }
            }
            return;
        }
        if (!isWaterTarget(target) && !boss.waterAtFeet() && age >= 18 && range <= 19 && boss.mayStartStrike()) {
            boss.begin(landAttack++ % 2 == 0 ? Motion.BREACH : Motion.PLOW);
            lastAttackWasBite = false;
        } else boss.burrowTowards(goal,.58);
    }

    private void huntFromWater(PlayerEntity target, Vec3d goal, Vec3d delta, double range, int age) {
        boolean wet = isWaterTarget(target);
        if (age >= 5 && range <= 24 && Math.abs(delta.y) < 12 && boss.mayStartStrike() && clearAttackLine(target)) {
            if (range <= OrcafisHuntingAttack.BITE_REACH && Math.abs(delta.y) <= 2.8
                    && (!lastAttackWasBite || !wet)) {
                boss.begin(Motion.WATER_BITE);
                lastAttackWasBite = true;
                return;
            }
            if (wet && age >= 10) {
                int choice = seaAttack++ % 5;
                boolean nearSurface = target.getWorld().getFluidState(target.getBlockPos().up(2)).isEmpty();
                Motion attack = switch (choice) {
                    case 1 -> nearSurface ? Motion.POUNCE : Motion.DASH;
                    case 2 -> nearSurface && range <= 20 ? Motion.SURFACE : Motion.DASH;
                    case 4 -> nearSurface ? Motion.TIDE : Motion.DASH;
                    default -> Motion.DASH;
                };
                boss.face(delta,16);
                if (boss.begin(attack)) {
                    lastAttackWasBite = false;
                    return;
                }
            }
        }
        if (!wet) {
            habitat.chaseSwimmer(boss,new Vec3d(goal.x,Math.min(goal.y-.25,boss.swimmingSurface()-.25),goal.z));
        } else {
            // Keep the pursuit point above the seabed. A one-block pool has no place to dive.
            double bottom = OrcafisLandAttacks.groundY(boss.getWorld(),goal.x,goal.z,goal.y);
            double depth = Math.min(goal.y,Math.max(bottom+.04,goal.y-(target.isSubmergedInWater() ? 0 : 1.35)));
            habitat.chaseSwimmer(boss,new Vec3d(goal.x,depth,goal.z));
        }
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
            case BREACH -> Motion.RISE;
            case DASH -> Motion.RETURN;
            case WAVE, TIDE -> Motion.DIVE;
            case SURFACE -> isWaterTarget(target) ? Motion.WAVE : travel(target);
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
        nbt.putBoolean("LastAttackWasBite",lastAttackWasBite);
        nbt.putInt("HealIn",healIn);
        nbt.putInt("LandAttack",landAttack);
        nbt.putInt("SeaAttack",seaAttack);
        habitat.write(nbt);
        return nbt;
    }

    void load(NbtCompound nbt) {
        opponent = nbt.containsUuid("Opponent") ? nbt.getUuid("Opponent") : null;
        retreat = nbt.getBoolean("Retreat");
        inland = nbt.getBoolean("Inland");
        lastAttackWasBite = nbt.getBoolean("LastAttackWasBite");
        healIn = nbt.getInt("HealIn");
        landAttack = nbt.getInt("LandAttack");
        seaAttack = nbt.getInt("SeaAttack");
        habitat.read(nbt);
    }
}
