package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/** Server-owned accumulation and ordered end-of-tick activation, separate from spell refunds. */
public final class AnomalyManager {
    private static final int SYNC_INTERVAL = 5;
    private static final int RETAINED_EVENTS = 4096;
    private static final Set<AnomalyTargetState> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LivingEntity> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ArrayDeque<PendingHit> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<PendingHit> FREE = new ArrayDeque<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean draining;
    private static long enqueued, processed, triggers, reactions, packets, localQueries;

    private static final class PendingHit {
        LivingEntity target;
        ServerWorld world;
        SpellAnomalyContext context;
        long tick;
        long epoch;
    }

    public record Metrics(long enqueued, long processed, long triggers, long reactions, long packets,
                          long localQueries, int tracked, int pending) {}

    private AnomalyManager() {}

    /** Called after the existing spell, domain and form tick handlers have registered. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        SpellAnomalyHits.amountResolver(AnomalyRules::amount);
        SpellAnomalyHits.register((target, context) -> enqueue(context, target));
        ServerTickEvents.END_SERVER_TICK.register(AnomalyManager::drain);
        ServerLivingEntityEvents.AFTER_DEATH.register((target, source) -> clear(target));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity target) clear(target);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> clear(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> { clear(oldPlayer); clear(player); });
        PlayerSessionCleanup.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
    }

    public static long now(MinecraftServer server) { return Integer.toUnsignedLong(server.getTicks()); }
    public static boolean enabled() { return enabled; }

    /** Isolated benchmark control; normal runtime always starts enabled. */
    public static boolean setEnabledForTesting(boolean next) {
        boolean previous = enabled;
        enabled = next;
        return previous;
    }

    public static void enqueue(SpellAnomalyContext context, LivingEntity target) {
        if (!enabled || context == null || target == null || !target.isAlive() || target.isSpectator()
                || !(target.getWorld() instanceof ServerWorld world)) return;
        AnomalyTargetState existing = state(target);
        if (!AnomalyEffects.hasBase(context.element()) && context.amount() > 0) return;
        if (context.amount() == 0 && existing == null) return;
        PendingHit event = FREE.pollFirst();
        if (event == null) event = new PendingHit();
        event.target = target;
        event.world = world;
        event.context = context;
        event.tick = now(world.getServer());
        event.epoch = ((AnomalyTargetAccess) target).ssca$anomalyEpoch();
        PENDING.addLast(event);
        var access = (AnomalyTargetAccess) target;
        access.ssca$pendingAnomalyHits(access.ssca$pendingAnomalyHits() + 1);
        enqueued++;
    }

    public static void enqueue(LivingEntity target, SpellAnomalyContext context) { enqueue(context, target); }

    /** Preserve hit order when ice triggers before another successful hit in the same server tick. */
    public static void damageObserved(LivingEntity target) {
        if (!enabled || draining || !(target.getWorld() instanceof ServerWorld world)
                || ((AnomalyTargetAccess) target).ssca$pendingAnomalyHits() == 0) return;
        PendingHit event = FREE.pollFirst();
        if (event == null) event = new PendingHit();
        event.target = target;
        event.world = world;
        event.context = null;
        event.tick = now(world.getServer());
        event.epoch = ((AnomalyTargetAccess) target).ssca$anomalyEpoch();
        PENDING.addLast(event);
    }

    public static void drain(MinecraftServer server) {
        if (draining) return;
        draining = true;
        try {
            PendingHit event;
            while ((event = PENDING.pollFirst()) != null) {
                try {
                    if (enabled && event.world.getServer() == server && event.target.isAlive()
                            && !event.target.isRemoved() && event.target.getWorld() == event.world
                            && event.epoch == ((AnomalyTargetAccess) event.target).ssca$anomalyEpoch())
                        if (event.context == null) {
                            if (FrozenControl.blocked(event.target)) FrozenControl.breakFromHit(event.target);
                        } else process(event);
                } finally {
                    if (event.context != null) {
                        var access = (AnomalyTargetAccess) event.target;
                        access.ssca$pendingAnomalyHits(Math.max(0, access.ssca$pendingAnomalyHits() - 1));
                    }
                    event.target = null;
                    event.world = null;
                    event.context = null;
                    if (FREE.size() < RETAINED_EVENTS) FREE.addLast(event);
                }
            }
            if (!ACTIVE.isEmpty()) maintain(server, now(server));
            if (!DIRTY.isEmpty()) synchronize(server, now(server));
        } finally {
            draining = false;
        }
    }

