/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.power.SeedEnergyResourcePower;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
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
    // 以下均为默认值；运行时从 balance 快照读取（abilities.parasitic_seed_system）
    /** 未交战回复间隔（tick）：5s */
    private static final int INTERVAL_PEACE = 100;
    /** 交战回复间隔（tick）：8s */
    private static final int INTERVAL_COMBAT = 160;

    /** balance 快照读取（快照未初始化回退默认常量） */
    private static final BalanceReader BAL = new BalanceReader("abilities.parasitic_seed_system");

    /** 仅保存已有进度的玩家；没有条目即累计 0 tick。 */
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
        UUID id = player.getUuid();
        var energy = FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY;
        // 缺少 power 时读取值也会返回 0，先确认持有，才能开始计时。
        if (!FormUtils.isBatParasiticFruit(player) || !ResourceBars.hasPowerId(player, energy)
                || PowerUtils.getResourceValue(player, energy) >= SeedEnergyResourcePower.CAPACITY) {
            if (ACCUM.containsKey(id)) ACCUM.remove(id);
            return;
        }
        int interval = ParasiticCombatTracker.isInCombat(player)
                ? BAL.i("regen_interval_combat", INTERVAL_COMBAT)
                : BAL.i("regen_interval_peace", INTERVAL_PEACE);
        int acc = ACCUM.getOrDefault(id, 0) + 1;
        if (acc >= interval) {
            PowerUtils.changeResourceValueAndSync(player, energy, 1);
            ACCUM.remove(id);
        } else {
            ACCUM.put(id, acc);
        }
    }
}
