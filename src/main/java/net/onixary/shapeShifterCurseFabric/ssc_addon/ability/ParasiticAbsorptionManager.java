/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寄生果蝠主技能专属「伤害吸收」管理器。
 * <p>
 * 触发时按药水等级累加黄心（(amp+1) 颗 = (amp+1)*2 absorption HP），并把持续刷新到 15s；
 * 重复触发在原有黄心基础上继续累加并刷新持续；到期清除本机制提供的 absorption。
 * 服务端权威，多人一致。
 */
public final class ParasiticAbsorptionManager {
    /** 最多持续 15s（300t），每次触发刷新到此 */
    public static final int MAX_DURATION = 300;

    private static final Map<UUID, AbsorptionData> DATA = new ConcurrentHashMap<>();

    private ParasiticAbsorptionManager() {
    }

    private static final class AbsorptionData {
        float granted;
        long endTick;
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ParasiticAbsorptionManager::onWorldTick);
        // 玩家断开连接时清除本机制提供的 absorption（黄心由 setAbsorptionAmount 写入会被存档保存，
        // 而内存 DATA 在断连后丢失，若不清会导致重连后黄心永久残留）。
        // DISCONNECT 在 Netty IO 线程触发，setAbsorptionAmount 改实体属性需调度回主线程。
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            net.minecraft.server.network.ServerPlayerEntity player = handler.player;
            if (player == null) return;
            java.util.UUID uuid = player.getUuid();
            AbsorptionData d = DATA.remove(uuid);
            if (d == null) return;
            float granted = d.granted;
            server.execute(() -> {
                if (player.isAlive()) {
                    player.setAbsorptionAmount(Math.max(0.0f, player.getAbsorptionAmount() - granted));
                }
            });
        });
    }

    /** 触发吸收：累加 (amp+1) 颗黄心并把持续刷新到 15s，同时刷新显示图标。 */
    public static void addAbsorption(LivingEntity entity, int amplifier) {
        if (!(entity.getWorld() instanceof ServerWorld world)) return;
        long now = world.getTime();
        float add = (amplifier + 1) * 2.0f;
        entity.setAbsorptionAmount(entity.getAbsorptionAmount() + add);
        AbsorptionData d = DATA.computeIfAbsent(entity.getUuid(), k -> new AbsorptionData());
        d.granted += add;
        d.endTick = now + MAX_DURATION;
        // 纯显示图标（时长 = 15s，到期由本管理器清 absorption）
        entity.addStatusEffect(new StatusEffectInstance(SscAddon.BAT_ABSORPTION, MAX_DURATION, amplifier, false, true, true));
    }

    private static void onWorldTick(ServerWorld world) {
        if (DATA.isEmpty()) return;
        long now = world.getTime();
        Iterator<Map.Entry<UUID, AbsorptionData>> it = DATA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AbsorptionData> e = it.next();
            AbsorptionData d = e.getValue();
            if (now < d.endTick) continue;
            // 到期：在目标所在世界清除本机制提供的 absorption
            Entity entity = world.getEntity(e.getKey());
            if (entity instanceof LivingEntity living) {
                living.setAbsorptionAmount(Math.max(0.0f, living.getAbsorptionAmount() - d.granted));
                it.remove();
            }
            // 本世界找不到目标（跨维度/离线）：保留，等其所在世界 tick 时清除
        }
    }
}