    private static void process(PendingHit event) {
        LivingEntity target = event.target;
        AnomalyTargetState current = state(target);
        if (current != null && (current.target != target || current.world != event.world)) {
            clear(target);
            current = null;
        }
        if (current == null) {
            if (event.context.amount() <= 0) return;
            current = new AnomalyTargetState(target, event.world);
            ((AnomalyTargetAccess) target).ssca$anomalyState(current);
            ACTIVE.add(current);
        }
        processed++;
        var hit = current.ledger.hit(event.context.element(), event.context.amount(), event.tick);
        DIRTY.add(target);
        if (!hit.reachedMaximum()) return;
        // Commit consumption/history before effects can produce nested damage callbacks.
        current.ledger.clearElement(event.context.element());
        String previous = current.activate(event.context.element(), event.tick);
        triggers++;
        AnomalyEffects.activate(event.context.element(), target, event.context);
        if (!previous.isEmpty() && target.isAlive() && !target.isRemoved()) {
            reactions++;
            AnomalyEffects.react(previous, event.context.element(), target, event.context);
        }
    }

    private static void maintain(MinecraftServer server, long tick) {
        var iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            AnomalyTargetState current = iterator.next();
            if (current.world.getServer() != server) continue;
            LivingEntity target = current.target;
            if (!target.isAlive() || target.isRemoved() || target.getWorld() != current.world) {
                iterator.remove();
                detach(current);
                AnomalyEffects.clear(target);
                AnomalyNetwork.clear(target);
                continue;
            }
            if (current.ledger.expire(tick)) DIRTY.add(target);
            if (current.ledger.remainingTicks(tick) == 0 && current.linkRemaining(tick) == 0) {
                iterator.remove();
                detach(current);
                DIRTY.add(target);
                continue;
            }
        }
    }

    private static void synchronize(MinecraftServer server, long tick) {
        var iterator = DIRTY.iterator();
        while (iterator.hasNext()) {
            LivingEntity target = iterator.next();
            if (!(target.getWorld() instanceof ServerWorld world) || world.getServer() != server
                    || target.isRemoved() || !target.isAlive()) {
                iterator.remove();
                continue;
            }
            var access = (AnomalyTargetAccess) target;
            long lastSent = access.ssca$anomalyLastSent();
            if (lastSent == Long.MIN_VALUE || tick - lastSent >= SYNC_INTERVAL) {
                AnomalyNetwork.sync(target);
                access.ssca$anomalyLastSent(tick);
                iterator.remove();
            }
        }
    }

    public static void effectsChanged(LivingEntity target) {
        if (target.getWorld() instanceof ServerWorld && target.isAlive() && !target.isRemoved()) DIRTY.add(target);
    }

    public static AnomalyTargetState state(LivingEntity target) {
        return ((AnomalyTargetAccess) target).ssca$anomalyState();
    }

    public static AnomalySnapshot snapshot(LivingEntity target) {
        AnomalyTargetState current = state(target);
        long tick = target.getWorld() instanceof ServerWorld world ? now(world.getServer()) : 0;
        if (current == null || current.target != target || current.world != target.getWorld())
            return new AnomalySnapshot(AnomalyLedger.MAXIMUM, Map.of(), 0, "", 0, AnomalyEffects.effectSnapshot(target));
        var values = current.ledger.snapshot(tick);
        int link = current.linkRemaining(tick);
        return new AnomalySnapshot(values.maximum(), values.values(), values.remainingTicks(),
                link == 0 ? "" : current.lastElement, link, AnomalyEffects.effectSnapshot(target));
    }

    public static void clear(LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld)) return;
        ((AnomalyTargetAccess) target).ssca$invalidateAnomalyEpoch();
        AnomalyTargetState current = state(target);
        boolean hadState = current != null || AnomalyEffects.hasState(target);
        if (current != null) { ACTIVE.remove(current); detach(current); }
        AnomalyEffects.clear(target);
        DIRTY.remove(target);
        if (hadState) AnomalyNetwork.clear(target);
    }

    private static void detach(AnomalyTargetState current) {
        if (state(current.target) == current) ((AnomalyTargetAccess) current.target).ssca$anomalyState(null);
    }

    public static void clearAll() {
        for (AnomalyTargetState current : List.copyOf(ACTIVE)) clear(current.target);
        ACTIVE.clear();
        DIRTY.clear();
        while (!PENDING.isEmpty()) {
            var event = PENDING.removeFirst();
            if (event.context != null) {
                var access = (AnomalyTargetAccess) event.target;
                access.ssca$pendingAnomalyHits(Math.max(0, access.ssca$pendingAnomalyHits() - 1));
            }
        }
        FREE.clear();
        AnomalyEffects.clearAll();
        DIRTY.clear();
        enabled = true;
    }

    public static Metrics metrics() {
        return new Metrics(enqueued, processed, triggers, reactions, packets, localQueries, ACTIVE.size(), PENDING.size());
    }
    public static void resetMetrics() { enqueued = processed = triggers = reactions = packets = localQueries = 0; }
    public static void recordPacket() { packets++; }
    public static void recordLocalQuery() { localQueries++; }
}
