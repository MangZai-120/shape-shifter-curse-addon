package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-thread only; unrelated operations and new connections never consume each other's budget. */
public final class EvolutionRequestBudget {
    public enum Action { OPEN, PURCHASE, SELECT, START }
    private final Map<Object, EnumMap<Action, Integer>> accepted = new WeakHashMap<>();

    public boolean accept(Object connection, Action action, int tick) {
        var actions = accepted.computeIfAbsent(connection, key -> new EnumMap<>(Action.class));
        Integer last = actions.get(action);
        if (last != null && tick - last < 2) return false;
        actions.put(action, tick);
        return true;
    }
}
