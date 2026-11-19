package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Forward and lateral offsets in blocks along the authored swimming turn. */
final class OrcafisDashPath {
    private record Bend(double end, double side1, double forward1, double side2, double forward2,
                        double side3, double forward3) {}

    private static final Bend[] BENDS = {
            new Bend(49, 0, 23.25, 1.875, 24.125, 5.125, 24.125),
            new Bend(69, 7.625, 24.125, 9.625, 21.75, 9.625, 18.875),
            new Bend(83, 9.625, 16.75, 9.625, 14.625, 9.625, 11.25),
            new Bend(95.6, 9.625, 8.125, 9.625, .625, 9.625, -2.25),
            new Bend(109.6, 9.625, -5, 7.5, -7.0625, 4.8125, -7.0625),
            new Bend(120, 2.625, -7.0625, 0, -5, 0, -2.25),
            new Bend(133, 0, -.5, 0, 0, 0, 0)
    };

    static Vec3d offset(double tick) {
        if (tick <= 12.6) return new Vec3d(0, 0, -.75 * Math.min(tick / 8.4, 1));
        if (tick <= 23) return new Vec3d(0, 0, MathHelper.lerp((tick - 12.6) / 10.4, -.75, 14.875));
        double start = 23, x = 0, z = 14.875;
        for (Bend b : BENDS) {
            if (tick <= b.end) {
                double u = (tick - start) / (b.end - start), v = 1 - u;
                return new Vec3d(v*v*v*x + 3*v*v*u*b.side1 + 3*v*u*u*b.side2 + u*u*u*b.side3, 0,
                        v*v*v*z + 3*v*v*u*b.forward1 + 3*v*u*u*b.forward2 + u*u*u*b.forward3);
            }
            start = b.end;
            x = b.side3;
            z = b.forward3;
        }
        return Vec3d.ZERO;
    }
}
