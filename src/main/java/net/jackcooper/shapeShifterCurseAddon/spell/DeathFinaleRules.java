package net.jackcooper.shapeShifterCurseAddon.spell;

/** Shared spherical boundary and countdown rules; no client dependencies. */
public final class DeathFinaleRules {
    public static final int CHARGE_TICKS = 260;
    public static final double RADIUS = 13;
    public static final int AFTERGLOW_TICKS = 16;
    private DeathFinaleRules() {}

    public static boolean contains(double distanceSquared, double radius) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0
                && Double.isFinite(radius) && radius > 0 && distanceSquared <= radius * radius;
    }

    public static int secondsLeft(int elapsed, int duration) {
        return Math.max(0, (duration - elapsed + 19) / 20);
    }
}
