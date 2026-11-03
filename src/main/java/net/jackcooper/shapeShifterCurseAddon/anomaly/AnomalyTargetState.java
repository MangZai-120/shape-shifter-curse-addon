package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

/** Owned by the server thread; inactive entities never allocate this object. */
public final class AnomalyTargetState {
    private record Activation(long tick, long order) {}
    final LivingEntity target;
    final ServerWorld world;
    final AnomalyLedger ledger = new AnomalyLedger();
    private final Map<String, Activation> activations = new HashMap<>();
    private long order;
    String lastElement = "";
    long lastActivation = Long.MIN_VALUE;

    AnomalyTargetState(LivingEntity target, ServerWorld world) {
        this.target = target;
        this.world = world;
    }

    String activate(String element, long now) {
        String previous = "";
        long latestOrder = -1;
        for (var entry : activations.entrySet()) {
            Activation activation = entry.getValue();
            if (!entry.getKey().equals(element) && activation.order() > latestOrder
                    && AnomalyLedger.inLinkWindow(activation.tick(), now)) {
                previous = entry.getKey();
                latestOrder = activation.order();
            }
        }
        activations.put(element, new Activation(now, ++order));
        lastElement = element;
        lastActivation = now;
        return previous;
    }

    int linkRemaining(long now) {
        return AnomalyLedger.inLinkWindow(lastActivation, now)
                ? (int) (AnomalyLedger.LINK_TICKS - (now - lastActivation)) : 0;
    }
}
