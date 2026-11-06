package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable shared input for future target bars, HUDs and network mirrors. */
public record AnomalySnapshot(double maximum, Map<String, Double> values, int remainingTicks,
                              String lastElement, int linkRemaining,
                              List<AnomalyEffects.EffectView> effects,
                              int fullRemainingTicks, Set<String> fullElements) {
    public AnomalySnapshot(double maximum, Map<String, Double> values, int remainingTicks,
                           String lastElement, int linkRemaining, List<AnomalyEffects.EffectView> effects) {
        this(maximum, values, remainingTicks, lastElement, linkRemaining, effects, 0, Set.of());
    }
    public AnomalySnapshot {
        values = Map.copyOf(values);
        effects = List.copyOf(effects);
        fullElements = Set.copyOf(fullElements);
    }

    public boolean empty() {
        return values.isEmpty() && linkRemaining == 0 && effects.isEmpty();
    }
}
