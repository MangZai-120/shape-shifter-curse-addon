package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** The encounter boundary follows ocean biomes. Idle destinations depend on actual navigable water. */
final class OrcafisHabitat {
    private static final int COAST_RANGE = 64, OPEN_RADIUS = 5, SEARCH_RANGE = 96;
    private BlockPos ocean, refuge, destination, marineHome, blockedSite;
    private BlockPos lastInlandCheck;
    private long retryAt, retryInlandAt, retryBlockedAt;
    private Path path, pursuit;
    private long nextPursuitPath;
    private int stuck, searchOffset;

    boolean withinCoast(ServerWorld world, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ(), y = world.getSeaLevel();
        if (ocean != null && horizontalSquared(ocean,x,z) <= COAST_RANGE*COAST_RANGE
                && world.getBiome(ocean).isIn(BiomeTags.IS_OCEAN)) return true;
        if (lastInlandCheck != null && lastInlandCheck.getX() == x && lastInlandCheck.getZ() == z
                && world.getTime() < retryInlandAt) return false;
        BlockPos.Mutable sample = new BlockPos.Mutable(x,y,z);
        if (rememberOcean(world,sample)) return true;

        // Minecraft selects each column's biome from eight nearby quart samples. Reject whole
        // quarts first, then resolve the actual block columns for an inclusive 64-block radius.
        int quartY = (y-2) >> 2;
        for (int qx = (x-COAST_RANGE-5) >> 2; qx <= (x+COAST_RANGE+2) >> 2; qx++) {
            for (int qz = (z-COAST_RANGE-5) >> 2; qz <= (z+COAST_RANGE+2) >> 2; qz++) {
                int left = qx*4-2, front = qz*4-2;
                int dx = MathHelper.clamp(x,left,left+7)-x, dz = MathHelper.clamp(z,front,front+7)-z;
                if (dx*dx+dz*dz > COAST_RANGE*COAST_RANGE) continue;
                if (!world.getBiomeForNoiseGen(qx,quartY,qz).isIn(BiomeTags.IS_OCEAN)
                        && !world.getBiomeForNoiseGen(qx,quartY+1,qz).isIn(BiomeTags.IS_OCEAN)) continue;
                for (int bx = left; bx < left+8; bx++) {
                    for (int bz = front; bz < front+8; bz++) {
                        if ((bx-x)*(bx-x)+(bz-z)*(bz-z) > COAST_RANGE*COAST_RANGE) continue;
                        if (rememberOcean(world,sample.set(bx,y,bz))) return true;
                    }
                }
            }
        }
        lastInlandCheck = pos.toImmutable();
        retryInlandAt = world.getTime()+60;
        return false;
    }

    private boolean rememberOcean(ServerWorld world, BlockPos pos) {
        if (!world.getBiome(pos).isIn(BiomeTags.IS_OCEAN)) return false;
        ocean = pos.toImmutable();
        return true;
    }

    private static double horizontalSquared(BlockPos pos, int x, int z) {
        double dx = (double)pos.getX()-x, dz = (double)pos.getZ()-z;
        return dx*dx+dz*dz;
    }

    private static BlockPos openWater(ServerWorld world, int x, int z, boolean seaOnly) {
        if (!world.isChunkLoaded(x>>4,z>>4)) return null;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        BlockPos.Mutable sample = new BlockPos.Mutable(x,y,z);
        if (!water(world,sample)) return null;
        if (seaOnly && !world.getBiome(sample.set(x,world.getSeaLevel(),z)).isIn(BiomeTags.IS_OCEAN)) return null;
        for (int cx = (x-OPEN_RADIUS)>>4; cx <= (x+OPEN_RADIUS)>>4; cx++) {
            for (int cz = (z-OPEN_RADIUS)>>4; cz <= (z+OPEN_RADIUS)>>4; cz++) {
                if (!world.isChunkLoaded(cx,cz)) return null;
            }
        }
        // All columns share three unobstructed layers, excluding isolated puddles and narrow channels.
        for (int bx = x-OPEN_RADIUS; bx <= x+OPEN_RADIUS; bx++) {
            for (int bz = z-OPEN_RADIUS; bz <= z+OPEN_RADIUS; bz++) {
                for (int depth = 0; depth < 3; depth++) {
                    if (!water(world,sample.set(bx,y-depth,bz))) return null;
                }
            }
        }
        return new BlockPos(x,y-2,z);
    }

