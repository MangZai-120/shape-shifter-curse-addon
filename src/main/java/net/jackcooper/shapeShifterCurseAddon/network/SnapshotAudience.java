package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers only connections that need a final empty snapshot when leaving a visual audience. */
public final class SnapshotAudience {
    private record Connection(Object handler) {}
    private final Map<UUID, Connection> visible = new HashMap<>();

    public boolean shouldSend(UUID player, Object handler, boolean hasVisibleState) {
        Connection previous = visible.get(player);
        return hasVisibleState || previous != null && previous.handler == handler;
    }

    /** Call only after the corresponding authoritative snapshot was actually sent. */
    public void sent(UUID player, Object handler, boolean hasVisibleState) {
        if (hasVisibleState) visible.put(player, new Connection(handler));
        else disconnect(player, handler);
    }

    public void disconnect(UUID player, Object handler) {
        Connection previous = visible.get(player);
        if (previous != null && previous.handler == handler) visible.remove(player);
    }

    public void clear() { visible.clear(); }
    public int size() { return visible.size(); }
}
