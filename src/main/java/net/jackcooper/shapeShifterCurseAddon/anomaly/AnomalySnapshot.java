package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.List;
import java.util.Map;

/** Immutable shared input for future target bars, HUDs and network mirrors. */
public record AnomalySnapshot(double maximum, Map<String, Double> values, int remainingTicks,
                              String lastElement, int linkRemaining,
                              List<AnomalyEffects.EffectView> effects) {
    public AnomalySnapshot {
        values = Map.copyOf(values);
        effects = List.copyOf(effects);
    }

    public boolean empty() {
        return values.isEmpty() && linkRemaining == 0 && effects.isEmpty();
    }
}
