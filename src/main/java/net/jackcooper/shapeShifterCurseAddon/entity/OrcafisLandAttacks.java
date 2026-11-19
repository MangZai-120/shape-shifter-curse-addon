package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.tag.BlockTags;

public final class OrcafisLandAttacks {
    public static double groundY(World world, double x, double z, double nearY) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        int bottom = Math.max(world.getBottomY(), MathHelper.floor(nearY)-12);
        for (int y=Math.min(world.getTopY()-1,MathHelper.floor(nearY)+2); y>=bottom; y--) {
            p.set(x,y,z);
            var state = world.getBlockState(p);
            if (state.isIn(BlockTags.LEAVES)) continue;
            var shape = state.getCollisionShape(world,p);
            if (!shape.isEmpty()) return y + shape.getMax(Direction.Axis.Y);
        }
        return world.getTopY(Heightmap.Type.OCEAN_FLOOR, MathHelper.floor(x),MathHelper.floor(z));
    }

    public static final int LIFT = 24, IMPACT = 66, RECOVERED = IMPACT + 100;
    public static final float SHOCK_RANGE = 10;
    public static float shockRadius(float age) {
        return MathHelper.clamp(age / 30,0,1) * SHOCK_RANGE;
    }

    // Fluke positions for the striking part of boss.tail_sweep, in the actor's local X/Z plane.
    private static final float[] TAIL_X = {4.56f,2.36f,-2.16f,-5.43f,-2.26f,3.16f,5.51f,5.57f,5.19f};
    private static final float[] TAIL_Z = {-2.83f,-4.83f,-4.96f,-.40f,5.15f,4.90f,.70f,-.81f,-2.3f};
    public static float tailX(float tick) { return coordinate(TAIL_X,tick); }
    public static float tailZ(float tick) { return coordinate(TAIL_Z,tick); }
    private static float coordinate(float[] values,float tick) {
        float key=MathHelper.clamp((tick-18)/2,0,values.length-1);
        int i=Math.min((int)key,values.length-2);
        return MathHelper.lerp(key-i,values[i],values[i+1]);
    }
}
