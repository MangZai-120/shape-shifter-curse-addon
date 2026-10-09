package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.math.Vec3d;

/** Shared spherical boundary and countdown rules; no client dependencies. */
public final class DeathFinaleRules {
    public static final int CHARGE_TICKS = 260;
    public static final double RADIUS = 13;
    public static final double MAX_CAST_DISPLACEMENT = 3;
    public static final int AFTERGLOW_TICKS = 16;
    private DeathFinaleRules() {}

    /** Charge center follows the caster, while displacement is always measured from the start. */
    public static final class ChargePosition {
        private final Vec3d anchor;
        private Vec3d center;

        public ChargePosition(Vec3d start) { anchor = start; center = start; }
        public Vec3d center() { return center; }
        public boolean canContinue(Vec3d current) {
            return contains(current.squaredDistanceTo(anchor), MAX_CAST_DISPLACEMENT);
        }
        public boolean follow(Vec3d current) {
            if (!canContinue(current)) return false;
            center = current;
            return true;
        }
    }

    public static boolean contains(double distanceSquared, double radius) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0
                && Double.isFinite(radius) && radius > 0 && distanceSquared <= radius * radius;
    }

    public static int secondsLeft(int elapsed, int duration) {
        return Math.max(0, (duration - elapsed + 19) / 20);
    }
}
