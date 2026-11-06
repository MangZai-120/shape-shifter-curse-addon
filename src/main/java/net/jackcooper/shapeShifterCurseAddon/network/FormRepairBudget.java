package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server-thread request budget shared by both client repair channels. */
public final class FormRepairBudget {
    private final Map<Object, Long> lastAccepted = new IdentityHashMap<>();

    public boolean accept(Object connection, long tick) {
        Long previous = lastAccepted.get(connection);
        if (previous != null && tick - previous < 20) return false;
        lastAccepted.put(connection, tick);
        return true;
    }

    public void remove(Object connection) { lastAccepted.remove(connection); }
    public void clear() { lastAccepted.clear(); }
}
