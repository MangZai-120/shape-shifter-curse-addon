package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-target elemental accumulation with one shared, non-decaying expiry time. */
public final class AnomalyLedger {
    public static final double MAXIMUM = 500;
    public static final int RETENTION_TICKS = 20 * 20;
    public static final int LINK_TICKS = 5 * 20;

    public record Hit(double before, double after, boolean reachedMaximum) {}

    /** Immutable display input; rendering never receives the mutable combat ledger. */
    public record Snapshot(double maximum, Map<String, Double> values, int remainingTicks) {
        public Snapshot { values = Map.copyOf(values); }
    }

    private final Map<String, Double> values = new LinkedHashMap<>();
    private long expiresAt = Long.MIN_VALUE;

    /** A zero-contribution hit still refreshes every existing element on this target. */
    public Hit hit(String element, double amount, long now) {
        if (element == null || element.isBlank() || !Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("Invalid anomaly contribution");
        expire(now);
        double before = values.getOrDefault(element, 0.0);
        double after = Math.min(MAXIMUM, before + amount);
        if (after > 0) values.put(element, after);
        if (!values.isEmpty()) expiresAt = deadline(now, RETENTION_TICKS);
        return new Hit(before, after, before < MAXIMUM && after >= MAXIMUM);
    }

    public double value(String element, long now) {
        expire(now);
        return values.getOrDefault(element, 0.0);
    }

    public Map<String, Double> values(long now) {
        expire(now);
        return Map.copyOf(values);
    }

    public Snapshot snapshot(long now) {
        expire(now);
        return new Snapshot(MAXIMUM, values, remainingTicks(now));
    }

    /** Trigger consumption is explicit so the effect controller owns that gameplay rule. */
    public void clearElement(String element) {
        values.remove(element);
        if (values.isEmpty()) expiresAt = Long.MIN_VALUE;
    }

    public int remainingTicks(long now) {
        expire(now);
        return values.isEmpty() ? 0 : (int) Math.min(RETENTION_TICKS, expiresAt - now);
    }

    public boolean expire(long now) {
        if (values.isEmpty() || now < expiresAt) return false;
        clear();
        return true;
    }

    public void clear() {
        values.clear();
        expiresAt = Long.MIN_VALUE;
    }

    public static boolean inLinkWindow(long triggeredAt, long now) {
        return now >= triggeredAt && now < deadline(triggeredAt, LINK_TICKS);
    }

    private static long deadline(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }
}
