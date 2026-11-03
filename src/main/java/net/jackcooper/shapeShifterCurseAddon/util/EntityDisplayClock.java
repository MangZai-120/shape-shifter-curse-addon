package net.jackcooper.shapeShifterCurseAddon.util;

/** Pure display arithmetic; entities retain their authoritative server counters and transitions. */
public final class EntityDisplayClock {
    public static final long UNSET = Long.MIN_VALUE;
    private EntityDisplayClock() {}

    public static int elapsed(long now, long start) {
        if (start == UNSET || now <= start) return 0;
        long delta = now - start;
        return delta < 0 || delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
    }

    public static int remaining(long now, long end) {
        if (end == UNSET || now >= end) return 0;
        long delta = end - now;
        return delta < 0 || delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
    }

    /** Frost forging deliberately restarts its partial cycle even when no thorn was available. */
    public static int cyclicProgress(int base, long start, int period, long now) {
        if (start == UNSET || period <= 0 || now <= start) return base;
        long offset = Math.floorMod(Math.floorMod(now, (long) period) - Math.floorMod(start, (long) period), period);
        return (int) Math.min(Integer.MAX_VALUE, (long) base + offset);
    }
}
