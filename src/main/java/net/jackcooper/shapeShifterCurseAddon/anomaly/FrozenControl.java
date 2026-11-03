package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.ControlledAbilityInterruption;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** A separate control effect: no changes to existing frost-freeze, cocoon or STUN semantics. */
public final class FrozenControl {
    public static final int DEFAULT_DURATION_TICKS = 100;
    public static final int SHATTER_SLOW_TICKS = 60;
    public record Pose(float yaw, float pitch) {}
    private record Entry(LivingEntity target, ServerWorld world) {}
    private static final Map<UUID, Pose> POSES = new HashMap<>();
    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();
    private static final java.util.Set<UUID> LOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean initialized;
    private FrozenControl() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Entry entry : java.util.List.copyOf(ACTIVE.values())) {
                LivingEntity target = entry.target();
                if (target.isRemoved() || target.getWorld() != entry.world() || !blocked(target)) clear(target);
                else hold(target);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((target, source) -> clear(target));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                Entry entry = ACTIVE.get(living.getUuid());
                // A loaded status marker has no live control owner; transient freeze never survives reload.
                if (entry == null || entry.target() != living) clear(living);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) clear(living);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clear(handler.player));
        PlayerSessionCleanup.register((handler, server) -> clear(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> { clear(old); clear(player); });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> clear(player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Entry entry : java.util.List.copyOf(ACTIVE.values())) clear(entry.target());
            ACTIVE.clear(); POSES.clear(); LOCKED.clear();
        });
    }
    /** Duration belongs to the caller; there is no anomaly threshold, immunity or default duration here. */
    public static boolean freeze(LivingEntity target, int durationTicks) {
        if (target == null || !(target.getWorld() instanceof ServerWorld) || !target.isAlive()
                || target.isSpectator() || durationTicks <= 0) return false;
        target.addStatusEffect(new StatusEffectInstance(SscAddon.FROZEN, durationTicks, 0, false, false, true));
        if (!blocked(target)) return false;
        applied(target);
        return true;
    }
    /** Also handles /effect and other legitimate sources that apply the registered status directly. */
    public static void applied(LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld world) || !blocked(target)) return;
        Entry previous = ACTIVE.get(target.getUuid());
        if (previous != null && previous.target() == target && previous.world() == world) {
            AnomalyManager.effectsChanged(target);
            return;
        }
        if (previous != null) clear(previous.target());
        ACTIVE.put(target.getUuid(), new Entry(target, world));
        capture(target);
        target.stopRiding();
        target.clearActiveItem();
        if (target instanceof MobEntity mob) mob.getNavigation().stop();
        if (target instanceof ServerPlayerEntity player) {
            player.closeHandledScreen();
            SpellChannelManager.onFrozen(player);
            ControlledAbilityInterruption.interrupt(player);
        }
        hold(target);
        AnomalyManager.effectsChanged(target);
    }
    public static void clear(LivingEntity target) {
        target.removeStatusEffect(SscAddon.FROZEN);
        release(target);
    }
    /** A successful later hit shatters the enclosure; the triggering buildup hit precedes freeze(). */
    public static void breakFromHit(LivingEntity target) {
        clear(target);
        if (target.isAlive() && !target.isRemoved())
            target.addStatusEffect(new StatusEffectInstance(SscAddon.FROZEN_RECOVERY, SHATTER_SLOW_TICKS, 0));
    }
    public static boolean blocked(LivingEntity entity) {
        return entity != null && entity.isAlive() && entity.hasStatusEffect(SscAddon.FROZEN);
    }
    public static void capture(LivingEntity entity) {
        POSES.put(entity.getUuid(), new Pose(entity.getYaw(), entity.getPitch()));
        LOCKED.add(entity.getUuid());
    }
    public static Pose pose(LivingEntity entity) { return POSES.get(entity.getUuid()); }
    public static PlayerMoveC2SPacket lockLook(LivingEntity entity,PlayerMoveC2SPacket packet) {
        Pose pose = pose(entity);
        return new PlayerMoveC2SPacket.Full(packet.getX(entity.getX()),packet.getY(entity.getY()),packet.getZ(entity.getZ()),
                pose == null ? entity.getYaw() : pose.yaw(),pose == null ? entity.getPitch() : pose.pitch(),packet.isOnGround());
    }
    public static boolean serverLocked(UUID uuid) { return LOCKED.contains(uuid); }
    public static void release(LivingEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld)) return;
        Entry entry = ACTIVE.get(entity.getUuid());
        if (entry != null && entry.target() != entity) return;
        ACTIVE.remove(entity.getUuid());
        boolean hadPose = POSES.remove(entity.getUuid()) != null;
        boolean hadLock = LOCKED.remove(entity.getUuid());
        if (entry != null || hadPose || hadLock) AnomalyManager.effectsChanged(entity);
    }
    public static void hold(LivingEntity entity) {
        Pose pose = POSES.get(entity.getUuid());
        if (pose == null) return;
        entity.setYaw(pose.yaw());
        entity.setPitch(pose.pitch());
        entity.setHeadYaw(pose.yaw());
        entity.setBodyYaw(pose.yaw());
        entity.forwardSpeed = 0;
        entity.sidewaysSpeed = 0;
        entity.clearActiveItem();
    }
}
