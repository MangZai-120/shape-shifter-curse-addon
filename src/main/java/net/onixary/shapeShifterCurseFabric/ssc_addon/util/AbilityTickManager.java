package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityTickManager {
    private AbilityTickManager() {
    }

    private static final Set<ServerPlayerEntity> ACTIVE_DASH_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<ServerPlayerEntity> ACTIVE_TELEPORT_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<ServerPlayerEntity> ACTIVE_FROST_STORM_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<ServerPlayerEntity> ACTIVE_GROUP_HEAL_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<ServerPlayerEntity> ACTIVE_JUKEBOX_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void registerDashingPlayer(ServerPlayerEntity player) {
        ACTIVE_DASH_PLAYERS.add(player);
    }

    public static void unregisterDashingPlayer(ServerPlayerEntity player) {
        ACTIVE_DASH_PLAYERS.remove(player);
    }

    public static void registerTeleportingPlayer(ServerPlayerEntity player) {
        ACTIVE_TELEPORT_PLAYERS.add(player);
    }

    public static void unregisterTeleportingPlayer(ServerPlayerEntity player) {
        ACTIVE_TELEPORT_PLAYERS.remove(player);
    }

    public static void registerFrostStormPlayer(ServerPlayerEntity player) {
        ACTIVE_FROST_STORM_PLAYERS.add(player);
    }

    public static void unregisterFrostStormPlayer(ServerPlayerEntity player) {
        ACTIVE_FROST_STORM_PLAYERS.remove(player);
    }

    public static void registerGroupHealPlayer(ServerPlayerEntity player) {
        ACTIVE_GROUP_HEAL_PLAYERS.add(player);
    }

    public static void unregisterGroupHealPlayer(ServerPlayerEntity player) {
        ACTIVE_GROUP_HEAL_PLAYERS.remove(player);
    }

    public static void registerJukeboxPlayer(ServerPlayerEntity player) {
        ACTIVE_JUKEBOX_PLAYERS.add(player);
    }

    public static void unregisterJukeboxPlayer(ServerPlayerEntity player) {
        ACTIVE_JUKEBOX_PLAYERS.remove(player);
    }

    public static void unregisterAll(ServerPlayerEntity player) {
        ACTIVE_DASH_PLAYERS.remove(player);
        ACTIVE_TELEPORT_PLAYERS.remove(player);
        ACTIVE_FROST_STORM_PLAYERS.remove(player);
        ACTIVE_GROUP_HEAL_PLAYERS.remove(player);
        ACTIVE_JUKEBOX_PLAYERS.remove(player);
    }

    public static boolean hasActiveAbility(ServerPlayerEntity player) {
        return ACTIVE_DASH_PLAYERS.contains(player) ||
               ACTIVE_TELEPORT_PLAYERS.contains(player) ||
               ACTIVE_FROST_STORM_PLAYERS.contains(player) ||
               ACTIVE_GROUP_HEAL_PLAYERS.contains(player) ||
               ACTIVE_JUKEBOX_PLAYERS.contains(player);
    }

    public static void tickAll(net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility meleeAbility,
                               net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack teleportAbility,
                               net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm frostStormAbility,
                               net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal groupHealAbility,
                               net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox jukeboxAbility) {
        ACTIVE_DASH_PLAYERS.forEach(player -> {
            try { meleeAbility.tick(player); } catch (Exception e) {}
        });
        ACTIVE_TELEPORT_PLAYERS.forEach(player -> {
            try { teleportAbility.tick(player); } catch (Exception e) {}
        });
        ACTIVE_FROST_STORM_PLAYERS.forEach(player -> {
            try { frostStormAbility.tick(player); } catch (Exception e) {}
        });
        ACTIVE_GROUP_HEAL_PLAYERS.forEach(player -> {
            try { groupHealAbility.tick(player); } catch (Exception e) {}
        });
        ACTIVE_JUKEBOX_PLAYERS.forEach(player -> {
            try { jukeboxAbility.tick(player); } catch (Exception e) {}
        });
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        unregisterAll(player);
    }
}