    private static boolean water(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.getFluidState().isIn(FluidTags.WATER) && state.getCollisionShape(world,pos).isEmpty();
    }

    void clearRoute() {
        path = null;
        pursuit = null;
        nextPursuitPath = 0;
        destination = null;
        stuck = 0;
        retryAt = 0;
    }

    boolean atRefuge(OrcafisEntity boss) {
        return refuge != null && boss.waterAtFeet()
                && horizontalSquared(refuge,boss.getBlockX(),boss.getBlockZ()) <= 4
                && Math.abs(boss.getY()-refuge.getY()) < 3;
    }

    void startRetreat() {
        clearRoute();
        refuge = null;
    }

    boolean move(OrcafisEntity boss, boolean retreat) {
        ServerWorld world = (ServerWorld)boss.getWorld();
        long now = world.getTime();
        if (destination == null) {
            if (now < retryAt) return false;
            BlockPos here = boss.waterAtFeet() ? openWater(world,boss.getBlockX(),boss.getBlockZ(),retreat) : null;
            if (here != null) {
                refuge = here;
                if (retreat || world.getBiome(here.withY(world.getSeaLevel())).isIn(BiomeTags.IS_OCEAN)) marineHome = here;
                if (retreat) { retryAt = now + 20; return false; }
            }
            chooseRoute(boss,world,retreat,here != null);
            if (destination == null) {
                retryAt = now + (retreat ? 30 : 65) + boss.getRandom().nextInt(20);
                return false;
            }
        }
        Vec3d before = boss.getPos();
        Vec3d goal = Vec3d.ofCenter(destination);
        if (retreat) {
            if (boss.waterAtFeet() && stuck < 3) boss.swimTowards(goal,.40);
            else boss.burrowTowards(goal,.63);
        } else {
            followPath(boss,path,boss.waterAtFeet() ? .20 : .115);
        }
        boolean moved = before.squaredDistanceTo(boss.getPos()) > .00001;
        stuck = moved ? 0 : stuck+1;
        boolean finished = path != null && path.isFinished();
        boolean arrived = boss.getPos().squaredDistanceTo(goal) < 2;
        if (finished || arrived || stuck > 22) {
            if (path != null && !arrived && (!path.reachesTarget() || stuck > 22)) {
                // A walled-off pool must not monopolize every subsequent path search.
                blockedSite = destination;
                retryBlockedAt = now + 400;
            }
            clearRoute();
            retryAt = now + (retreat ? 1 : 8);
        }
        return moved;
    }

