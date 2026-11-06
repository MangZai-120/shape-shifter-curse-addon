/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;

/** Parasitic absorption is owned and persisted by its target, including non-player entities. */
public final class ParasiticAbsorptionManager {
    public static final int MAX_DURATION = 300;
    private static final BalanceReader BAL = new BalanceReader("abilities.parasitic_seed_system");
    private ParasiticAbsorptionManager() { }

    public static void init() {
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) -> {
            if (handler.player instanceof ParasiticAbsorptionAccess access) {
                access.ssca$parasiticAbsorption().remove(handler.player);
                handler.player.removeStatusEffect(SscAddon.BAT_ABSORPTION);
            }
        });
    }

    public static void addAbsorption(LivingEntity entity, int amplifier) {
        addAbsorption(entity, amplifier, 1.0f);
    }

    public static void addAbsorption(LivingEntity entity, int amplifier, float durationFactor) {
        if (!(entity.getWorld() instanceof ServerWorld) || !(entity instanceof ParasiticAbsorptionAccess access)) return;
        int duration = Math.max(20, Math.round(BAL.i("absorption_max_duration", MAX_DURATION) * durationFactor));
        entity.addStatusEffect(new StatusEffectInstance(SscAddon.BAT_ABSORPTION, duration, amplifier, false, true, true));
        access.ssca$parasiticAbsorption().grant(entity, (amplifier + 1) * 2.0f);
        access.ssca$parasiticAbsorption().refresh(entity.getWorld().getTime() + duration);
    }
}
