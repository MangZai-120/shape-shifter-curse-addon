package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import java.util.*;
import java.util.function.Predicate;

/** One world's authoritative FIFO. Three layers TOTAL per host, including all owners and kinds. */
public final class FruitLayerLedger {
    public static final int MAX_LAYERS = 3;
    public record Layer(UUID owner, FruitSelection fruit, long plantedAt, long rootedAt,
                        long expiresAt, long sequence) {
        public boolean rooted(long now) { return now >= rootedAt; }
    }
    private final Map<UUID, ArrayDeque<Layer>> hosts = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> ownerHosts = new HashMap<>();
    private long sequence;

    public Set<UUID> add(UUID host, UUID owner, FruitSelection fruit, long now,
                         int life, int rooting, int maxHosts) {
        Objects.requireNonNull(host); Objects.requireNonNull(owner); Objects.requireNonNull(fruit);
        if (fruit == FruitSelection.AUTO) throw new IllegalArgumentException("Resolve AUTO before planting");
        var changed = new LinkedHashSet<UUID>();
        prune(now, ignored -> true).forEach(changed::add);
        var owned = ownerHosts.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
        if (!owned.contains(host) && owned.size() >= Math.max(1, maxHosts)) {
            UUID oldest = owned.iterator().next();
            removeOwnerFromHost(owner, oldest); changed.add(oldest);
        }
        var layers = hosts.computeIfAbsent(host, ignored -> new ArrayDeque<>());
        if (layers.size() == MAX_LAYERS) layers.removeFirst();
        layers.addLast(new Layer(owner, fruit, now, now + Math.max(0, rooting),
                now + Math.max(1, life), sequence++));
        changed.add(host); rebuildOwners();
        return Set.copyOf(changed);
    }

    public Set<UUID> prune(long now, Predicate<Layer> valid) {
        var changed = new LinkedHashSet<UUID>();
        var iterator = hosts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().removeIf(layer -> now >= layer.expiresAt() || !valid.test(layer)))
                changed.add(entry.getKey());
            if (entry.getValue().isEmpty()) iterator.remove();
        }
        if (!changed.isEmpty()) rebuildOwners();
        return Set.copyOf(changed);
    }

    public Set<UUID> removeOwner(UUID owner) {
        var changed = new LinkedHashSet<UUID>();
        for (UUID host : List.copyOf(hosts.keySet())) {
            if (removeOwnerFromHost(owner, host)) changed.add(host);
        }
        rebuildOwners(); return Set.copyOf(changed);
    }

    public void removeHost(UUID host) { hosts.remove(host); rebuildOwners(); }
    public List<Layer> layers(UUID host) {
        var value = hosts.get(host); return value == null ? List.of() : List.copyOf(value);
    }
    public Set<UUID> hosts() { return Set.copyOf(hosts.keySet()); }

    private boolean removeOwnerFromHost(UUID owner, UUID host) {
        var value = hosts.get(host);
        if (value == null) return false;
        boolean removed = value.removeIf(layer -> layer.owner().equals(owner));
        if (value.isEmpty()) hosts.remove(host);
        return removed;
    }

    private void rebuildOwners() {
        var actual = new HashMap<UUID, LinkedHashSet<UUID>>();
        hosts.forEach((host, layers) -> layers.forEach(layer ->
                actual.computeIfAbsent(layer.owner(), ignored -> new LinkedHashSet<>()).add(host)));
        // Retain the original host insertion order while that owner's layers remain there.
        actual.forEach((owner, active) -> {
            var previous = ownerHosts.get(owner);
            if (previous != null) {
                var ordered = new LinkedHashSet<UUID>();
                previous.stream().filter(active::contains).forEach(ordered::add);
                ordered.addAll(active); actual.put(owner, ordered);
            }
        });
        ownerHosts.clear(); ownerHosts.putAll(actual);
    }
}
