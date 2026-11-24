package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Resolve only the collisions introduced by one batch of terrain restoration. */
final class DeathDomainTerrainRestore {
    private static final int SEARCH_RADIUS = 8;
    private final ServerWorld world;
    private final Map<BlockPos, DeathDomainRecovery.Change> written = new HashMap<>();
    private Box bounds;

    DeathDomainTerrainRestore(ServerWorld world) {
        this.world = world;
    }

    void restore(BlockPos pos, BlockState original, BlockState converted, int flags) {
        if (world.setBlockState(pos, original, flags)) {
            written.put(pos, new DeathDomainRecovery.Change(original, converted));
            Box block = new Box(pos);
            bounds = bounds == null ? block : bounds.union(block);
        }
    }

    void finish() {
        if (bounds == null) return;
        for (var entity : world.getEntitiesByClass(LivingEntity.class, bounds.expand(1), DeathDomainTerrainRestore::canBeTrapped)) {
            var hits = new ArrayList<BlockPos>();
            Box box = entity.getBoundingBox().expand(1);
            for (BlockPos pos : positions(box)) {
                var change = written.get(pos);
                // Neighbour updates later in the batch can remove a block that was successfully written.
                if (change != null && world.getBlockState(pos).equals(change.original())
                        && intersectsNewCollision(world, pos, change, entity)) hits.add(pos.toImmutable());
            }
            if (hits.isEmpty()) continue;
            Vec3d dest = findSpace(entity);
            if (dest != null) {
                entity.requestTeleportAndDismount(dest.x, dest.y, dest.z);
            } else {
                // No safe local landing. Keep the original journal entry pending until the space is vacated.
                for (var pos : hits) {
                    var change = written.remove(pos);
                    if (world.getBlockState(pos).equals(change.original())
                            && world.setBlockState(pos, change.expected(), net.minecraft.block.Block.NOTIFY_ALL)) {
                        DeathDomainRecovery.defer(world, pos, change);
                    }
                }
            }
        }
    }

    static boolean occupied(ServerWorld world, BlockPos pos, DeathDomainRecovery.Change change) {
        return !world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(1),
                e -> canBeTrapped(e) && intersectsNewCollision(world, pos, change, e)).isEmpty();
    }

    private static boolean canBeTrapped(LivingEntity entity) {
        return entity.isAlive() && !entity.noClip && !entity.isSpectator();
    }

    private static boolean intersectsNewCollision(ServerWorld world, BlockPos pos, DeathDomainRecovery.Change change, LivingEntity entity) {
        var context = ShapeContext.of(entity);
        var restored = change.original().getCollisionShape(world, pos, context);
        var old = change.expected().getCollisionShape(world, pos, context);
        var added = VoxelShapes.combine(restored, old, BooleanBiFunction.ONLY_FIRST);
        var localBox = entity.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ());
        return VoxelShapes.matchesAnywhere(added, VoxelShapes.cuboid(localBox), BooleanBiFunction.AND);
    }

    private Vec3d findSpace(LivingEntity entity) {
        Vec3d origin = entity.getPos();
        Box body = entity.getBoundingBox();
        Vec3d center = body.getCenter();
        // Vanilla's continuous fit preserves fractional steps such as soil rising 1/8 block from soul sand.
        Box nearby = Box.of(center, 2, 2, 2);
        if (loaded(nearby.expand(body.getXLength(), body.getYLength(), body.getZLength()).expand(1))) {
            var closest = world.findClosestCollision(entity, VoxelShapes.cuboid(nearby), center,
                    body.getXLength(), body.getYLength(), body.getZLength());
            if (closest.isPresent()) {
                var dest = origin.add(closest.get().subtract(center));
                if (dest.squaredDistanceTo(origin) <= 1 && safe(entity, dest)) return dest;
            }
        }
        Vec3d best = null;
        double limit = SEARCH_RADIUS * SEARCH_RADIUS;
        var feet = entity.getBlockPos();
        for (var pos : BlockPos.iterateOutwards(feet, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            // Manhattan shells beyond this bound cannot contain a closer point.
            if (pos.getManhattanDistance(feet) > Math.sqrt(3 * limit) + 3) break;
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            var shape = world.getBlockState(pos).getCollisionShape(world, pos, ShapeContext.of(entity));
            double y = pos.getY() + (shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y));
            double x = pos.getX() == feet.getX() ? origin.x : pos.getX() + 0.5;
            double z = pos.getZ() == feet.getZ() ? origin.z : pos.getZ() + 0.5;
            var dest = new Vec3d(x, y, z);
            double distance = origin.squaredDistanceTo(dest);
            if (distance <= limit && safe(entity, dest)) {
                best = dest;
                limit = distance;
            }
        }
        return best;
    }

    private boolean safe(LivingEntity entity, Vec3d dest) {
        Box box = entity.getBoundingBox().offset(dest.subtract(entity.getPos()));
        var border = world.getWorldBorder();
        if (box.minY < world.getBottomY() || box.maxY > world.getTopY() || box.minX < border.getBoundWest()
                || box.maxX > border.getBoundEast() || box.minZ < border.getBoundNorth() || box.maxZ > border.getBoundSouth()
                || !loaded(box.expand(1)) || !world.isSpaceEmpty(entity, box)) return false;
        for (var pos : positions(box.stretch(0, -0.01, 0).contract(1.0E-6))) {
            var state = world.getBlockState(pos);
            if (entity.getType().isInvalidSpawn(state)
                    || (!entity.isFireImmune() && state.getFluidState().isIn(FluidTags.LAVA))) return false;
        }
        return entity.hasNoGravity() || entity.isFallFlying() || (entity instanceof ServerPlayerEntity player && player.getAbilities().flying)
                || world.getFluidState(BlockPos.ofFloored(dest)).isIn(FluidTags.WATER)
                || world.getBlockCollisions(entity, box.offset(0, -0.05, 0)).iterator().hasNext();
    }

    private boolean loaded(Box box) {
        return world.isRegionLoaded(MathHelper.floor(box.minX), MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX), MathHelper.floor(box.maxZ));
    }

    private static Iterable<BlockPos> positions(Box box) {
        return BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ));
    }
}
