package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.util.math.Vec3d;

/** One local rope step. Collision and movement remain with vanilla; no position or network writes. */
public final class SwingMotion {
    private static final double MIN = SpiderMoonWeaverSwingManager.MIN_ROPE_LEN;
    private static final double MAX = SpiderMoonWeaverSwingManager.MAX_ROPE_REACH;
    private static final double CLIMB = 0.169, DESCEND = 0.15;
    private static final double CLIMB_COS = Math.cos(Math.toRadians(70));

    private SwingMotion() {}

    public record Step(Vec3d velocity, double ropeLength, int reelIntent) {}

    /** A grounded player must be able to start reeling, without needing a normal jump first. */
    public static Step groundStep(Vec3d velocity, Vec3d towardAnchor, double distance,
                                  double ropeLength, boolean climb) {
        double length = Math.max(MIN, Math.min(MAX, ropeLength));
        Vec3d v = velocity;
        int intent = 0;
        if (!climb) {
            length = Math.max(length, Math.min(MAX, distance));
        } else if (towardAnchor.y >= CLIMB_COS) {
            // Start lift along the rope; resting gravity or a ground slide must not cancel it.
            Step lifted = step(Vec3d.ZERO, towardAnchor,
                    distance, length, true, false, true, 0, 0);
            return lifted;
        } else {
            // A shallow anchor first pulls along the ground, until the rope can lift the player.
            double horizontal = Math.sqrt(towardAnchor.x * towardAnchor.x + towardAnchor.z * towardAnchor.z);
            if (horizontal > 1.0e-6) {
                Vec3d direction = new Vec3d(towardAnchor.x / horizontal, 0, towardAnchor.z / horizontal);
                double speed = Math.min(CLIMB, Math.max(0, distance - MIN));
                speed = Math.min(speed, distance * horizontal);
                v = v.add(direction.multiply(speed - v.dotProduct(direction)));
                double projected = Math.hypot(Math.max(0, distance * horizontal - speed), distance * towardAnchor.y);
                length = Math.max(MIN, Math.min(MAX, projected));
                if (speed > 0) intent = 1;
            }
        }
        double horizontalSpeed = v.horizontalLength();
        double limit = tangentialLimit(length);
        if (horizontalSpeed > limit) v = new Vec3d(v.x * limit / horizontalSpeed, v.y, v.z * limit / horizontalSpeed);
        return new Step(v, length, intent);
    }

    public static Step step(Vec3d velocity, Vec3d towardAnchor, double distance, double ropeLength,
                            boolean climb, boolean descend, boolean canExtend, double pushX, double pushZ) {
        double length = Math.max(MIN, Math.min(MAX, ropeLength));
        boolean canClimb = towardAnchor.y >= CLIMB_COS;
        boolean reeling = false;
        int intent = 0;
        Vec3d v = velocity;
        if (climb && canClimb) {
            // Stop at the real minimum; the tangential component is independent of reeling speed.
            double speed = Math.min(CLIMB, Math.max(0, distance - MIN));
            v = v.add(towardAnchor.multiply(speed - v.dotProduct(towardAnchor)));
            length = Math.max(MIN, Math.min(MAX, distance - speed));
            reeling = speed > 0;
            if (speed > 0) intent = 1;
        } else if (descend && canExtend) {
            double speed = Math.min(DESCEND, Math.max(0, MAX - distance));
            v = v.add(towardAnchor.multiply(-speed - v.dotProduct(towardAnchor)));
            length = Math.max(MIN, Math.min(MAX, distance + speed));
            reeling = speed > 0;
            if (speed > 0) intent = -1;
        }
        v = v.add(pushX, 0, pushZ);

        // A newly shortened rope is the destination of this movement, not an existing overstretch.
        // Adding a spring for that same shortening used to accelerate each climb step twice.
        double effectiveLength = canClimb ? length : MAX;
        if (!reeling && distance >= effectiveLength - 1.0e-6) {
            double radial = v.dotProduct(towardAnchor);
            if (radial < 0) v = v.subtract(towardAnchor.multiply(radial));
            double over = Math.max(0, distance - effectiveLength);
            v = v.add(towardAnchor.multiply(Math.min(over * 0.2, 0.3)));
        }

        double radial = v.dotProduct(towardAnchor);
        Vec3d tangent = v.subtract(towardAnchor.multiply(radial));
        double tangentSpeed = tangent.length();
        double tangentLimit = tangentialLimit(length);
        if (tangentSpeed > tangentLimit) tangent = tangent.multiply(tangentLimit / tangentSpeed);
        v = tangent.add(towardAnchor.multiply(radial));
        // Side-mounted short ropes also need a horizontal cap: their motion can be radial rather than tangential.
        double horizontalSpeed = v.horizontalLength();
        double horizontalLimit = reeling ? Math.max(tangentLimit, Math.abs(radial) * Math.sqrt(Math.max(0, 1 - towardAnchor.y * towardAnchor.y))) : tangentLimit;
        if (horizontalSpeed > horizontalLimit) v = v.multiply(horizontalLimit / horizontalSpeed);
        double speed = v.length();
        if (speed > 0.72) v = v.multiply(0.72 / speed);
        return new Step(v, length, intent);
    }

    /** Bound angular speed close to the anchor while retaining the original long-rope cap. */
    public static double tangentialLimit(double ropeLength) {
        return Math.min(0.72, Math.max(0.12, ropeLength * 0.08));
    }
}
