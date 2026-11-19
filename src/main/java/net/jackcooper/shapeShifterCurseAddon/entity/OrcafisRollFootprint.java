package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Horizontal sections of the prone animal, in its own facing coordinates. */
final class OrcafisRollFootprint {
    private static final Box[] SECTIONS = OrcafisCollisionRig.local(OrcafisEntity.Motion.ROLL_LEFT,0).boxes();

    static double distanceSquared(Box player, Vec3d origin, Vec3d forward, double previous, double current) {
        Vec3d delta = player.getCenter().subtract(origin);
        double along = delta.dotProduct(forward);
        double across = delta.x*forward.z-delta.z*forward.x;
        double halfAlong = .5*(Math.abs(forward.x)*player.getXLength()+Math.abs(forward.z)*player.getZLength());
        double halfAcross = .5*(Math.abs(forward.z)*player.getXLength()+Math.abs(forward.x)*player.getZLength());
        double distance = Double.POSITIVE_INFINITY;
        for (Box section : SECTIONS) {
            if (section == null) continue;
            double x = gap(across-halfAcross,across+halfAcross,
                    section.minX+Math.min(previous,current),section.maxX+Math.max(previous,current));
            double z = gap(along-halfAlong,along+halfAlong,section.minZ,section.maxZ);
            distance = Math.min(distance,x*x+z*z);
        }
        double y = gap(player.minY,player.maxY,origin.y-.4,origin.y+2);
        return distance+y*y;
    }

    private static double gap(double minA,double maxA,double minB,double maxB) {
        return Math.max(0,Math.max(minA-maxB,minB-maxA));
    }
}
