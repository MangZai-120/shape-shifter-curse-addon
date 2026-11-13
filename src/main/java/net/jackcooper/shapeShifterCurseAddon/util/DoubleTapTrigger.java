package net.jackcooper.shapeShifterCurseAddon.util;

/** Two press edges separated by a release; a completed pair is consumed once. */
public final class DoubleTapTrigger {
    private final long windowMillis;
    private boolean held;
    private long firstPress = Long.MIN_VALUE;

    public DoubleTapTrigger(long windowMillis) {
        if (windowMillis <= 0) throw new IllegalArgumentException("Double-tap window must be positive");
        this.windowMillis = windowMillis;
    }

    public boolean update(boolean pressed, long nowMillis) {
        if (!pressed) {
            held = false;
            return false;
        }
        if (held) return false;
        held = true;
        boolean triggered = firstPress != Long.MIN_VALUE
                && nowMillis >= firstPress && nowMillis - firstPress <= windowMillis;
        firstPress = triggered ? Long.MIN_VALUE : nowMillis;
        return triggered;
    }
}
