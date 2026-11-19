package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** The crest, debris and physical obstruction share this profile. */
public final class OrcafisBurrowProfile {
    public static final float HALF_WIDTH = 2.05f;
    public static final float HALF_LENGTH = 3.65f;

    private OrcafisBurrowProfile() {}

    public record Surface(BlockPos block, BlockState state, float y) {}

    public static Surface surface(World world, double x, double z, double nearY) {
        int top = Math.min(world.getTopY() - 1, MathHelper.floor(nearY + .55));
        int bottom = Math.max(world.getBottomY(), MathHelper.floor(nearY - 1.35));
        BlockPos.Mutable sample = new BlockPos.Mutable();
        for (int y = top; y >= bottom; y--) {
            sample.set(x,y,z);
            BlockState state = world.getBlockState(sample);
            if (state.getFluidState().isIn(FluidTags.WATER)) return null;
            if (state.isIn(BlockTags.LEAVES)) continue;
            var shape = state.getCollisionShape(world,sample);
            if (shape.isEmpty()) continue;
            float surfaceY = y + (float)shape.getMax(Direction.Axis.Y);
            return Math.abs(surfaceY-nearY) <= 1 ? new Surface(sample.toImmutable(),state,surfaceY) : null;
        }
        return null;
    }

    public static float lift(float sideways, float forward, float age, float strength) {
        float x = sideways / HALF_WIDTH, z = forward / HALF_LENGTH;
        float footprint = 1 - x*x - z*z;
        if (footprint <= 0) return 0;
        float roll = .89f + .16f*MathHelper.cos(forward*1.8f-age*.55f)
                + .09f*MathHelper.sin(forward*3f+sideways*.8f-age*.81f);
        return footprint*MathHelper.sqrt(footprint)*roll*strength;
    }

    public static Box collision(Vec3d origin, float yaw, float age, float strength) {
        double angle = yaw * MathHelper.RADIANS_PER_DEGREE;
        double sin = Math.sin(angle), cos = Math.cos(angle);
        // An ellipse inside the sloped shoulders gives a low, forgiving axis-aligned obstruction.
        double x = Math.hypot(1.10*cos, 2.08*sin);
        double z = Math.hypot(1.10*sin, 2.08*cos);
        double top = lift(0,0,age,strength)*.86;
        return new Box(origin.x-x,origin.y,origin.z-z,origin.x+x,origin.y+top,origin.z+z);
    }
}
