package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A target keeps one shared benefit while at least one source has a live lease. */
public final class SourceLeaseBook<T, S> {
    private final Map<T, Map<S, Long>> targets = new HashMap<>();

    public void renew(T target, S source, long expiresAt) {
        targets.computeIfAbsent(target, ignored -> new HashMap<>()).put(source, expiresAt);
    }

    public Set<T> release(S source) { return retain(source, Set.of()); }

    public Set<T> retain(S source, Set<T> inRange) {
        Set<T> removed = new HashSet<>();
        var iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            var target = iterator.next();
            if (!inRange.contains(target.getKey())) target.getValue().remove(source);
            if (target.getValue().isEmpty()) {
                removed.add(target.getKey());
                iterator.remove();
            }
        }
        return removed;
    }

    public Set<T> expire(long now) {
        Set<T> removed = new HashSet<>();
        var iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            var target = iterator.next();
            target.getValue().values().removeIf(until -> until <= now);
            if (target.getValue().isEmpty()) {
                removed.add(target.getKey());
                iterator.remove();
            }
        }
        return removed;
    }

    public Set<T> clear() {
        Set<T> removed = new HashSet<>(targets.keySet());
        targets.clear();
        return removed;
    }
}
