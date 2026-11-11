package net.jackcooper.shapeShifterCurseAddon.ability;

/** Validates a requested rope change against server time, geometry and resources. */
public final class SwingReelAuthority {
    public static final int MAX_INPUT_TICKS = 4;
    public static final double CLIMB_PER_TICK = 0.169;
    public static final double DESCEND_PER_TICK = 0.15;
    public static final double TETHER_PER_TICK = 0.16 * 1.5;
    private SwingReelAuthority() {}

    public record Result(double length, double cost, boolean canExtend) {}

    public static Result apply(double current, double requested, int intent, int elapsed,
                               double min, double max, boolean tether, double mana,
                               double blocksPerMana, double groundedDistance) {
        double available = Double.isFinite(mana) ? Math.max(0, mana) : 0;
        boolean canExtend = !tether && available > 1.0e-9 && Double.isFinite(blocksPerMana) && blocksPerMana > 0;
        if (!Double.isFinite(requested) || intent < -1 || intent > 1)
            return new Result(current, 0, canExtend);
        double length = Math.max(min, Math.min(max, current));
        // Ground slack is justified by the server's position, never the requested length.
        if (!tether && intent == 0 && Double.isFinite(groundedDistance))
            length = Math.max(length, Math.max(min, Math.min(max, groundedDistance)));
        int ticks = Math.max(0, Math.min(MAX_INPUT_TICKS, elapsed));
        double target = Math.max(min, Math.min(max, requested));
        if (intent > 0 && target < length) {
            length -= Math.min(length - target, ticks * (tether ? TETHER_PER_TICK : CLIMB_PER_TICK));
        } else if (intent < 0 && canExtend && target > length) {
            double change = Math.min(target - length, ticks * DESCEND_PER_TICK);
            change = Math.min(change, available * blocksPerMana);
            double cost = change / blocksPerMana;
            return new Result(length + change, cost, available - cost > 1.0e-9);
        }
        return new Result(length, 0, canExtend);
    }

    /** Keep only a bounded lead while input is held; released/denied input converges immediately. */
    public static double reconcile(double predicted, double authoritative, int intent,
                                   boolean canExtend, boolean tether) {
        if (!Double.isFinite(predicted) || intent == 0 || intent < 0 && !canExtend) return authoritative;
        double lead = MAX_INPUT_TICKS * (tether ? TETHER_PER_TICK : intent > 0 ? CLIMB_PER_TICK : DESCEND_PER_TICK);
        return intent > 0 ? Math.max(authoritative - lead, Math.min(authoritative, predicted))
                : Math.min(authoritative + lead, Math.max(authoritative, predicted));
    }
}
