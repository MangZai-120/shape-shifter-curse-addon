package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 每目标的未满格累积共用二十秒保留时间；已触发的满格条另共用五秒锁定时间。 */
public final class AnomalyLedger {
    public static final double MAXIMUM = 500;
    public static final int RETENTION_TICKS = 20 * 20;
    public static final int LINK_TICKS = 5 * 20;
    public static final int FULL_TICKS = 5 * 20;

    public record Hit(double before, double after, boolean reachedMaximum) {}

    /** Immutable display input; rendering never receives the mutable combat ledger. */
    public record Snapshot(double maximum, Map<String, Double> values, int remainingTicks,
                           int fullRemainingTicks, Set<String> fullElements) {
        public Snapshot { values = Map.copyOf(values); fullElements = Set.copyOf(fullElements); }
    }

    private final Map<String, Double> values = new LinkedHashMap<>();
    private long expiresAt = Long.MIN_VALUE;
    private final Set<String> fullElements = new LinkedHashSet<>();
    private long fullExpiresAt = Long.MIN_VALUE;

    /** 零贡献命中仍刷新未满格累积；满格展示只由新的异常触发刷新。 */
    public Hit hit(String element, double amount, long now) {
        if (element == null || element.isBlank() || !Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("Invalid anomaly contribution");
        expire(now);
        double before = values.getOrDefault(element, 0.0);
        // 满格期间只保留显示；重复命中既不能重新触发，也不能延长满格时间。
        if (fullElements.contains(element)) {
            if (values.size() > fullElements.size()) expiresAt = deadline(now, RETENTION_TICKS);
            return new Hit(before, before, false);
        }
        double after = Math.min(MAXIMUM, before + amount);
        if (after > 0) values.put(element, after);
        if (values.size() > fullElements.size()) expiresAt = deadline(now, RETENTION_TICKS);
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
        return new Snapshot(MAXIMUM, values, remainingTicks(now), fullRemainingTicks(now), fullElements);
    }

    /** 新异常触发后，所有尚未消失的满格条一起重新保留五秒。 */
    public void holdMaximum(String element, long now) {
        expire(now);
        if (values.getOrDefault(element, 0.0) < MAXIMUM) throw new IllegalStateException("Anomaly is not full");
        fullElements.add(element);
        fullExpiresAt = deadline(now, FULL_TICKS);
        if (values.size() == fullElements.size()) expiresAt = Long.MIN_VALUE;
    }

    /** 显式清理同时移除累积值与满格锁定。 */
    public void clearElement(String element) {
        values.remove(element);
        fullElements.remove(element);
        if (values.size() == fullElements.size()) expiresAt = Long.MIN_VALUE;
        if (fullElements.isEmpty()) fullExpiresAt = Long.MIN_VALUE;
    }

    public int remainingTicks(long now) {
        expire(now);
        return values.size() == fullElements.size() ? 0 : (int) Math.min(RETENTION_TICKS, expiresAt - now);
    }

    public int fullRemainingTicks(long now) {
        expire(now);
        return fullElements.isEmpty() ? 0 : (int) Math.min(FULL_TICKS, fullExpiresAt - now);
    }

    public boolean expire(long now) {
        boolean changed = false;
        if (!fullElements.isEmpty() && now >= fullExpiresAt) {
            for (String element : fullElements) values.remove(element);
            fullElements.clear();
            fullExpiresAt = Long.MIN_VALUE;
            changed = true;
        }
        if (values.size() > fullElements.size() && now >= expiresAt) {
            values.keySet().removeIf(element -> !fullElements.contains(element));
            expiresAt = Long.MIN_VALUE;
            changed = true;
        }
        if (values.size() == fullElements.size()) expiresAt = Long.MIN_VALUE;
        return changed;
    }

    public void clear() {
        values.clear();
        fullElements.clear();
        expiresAt = Long.MIN_VALUE;
        fullExpiresAt = Long.MIN_VALUE;
    }

    public static boolean inLinkWindow(long triggeredAt, long now) {
        return now >= triggeredAt && now < deadline(triggeredAt, LINK_TICKS);
    }

    private static long deadline(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }
}
