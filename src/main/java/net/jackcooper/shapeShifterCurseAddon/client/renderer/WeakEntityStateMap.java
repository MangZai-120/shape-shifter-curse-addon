package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Weak identity keys: vanilla Entity.equals compares numeric IDs, which can repeat in another world. */
final class WeakEntityStateMap<K, V> {
    private final ReferenceQueue<K> collected = new ReferenceQueue<>();
    private final Map<IdentityReference<K>, V> states = new HashMap<>();

    V computeIfAbsent(K entity, Function<? super K, ? extends V> factory) {
        purge();
        IdentityReference<K> lookup = new IdentityReference<>(entity, null);
        V state = states.get(lookup);
        if (state != null) return state;
        state = factory.apply(entity);
        states.put(new IdentityReference<>(entity, collected), state);
        return state;
    }

    V getOrDefault(K entity, V fallback) {
        purge();
        return states.getOrDefault(new IdentityReference<>(entity, null), fallback);
    }

    void remove(K entity) {
        purge();
        states.remove(new IdentityReference<>(entity, null));
    }

    private void purge() {
        java.lang.ref.Reference<? extends K> reference;
        while ((reference = collected.poll()) != null) states.remove(reference);
    }

    private static final class IdentityReference<K> extends WeakReference<K> {
        private final int hash;

        IdentityReference(K entity, ReferenceQueue<K> queue) {
            super(entity, queue);
            hash = System.identityHashCode(entity);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            K entity = get();
            return entity != null && other instanceof IdentityReference<?> reference && entity == reference.get();
        }
    }
}
