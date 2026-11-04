package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded numeric checkpoints for a temporarily expired visual lease; never retains world/entity objects. */
public final class VisualSampleHistory<K> {
    private record Stamp(UUID anchor, int entityId, long epoch, int sample) {
        boolean matches(UUID nextAnchor, int nextEntityId, long nextEpoch) {
            return anchor.equals(nextAnchor) && entityId == nextEntityId && epoch == nextEpoch;
        }
    }

    private final int capacity;
    private final LinkedHashMap<K, Stamp> stamps = new LinkedHashMap<>();

    public VisualSampleHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("A visual history needs a positive capacity");
        this.capacity = capacity;
    }

    public void retain(K key, UUID anchor, int entityId, long epoch, int sample) {
        if (sample < 0) return;
        Stamp previous = stamps.remove(key);
        if (previous != null && previous.matches(anchor, entityId, epoch)) sample = Math.max(sample, previous.sample);
        stamps.put(key, new Stamp(anchor, entityId, epoch, sample));
        if (stamps.size() > capacity) stamps.remove(stamps.keySet().iterator().next());
    }

    /** Consume the checkpoint only for the exact source/cast identity, including reused integer entity IDs. */
    public int resume(K key, UUID anchor, int entityId, long epoch) {
        Stamp previous = stamps.remove(key);
        return previous != null && previous.matches(anchor, entityId, epoch) ? previous.sample : -1;
    }

    public int size() { return stamps.size(); }
    public void clear() { stamps.clear(); }
}
