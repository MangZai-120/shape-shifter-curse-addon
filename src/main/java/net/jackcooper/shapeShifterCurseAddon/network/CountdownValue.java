package net.jackcooper.shapeShifterCurseAddon.network;

/** A server-time anchor, not a wall-clock timer. Pauses and minimum values are explicit. */
public record CountdownValue(int value, long tick, int minimum, boolean running) {
    public int at(long now) {
        if (!running) return value;
        long elapsed = Math.max(0, now - tick);
        return (int) Math.max(minimum, (long) value - elapsed);
    }
    public boolean agreesWith(CountdownValue actual) {
        return minimum == actual.minimum && running == actual.running && at(actual.tick) == actual.value;
    }
}
