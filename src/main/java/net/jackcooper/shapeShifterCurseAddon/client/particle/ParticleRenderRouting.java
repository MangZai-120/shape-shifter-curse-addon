package net.jackcooper.shapeShifterCurseAddon.client.particle;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/** A view for one render only. Simulation queues and original texture sheets are never mutated. */
public final class ParticleRenderRouting {
    private ParticleRenderRouting() {}

    public static <S, P> Map<S, Queue<P>> forFrame(Map<S, Queue<P>> original, S opaque, S lit, S translucent,
                                                Predicate<P> shouldFade) {
        Set<P> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(original.get(opaque), shouldFade, moved);
        collect(original.get(lit), shouldFade, moved);
        if (moved.isEmpty()) return original;

        Map<S, Queue<P>> frame = new HashMap<>(original);
        Queue<P> blended = new ArrayDeque<>();
        Queue<P> existing = original.get(translucent);
        if (existing != null) blended.addAll(existing);
        split(frame, opaque, moved, blended);
        split(frame, lit, moved, blended);
        frame.put(translucent, blended);
        return frame;
    }

    private static <P> void collect(Queue<P> particles, Predicate<P> predicate, Set<P> moved) {
        if (particles != null) for (P particle : particles) if (predicate.test(particle)) moved.add(particle);
    }

    /** Same routing for AsyncParticles' per-frame synchronous draw sets. Original sets stay untouched. */
    public static <S, P> Set<P> syncBatch(Map<S, Set<P>> queues, S sheet, Set<P> original,
                                         S opaque, S lit, S translucent, Predicate<P> shouldFade) {
        if (sheet.equals(opaque) || sheet.equals(lit)) {
            if (original.stream().noneMatch(shouldFade)) return original;
            Set<P> kept = new java.util.LinkedHashSet<>(original);
            kept.removeIf(shouldFade);
            return kept;
        }
        if (!sheet.equals(translucent)) return original;
        Set<P> blended = null;
        for (S source : java.util.List.of(opaque, lit)) {
            for (P particle : queues.getOrDefault(source, Set.of())) if (shouldFade.test(particle)) {
                if (blended == null) blended = new java.util.LinkedHashSet<>(original);
                blended.add(particle);
            }
        }
        return blended == null ? original : blended;
    }

    private static <S, P> void split(Map<S, Queue<P>> frame, S sheet, Set<P> moved, Queue<P> blended) {
        Queue<P> particles = frame.get(sheet);
        if (particles == null || particles.stream().noneMatch(moved::contains)) return;
        Queue<P> kept = new ArrayDeque<>();
        for (P particle : particles) {
            if (moved.contains(particle)) blended.add(particle);
            else kept.add(particle);
        }
        frame.put(sheet, kept);
    }
}
