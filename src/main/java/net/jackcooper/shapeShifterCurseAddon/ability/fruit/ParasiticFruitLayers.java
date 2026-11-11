package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.InfectionSporeManager;
import net.jackcooper.shapeShifterCurseAddon.power.ParasiticFruitSeedPower;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** World-scoped shared host layers. Each fruit kind pulses once, support never multiplies healing. */
public final class ParasiticFruitLayers {
    private static final class Host {
        final LivingEntity entity;
        final Map<String, Long> nextPulse = new HashMap<>();
        boolean enemy;
        Host(LivingEntity entity) { this.entity = entity; }
    }
    private static final class WorldState {
        final FruitLayerLedger ledger = new FruitLayerLedger();
        final Map<UUID, Host> hosts = new LinkedHashMap<>();
    }
    private record Group(ServerPlayerEntity caster, FruitSelection fruit, int count, boolean friend) { }
    private static final Map<ServerWorld, WorldState> WORLDS = new IdentityHashMap<>();
    private ParasiticFruitLayers() { }
    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ParasiticFruitLayers::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var state : WORLDS.values()) for (var host : state.hosts.values()) cleanup(host.entity);
            WORLDS.clear();
        });
    }
    public static int plant(ServerPlayerEntity caster, LivingEntity host, FruitSelection fruit,
                            int life, int rooting, int maxHosts) {
        var world = caster.getServerWorld();
        var state = WORLDS.computeIfAbsent(world, ignored -> new WorldState());
        state.hosts.computeIfAbsent(host.getUuid(), ignored -> new Host(host));
        Set<UUID> changed = state.ledger.add(host.getUuid(), caster.getUuid(), fruit,
                world.getTime(), life, rooting, maxHosts);
        for (UUID uuid : changed) reconcile(world, state, uuid, false);
        return state.ledger.layers(host.getUuid()).size();
    }
    public static void removeOwner(ServerPlayerEntity caster) {
        for (var entry : WORLDS.entrySet()) {
            for (UUID uuid : entry.getValue().ledger.removeOwner(caster.getUuid()))
                reconcile(entry.getKey(), entry.getValue(), uuid, false);
        }
    }
    public static boolean isEnemyParasitized(UUID host, long now) {
        for (var state : WORLDS.values()) {
            Host value = state.hosts.get(host);
            if (value != null && value.enemy && state.ledger.layers(host).stream().anyMatch(l -> l.expiresAt() > now)) return true;
        }
        return false;
    }
    public static java.util.List<FruitLayerLedger.Layer> layers(ServerWorld world, UUID host) {
        var state = WORLDS.get(world); return state == null ? List.of() : state.ledger.layers(host);
    }
    private static boolean validOwner(ServerWorld world, FruitLayerLedger.Layer layer) {
        var caster = world.getServer().getPlayerManager().getPlayer(layer.owner());
        return caster != null && caster.isAlive() && caster.getWorld() == world
                && FormUtils.isForm(caster, FormIdentifiers.BAT_PARASITIC_FRUIT)
                && ParasiticFruitSeedPower.get(caster) != null;
    }
    public static void tick(ServerWorld world) {
        var state = WORLDS.get(world); if (state == null) return;
        state.ledger.prune(world.getTime(), layer -> validOwner(world, layer));
        for (UUID uuid : List.copyOf(state.hosts.keySet())) reconcile(world, state, uuid, true);
        if (state.hosts.isEmpty()) WORLDS.remove(world);
    }
    private static void reconcile(ServerWorld world, WorldState state, UUID uuid, boolean pulse) {
        Host runtime = state.hosts.get(uuid); if (runtime == null) return;
        LivingEntity host = runtime.entity;
        var layers = state.ledger.layers(uuid);
        if (layers.isEmpty() || !host.isAlive() || host.isRemoved() || host.getWorld() != world) {
            cleanup(host); state.ledger.removeHost(uuid); state.hosts.remove(uuid); return;
        }
        long now = world.getTime();
        Map<String, Group> groups = new LinkedHashMap<>();
        ServerPlayerEntity visualOwner = null;
        runtime.enemy = false;
        for (var layer : layers) {
            var caster = world.getServer().getPlayerManager().getPlayer(layer.owner());
            if (caster == null || caster.getWorld() != world) continue;
            boolean friend = WhitelistUtils.isBuffTarget(caster, host);
            if ((!friend && WhitelistUtils.isProtected(caster, host)) || DomainManager.blocksTargeting(caster, host)) continue;
            if (visualOwner == null) visualOwner = caster;
            if (!friend) runtime.enemy = true;
            if (!layer.rooted(now)) continue;
            String key = friend ? "support" : layer.fruit().key();
            Group old = groups.get(key);
            groups.put(key, new Group(old == null ? caster : old.caster(), layer.fruit(),
                    old == null ? 1 : old.count() + 1, friend));
        }
        Map<String, Integer> active = new HashMap<>();
        groups.forEach((key, value) -> active.put(key, value.count()));
        if (visualOwner != null && layers.size() == 3 && !InfectionSporeManager.isInfected(uuid)) active.put("outline", 3);
        FruitStatusOverlay.update(host, active);
        runtime.nextPulse.keySet().retainAll(groups.keySet());
        if (pulse) {
            for (var entry : groups.entrySet()) {
                String key = entry.getKey(); Group group = entry.getValue();
                if (now < runtime.nextPulse.getOrDefault(key, Long.MIN_VALUE)) continue;
                var power = ParasiticFruitSeedPower.get(group.caster()); if (power == null) continue;
                runtime.nextPulse.put(key, now + power.fruitInterval());
                DomainManager.runAttachedEffect(group.caster(), host, () -> FruitStatusOverlay.pulse(host, key,
                        group.count(), () -> power.harvest(group.caster(), host, group.fruit(), group.count(), group.friend())));
            }
        }
        if (visualOwner != null) {
            var caster = visualOwner; var power = ParasiticFruitSeedPower.get(caster);
            if (power != null) DomainManager.runAttachedEffect(caster, host, () -> power.drawAttached(caster, host, layers.size()));
        } else GlowMarker.unmark(host);
    }
    private static void cleanup(LivingEntity host) { FruitStatusOverlay.clear(host); GlowMarker.unmark(host); }
}
