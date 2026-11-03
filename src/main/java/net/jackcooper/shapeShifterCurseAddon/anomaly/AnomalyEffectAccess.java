package net.jackcooper.shapeShifterCurseAddon.anomaly;

/** Lazy, entity-local combat cache. Unaffected entities keep a null reference. */
public interface AnomalyEffectAccess {
    AnomalyEffects.Cache ssca$anomalyEffects();
    void ssca$anomalyEffects(AnomalyEffects.Cache cache);
    long ssca$lastAnomalyZoneHitTick();
    void ssca$lastAnomalyZoneHitTick(long tick);
}
