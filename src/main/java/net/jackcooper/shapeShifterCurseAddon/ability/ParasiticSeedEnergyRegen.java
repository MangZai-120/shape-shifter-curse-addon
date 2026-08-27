/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寄生果蝠种子量能量回复管理器。
 * <p>
 * 按交战状态决定回复间隔：未交战每 5s（100t）回 1 点，交战每 8s（160t）回 1 点；
 * 满 10 点时不再回复。交战判定与种子寿命一致（{@link ParasiticCombatTracker}）。
 * 服务端权威，替代原 JSON action_over_time（其无法判定交战）。
 */
public final class ParasiticSeedEnergyRegen {
    /** 未交战回复间隔（tick）：5s */
    private static final int INTERVAL_PEACE = 100;
    /** 交战回复间隔（tick）：8s */
    private static final int INTERVAL_COMBAT = 160;
    /** 能量上限 */
    private static final int MAX_ENERGY = 10;

    /** 玩家 UUID -> 距上次回复已累计 tick 数 */
    private static final Map<UUID, Integer> ACCUM = new ConcurrentHashMap<>();

    private ParasiticSeedEnergyRegen() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        // 仅果蝠形态持有种子量能量资源；非该形态直接清理累计并跳过
        if (!PowerUtils.hasResource(player, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, 0)) {
            ACCUM.remove(player.getUuid());
            return;
        }
        int current = PowerUtils.getResourceValue(player, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
        if (current >= MAX_ENERGY) {
            ACCUM.put(player.getUuid(), 0);
            return;
        }
        int interval = ParasiticCombatTracker.isInCombat(player) ? INTERVAL_COMBAT : INTERVAL_PEACE;
        int acc = ACCUM.getOrDefault(player.getUuid(), 0) + 1;
        if (acc >= interval) {
            PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, 1);
            acc = 0;
        }
        ACCUM.put(player.getUuid(), acc);
    }
}
