package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A target keeps one shared benefit while at least one source has a live lease. */
public final class SourceLeaseBook<T, S> {
    private final Map<T, Map<S, Long>> targets = new HashMap<>();
    private final Map<S, Set<T>> targetsBySource = new HashMap<>();
    // Renewals and releases may leave this bound earlier than the next actual expiry.
    private long nextExpiry = Long.MAX_VALUE;

    public void renew(T target, S source, long expiresAt) {
        var sources = targets.computeIfAbsent(target, ignored -> new HashMap<>());
        if (sources.put(source, expiresAt) == null) {
            targetsBySource.computeIfAbsent(source, ignored -> new HashSet<>()).add(target);
        }
        nextExpiry = Math.min(nextExpiry, expiresAt);
    }

    public Set<T> release(S source) { return retain(source, Set.of()); }

    public Set<T> retain(S source, Set<T> inRange) {
        Set<T> tracked = targetsBySource.get(source);
        if (tracked == null) return Set.of();
        Set<T> removed = new HashSet<>();
        var iterator = tracked.iterator();
        while (iterator.hasNext()) {
            var target = iterator.next();
            if (inRange.contains(target)) continue;
            iterator.remove();
            var sources = targets.get(target);
            sources.remove(source);
            if (sources.isEmpty()) {
                targets.remove(target);
                removed.add(target);
            }
        }
        if (tracked.isEmpty()) targetsBySource.remove(source);
        return removed;
    }

    public Set<T> expire(long now) {
        if (targets.isEmpty() || now < nextExpiry) return Set.of();
        nextExpiry = Long.MAX_VALUE;
        Set<T> removed = new HashSet<>();
        var iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            var target = iterator.next();
            var leases = target.getValue().entrySet().iterator();
            while (leases.hasNext()) {
                var lease = leases.next();
                if (lease.getValue() > now) {
                    nextExpiry = Math.min(nextExpiry, lease.getValue());
                    continue;
                }
                var tracked = targetsBySource.get(lease.getKey());
                tracked.remove(target.getKey());
                if (tracked.isEmpty()) targetsBySource.remove(lease.getKey());
                leases.remove();
            }
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
        targetsBySource.clear();
        nextExpiry = Long.MAX_VALUE;
        return removed;
    }
}
