package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffects;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyNetwork;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyRules;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalySnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;

/** Read-only mirror for world badges and target HUDs. No rendering or combat decisions live here. */
public final class AnomalyClient {
    private static final class View {
        final int entityId;
        final AnomalySnapshot snapshot;
        final long received, expiresAt;
        final Map<String, Double> accumulatingValues, fullValues;
        long projectedTick = Long.MIN_VALUE;
        AnomalySnapshot projected;
        View(int entityId, AnomalySnapshot snapshot, long received, long expiresAt) {
            this.entityId = entityId; this.snapshot = snapshot;
            this.received = received; this.expiresAt = expiresAt;
            if (snapshot.fullElements().isEmpty()) {
                accumulatingValues = snapshot.values();
                fullValues = Map.of();
            } else {
                var accumulating = new HashMap<String, Double>();
                var full = new HashMap<String, Double>();
                snapshot.values().forEach((element, amount) -> {
                    (snapshot.fullElements().contains(element) ? full : accumulating).put(element, amount);
                });
                accumulatingValues = Map.copyOf(accumulating);
                fullValues = Map.copyOf(full);
            }
        }
        int entityId() { return entityId; }
        AnomalySnapshot snapshot() { return snapshot; }
        long received() { return received; }
        long expiresAt() { return expiresAt; }
        Map<String, Double> values(long elapsed) {
            boolean accumulating = elapsed < snapshot.remainingTicks();
            boolean full = elapsed < snapshot.fullRemainingTicks();
            return accumulating && full ? snapshot.values() : accumulating ? accumulatingValues : full ? fullValues : Map.of();
        }
    }
    private static final Map<UUID, View> STATES = new HashMap<>();
    private static final List<LivingEntity> TARGETS = new ArrayList<>();
    private static final List<LivingEntity> READ_ONLY_TARGETS = Collections.unmodifiableList(TARGETS);
    private static long targetsTick = Long.MIN_VALUE;
    private static ClientWorld world;
    private static long ticks;
    private static boolean initialized;
    private AnomalyClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(AnomalyNetwork.STATE, (client, handler, buffer, sender) -> {
            AnomalyNetwork.Message message = AnomalyNetwork.read(buffer);
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null
                        || !client.world.getRegistryKey().getValue().equals(message.dimension())) return;
                ensureWorld(client.world);
                if (message.snapshot().empty()) STATES.remove(message.entityUuid());
                else {
                    int duration = Math.max(message.snapshot().remainingTicks(), Math.max(message.snapshot().linkRemaining(), message.snapshot().fullRemainingTicks()));
                    for (var effect : message.snapshot().effects()) duration = Math.max(duration, effect.ticks());
                    STATES.put(message.entityUuid(), new View(message.entityId(), message.snapshot(), ticks, ticks + duration));
                }
                invalidateTargets();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AnomalyNetwork.CONFIG, (client, handler, buffer, sender) -> {
            String json = buffer.readString(AnomalyNetwork.MAX_CONFIG_CHARS);
            client.execute(() -> {
                if (client.getNetworkHandler() == handler) AnomalyRules.acceptClientSnapshot(json);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear();
            AnomalyRules.clearClientSnapshot();
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, unloadedWorld) -> {
            if (world != unloadedWorld || STATES.isEmpty()) return;
            View view = STATES.get(entity.getUuid());
            if (view != null && view.entityId() == entity.getId()) {
                STATES.remove(entity.getUuid());
                invalidateTargets();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ensureWorld(client.world);
            // Single-player combat clocks stop while the integrated server is paused.
            if (client.isPaused()) return;
            ticks++;
            if (STATES.isEmpty() || client.world == null || ticks % 20 != 0) return;
            var iterator = STATES.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                View view = entry.getValue();
                var entity = client.world.getEntityById(view.entityId());
                if (entity != null && !entity.getUuid().equals(entry.getKey())) { iterator.remove(); continue; }
                if (ticks >= view.expiresAt() || entity != null && !entity.isAlive()) iterator.remove();
            }
        });
    }

    public static AnomalySnapshot snapshot(LivingEntity entity) {
        var client = MinecraftClient.getInstance();
        if (!entity.isAlive() || entity.isRemoved() || client.world != world || entity.getWorld() != world) return null;
        View view = STATES.get(entity.getUuid());
        if (view == null || view.entityId() != entity.getId()) return null;
        if (ticks >= view.expiresAt) return null;
        if (view.projectedTick == ticks) return view.projected;
        int elapsed = (int) Math.min(Integer.MAX_VALUE, ticks - view.received());
        var state = view.snapshot();
        int remaining = Math.max(0, state.remainingTicks() - elapsed);
        int link = Math.max(0, state.linkRemaining() - elapsed);
        int full = Math.max(0, state.fullRemainingTicks() - elapsed);
        var effects = state.effects().stream().filter(effect -> effect.ticks() > elapsed)
                .map(effect -> new AnomalyEffects.EffectView(effect.id(), effect.ticks() - elapsed, effect.strength())).toList();
        view.projected = new AnomalySnapshot(state.maximum(), view.values(elapsed), remaining,
                link == 0 ? "" : state.lastElement(), link, effects, full, full == 0 ? java.util.Set.of() : state.fullElements());
        view.projectedTick = ticks;
        return view.projected;
    }

    public static int trackedCount() { return STATES.size(); }

    /** Resolve only synchronized accumulating targets, once per tick; includes NPCs and creatures. */
    public static List<LivingEntity> accumulationTargets() {
        if (world == null || MinecraftClient.getInstance().world != world) return List.of();
        if (targetsTick != ticks) {
            TARGETS.clear();
            for (var entry : STATES.entrySet()) {
                View view = entry.getValue();
                if (view.values(ticks - view.received()).isEmpty()) continue;
                var entity = world.getEntityById(view.entityId());
                if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                        && living.getUuid().equals(entry.getKey())) TARGETS.add(living);
            }
            targetsTick = ticks;
        }
        return READ_ONLY_TARGETS;
    }

    private static void invalidateTargets() {
        TARGETS.clear();
        targetsTick = Long.MIN_VALUE;
    }

    /** Allocation-free view for world badges; time/effects projection stays in snapshot(). */
    public static Map<String, Double> accumulation(LivingEntity entity) {
        var client = MinecraftClient.getInstance();
        if (!entity.isAlive() || entity.isRemoved() || client.world != world || entity.getWorld() != world)
            return Map.of();
        View view = STATES.get(entity.getUuid());
        if (view == null || view.entityId() != entity.getId()) return Map.of();
        return view.values(ticks - view.received());
    }

    private static void ensureWorld(ClientWorld current) {
        if (world != current) { clear(); world = current; }
    }
    private static void clear() { STATES.clear(); invalidateTargets(); world = null; ticks = 0; }
}
