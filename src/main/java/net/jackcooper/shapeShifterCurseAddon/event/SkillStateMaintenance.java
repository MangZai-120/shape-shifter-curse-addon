package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Lifecycle cleanup for transient skill data. Unexpired quotas and persistent resources are retained. */
public final class SkillStateMaintenance {
    private SkillStateMaintenance() {}

    public static void init() {
        PlayerSessionCleanup.register((handler, server) -> {
            BatDesmodusBloodThirst.clearPlayer(handler.player.getUuid());
            ParasiticCombatTracker.clearPlayer(handler.player.getUuid());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            MancianimaAggroTracker.forget(entity.getUuid());
            if (entity instanceof ServerPlayerEntity player) {
                ControlledAbilityInterruption.cancelTransient(player);
                AnubisWolfSpDeathDomain.clearPlayer(player);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
                MancianimaAggroTracker.forget(entity.getUuid()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) {
                AnubisWolfSpDeathDomain.removeLegacyHealthReduction(living);
                CompanionResonanceManager.removeLegacyModifier(living);
            }
            if ((entity.getCommandTags().contains("ssca_ghost_creeper")
                    || entity.getCommandTags().contains("ssca_ghost_cat"))
                    && !NightmareSpookManager.ownsGhost(entity.getUuid())) entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0 || server.getOverworld() == null) return;
            long now = server.getOverworld().getTime();
            SeedEnergyEatingHandler.pruneAll(now);
            AllaySPRangedHitPassive.prune(now);
            AnubisWolfSpSoulEnergy.pruneSources(now);
            GoldenSandstormRegen.pruneSources(now);
            NightmareFearManager.pruneImmunity(now);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SeedEnergyEatingHandler.clearAll();
            ParasiticCombatTracker.clearAll();
            AllaySPRangedHitPassive.clearAll();
            AnubisWolfSpSoulEnergy.clearSources();
            NightmareFearManager.clearAll();
            MancianimaAggroTracker.clearAll();
            WindSpiritWindPressureManager.clearAll();
            CompanionResonanceManager.clearAll();
            PlayDeadAbsorptionManager.clearAll();
        });
    }
}
