package net.jackcooper.shapeShifterCurseAddon.anomaly;

/** Lazy, transient combat state attached directly to a living target. */
public interface AnomalyTargetAccess {
    AnomalyTargetState ssca$anomalyState();
    void ssca$anomalyState(AnomalyTargetState state);
    long ssca$anomalyEpoch();
    void ssca$invalidateAnomalyEpoch();
    long ssca$anomalyLastSent();
    void ssca$anomalyLastSent(long tick);
    int ssca$pendingAnomalyHits();
    void ssca$pendingAnomalyHits(int count);
}
