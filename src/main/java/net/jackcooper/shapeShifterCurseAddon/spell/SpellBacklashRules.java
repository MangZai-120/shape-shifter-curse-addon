package net.jackcooper.shapeShifterCurseAddon.spell;

/** Pure rules for a failed cast; rarity is white=1 through red=6. */
public final class SpellBacklashRules {
    private SpellBacklashRules() {}

    public static double excess(double collapse, double safety) {
        return Double.isFinite(collapse) && Double.isFinite(safety) && safety > 0
                ? Math.max(0, (collapse - safety) / safety) : 0;
    }

    public static double quality(int rarity) {
        return .90 + .04 * (Math.max(1, Math.min(6, rarity)) - 1);
    }

    public static float damage(double normalHit, double excess, int rarity) {
        if (!(normalHit > 0) || !(excess > 0) || !Double.isFinite(normalHit) || !Double.isFinite(excess)) return 0;
        return (float)Math.max(1, Math.min(40, normalHit * excess * quality(rarity)));
    }

    public static double durationMultiplier(double excess, int rarity) {
        if (!Double.isFinite(excess)) return .2;
        return Math.max(.2, Math.min(1, .2 + .1 * (Math.max(1, Math.min(6, rarity)) - 1) + .5 * Math.max(0, excess)));
    }

    public static int duration(int originalTicks, double excess, int rarity) {
        return originalTicks <= 0 ? 0 : Math.max(1, (int)Math.round(originalTicks * durationMultiplier(excess, rarity)));
    }
}