    private void chooseRoute(OrcafisEntity boss, ServerWorld world, boolean retreat, boolean inOpenWater) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        if (inOpenWater) {
            for (int i=0; i<16; i++) {
                double theta = boss.getRandom().nextDouble()*Math.PI*2;
                int distance = 4 + boss.getRandom().nextInt(10);
                addCandidate(world,candidates,boss.getBlockX()+(int)(distance*Math.cos(theta)),
                        boss.getBlockZ()+(int)(distance*Math.sin(theta)),false);
            }
        } else {
            collectWaters(boss,world,candidates,retreat);
        }
        if (retreat) {
            if (!candidates.isEmpty()) {
                destination = candidates.iterator().next();
                refuge = destination;
            } else if (marineHome != null && horizontalSquared(marineHome,boss.getBlockX(),boss.getBlockZ()) > 4) {
                destination = marineHome;
            } else if (ocean != null && horizontalSquared(ocean,boss.getBlockX(),boss.getBlockZ()) > 4) {
                destination = ocean;
            }
            return;
        }
        if (candidates.isEmpty() && !inOpenWater) {
            // Continue exploring loaded terrain when a spawn egg places the animal well inland.
            for (int i=0; i<5; i++) {
                int x = boss.getBlockX()+boss.getRandom().nextBetween(-30,30);
                int z = boss.getBlockZ()+boss.getRandom().nextBetween(-30,30);
                if (world.isChunkLoaded(x>>4,z>>4)) {
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
                    if (Math.abs(boss.getY()-y)<12) candidates.add(new BlockPos(x,y,z));
                }
            }
        }
        if (candidates.isEmpty()) return;
        path = boss.getNavigation().findPathTo(candidates,1);
        if (path != null && path.getLength() > 1) destination = path.getTarget();
        else {
            if (path != null) { blockedSite = path.getTarget(); retryBlockedAt = world.getTime()+400; }
            path = null;
        }
    }

    private void collectWaters(OrcafisEntity boss, ServerWorld world, Set<BlockPos> candidates, boolean retreat) {
        // Shift the sample grid on retries to find even a lake just large enough to fit.
        int shiftX = searchOffset%6, shiftZ = searchOffset/6;
        searchOffset = (searchOffset+7)%36;
        int x = boss.getBlockX()+shiftX, z = boss.getBlockZ()+shiftZ;
        for (int r=0; r<=SEARCH_RANGE && candidates.size()<16; r+=6) {
            for (int offset=-r; offset<=r; offset+=6) {
                addCandidate(world,candidates,x+offset,z+r,retreat);
                if (r!=0) addCandidate(world,candidates,x+offset,z-r,retreat);
                if (Math.abs(offset)!=r) {
                    addCandidate(world,candidates,x+r,z+offset,retreat);
                    addCandidate(world,candidates,x-r,z+offset,retreat);
                }
            }
        }
    }

    private void addCandidate(ServerWorld world, Set<BlockPos> targets, int x, int z, boolean oceanOnly) {
        if (!oceanOnly && blockedSite!=null && world.getTime()<retryBlockedAt && horizontalSquared(blockedSite,x,z)<144) return;
        BlockPos p = openWater(world,x,z,oceanOnly);
        if (p != null) targets.add(p);
    }

    void chaseSwimmer(OrcafisEntity boss, Vec3d goal) {
        long now = boss.getWorld().getTime();
        if (pursuit != null && !pursuit.isFinished() && now < nextPursuitPath) {
            followPath(boss,pursuit,.44);
            return;
        }
        pursuit = null;
        Vec3d before = boss.getPos();
        boss.swimTowards(goal,.48);
        if (now >= nextPursuitPath && before.squaredDistanceTo(goal)>4
                && before.squaredDistanceTo(boss.getPos())<.025) {
            pursuit = boss.getNavigation().findPathTo(BlockPos.ofFloored(goal),1);
            nextPursuitPath = now + 25;
        }
    }

    private void followPath(OrcafisEntity boss, Path path, double speed) {
        Vec3d position = boss.getPos();
        while (!path.isFinished() && position.squaredDistanceTo(path.getNodePosition(boss))<.55*.55) path.next();
        if (path.isFinished()) return;
        Vec3d step = path.getNodePosition(boss).subtract(position);
        boss.face(step,9);
        if (step.y>.12 && !boss.waterAtFeet()) {
            boss.move(MovementType.SELF,new Vec3d(0,Math.min(.22,step.y),0));
        } else {
            boss.move(MovementType.SELF,step.normalize().multiply(Math.min(speed,step.length())));
        }
    }

    void write(NbtCompound nbt) {
        if(ocean!=null) nbt.putLong("Coast",ocean.asLong());
        if(refuge!=null) nbt.putLong("Refuge",refuge.asLong());
        if(marineHome!=null) nbt.putLong("SeaHome",marineHome.asLong());
    }
    void read(NbtCompound nbt) {
        clearRoute();
        ocean=nbt.contains("Coast")?BlockPos.fromLong(nbt.getLong("Coast")):null;
        marineHome=nbt.contains("SeaHome")?BlockPos.fromLong(nbt.getLong("SeaHome")):null;
        refuge=nbt.contains("Refuge")?BlockPos.fromLong(nbt.getLong("Refuge")):null;
    }
}
