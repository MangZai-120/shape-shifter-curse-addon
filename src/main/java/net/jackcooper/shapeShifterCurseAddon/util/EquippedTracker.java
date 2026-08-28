/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端饰品装备状态追踪器（每饰品一个静态实例）。
 *
 * <p>统一「accessoryTick/onEquip 打点 + onUnequip/断线移除 + 3 tick 容错判定」的装备检测模式，
 * 替代各饰品物品类内各自维护的 EQUIPPED_PLAYERS 六件套。基于 tick 回调追踪，
 * 比 isEquipped API 更可靠（跨框架一致）。</p>
 *
 * <p>用法：物品类持有 {@code private static final EquippedTracker TRACKER = new EquippedTracker();}
 * 在 accessoryTick / onEquip 调 {@link #markEquipped}，onUnequip / clearPlayer 调
 * {@link #remove}，检测处调 {@link #isEquippedBy}。</p>
 */
public final class EquippedTracker {
    /** 玩家UUID -> 最后一次tick的游戏时间 */
    private final ConcurrentHashMap<UUID, Long> equippedPlayers = new ConcurrentHashMap<>();
    /** 超过该 tick 数未更新视为已卸下（容错） */
    private final long toleranceTicks;

    public EquippedTracker() {
        this(3L);
    }

    public EquippedTracker(long toleranceTicks) {
        this.toleranceTicks = toleranceTicks;
    }

    /** accessoryTick / onEquip 时打点。 */
    public void markEquipped(LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            equippedPlayers.put(player.getUuid(), entity.getWorld().getTime());
        }
    }

    /** onUnequip / 玩家断线时移除。 */
    public void remove(UUID uuid) {
        equippedPlayers.remove(uuid);
    }

    /** 检查玩家是否装备着该饰品（超过容错 tick 未更新视为已卸下）。 */
    public boolean isEquippedBy(ServerPlayerEntity player) {
        Long lastTick = equippedPlayers.get(player.getUuid());
        if (lastTick == null) return false;
        return Math.abs(player.getWorld().getTime() - lastTick) <= toleranceTicks;
    }
}
