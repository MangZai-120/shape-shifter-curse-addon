package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.additional_power.ManaTypePower;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 兜底机制：玩家加入服务器时清理 ManaComponent 中孤儿的 (manaType, manaSource) 条目。
 *
 * <p>背景：主包 {@code ManaComponent.ManaTypeSourceMap} 会持久化"哪个 power 提供了哪种 mana 类型"。
 * 若老存档曾持有某 {@link ManaTypePower}（如 form_spider 提供的 web_resource），
 * 但当前玩家身上该 power 已不存在，残留条目会导致 mana 能量条始终显示。
 *
 * <p>本兜底逻辑只在玩家加入服务器时跑一次，仅移除"当前确实没有对应活跃 power"的条目，不影响合法数据。
 *
 * <p>因为主包 {@link ManaTypePower} 的 {@code manaType} / {@code manaSource} 字段是 private 且
 * 未暴露 getter，这里通过反射读取（一次性，结果缓存）。若后续主包暴露 getter，可直接替换。
 */
public final class StaleManaCleaner {

    private static final Field MANA_TYPE_FIELD;
    private static final Field MANA_SOURCE_FIELD;

    static {
        Field type = null;
        Field source = null;
        try {
            type = ManaTypePower.class.getDeclaredField("manaType");
            type.setAccessible(true);
            source = ManaTypePower.class.getDeclaredField("manaSource");
            source.setAccessible(true);
        } catch (NoSuchFieldException e) {
            ShapeShifterCurseFabric.LOGGER.error(
                    "[ssc_addon] StaleManaCleaner 反射初始化失败：ManaTypePower 字段不存在，兜底将不会生效",
                    e);
        }
        MANA_TYPE_FIELD = type;
        MANA_SOURCE_FIELD = source;
    }

    private StaleManaCleaner() {
    }

    public static void register() {
        if (MANA_TYPE_FIELD == null || MANA_SOURCE_FIELD == null) {
            return;
        }
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // 延迟 2 tick 调度，确保 Apoli 已完成 power 反序列化与 onAdded 回放
            server.execute(() -> server.execute(() -> {
                try {
                    cleanup(player);
                } catch (Throwable t) {
                    ShapeShifterCurseFabric.LOGGER.warn(
                            "[ssc_addon] StaleManaCleaner 在玩家 {} 上执行失败",
                            player.getName().getString(), t);
                }
            }));
        });
    }

    private static void cleanup(ServerPlayerEntity player) {
        ManaComponent mana = ManaUtils.getManaComponent(player);
        if (mana == null || mana.ManaTypeSourceMap.isEmpty()) {
            return;
        }

        // 收集当前活跃的 (manaType, manaSource) 组合
        Set<String> activePairs = new HashSet<>();
        for (ManaTypePower power : PowerHolderComponent.getPowers(player, ManaTypePower.class)) {
            Identifier mt;
            Identifier ms;
            try {
                mt = (Identifier) MANA_TYPE_FIELD.get(power);
                ms = (Identifier) MANA_SOURCE_FIELD.get(power);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (mt != null && ms != null) {
                activePairs.add(mt + "|" + ms);
            }
        }

        // 收集孤儿条目（与 Map 解耦避免并发修改）
        List<Pair<Identifier, Identifier>> orphans = new ArrayList<>();
        mana.ManaTypeSourceMap.forEach((typeId, sources) -> {
            if (sources == null) return;
            for (Identifier source : sources) {
                if (source == null) continue;
                if (!activePairs.contains(typeId + "|" + source)) {
                    orphans.add(new Pair<>(typeId, source));
                }
            }
        });

        if (orphans.isEmpty()) {
            return;
        }

        for (Pair<Identifier, Identifier> orphan : orphans) {
            mana.loseManaTypeID(orphan.getLeft(), orphan.getRight());
        }
        ShapeShifterCurseFabric.LOGGER.info(
                "[ssc_addon] 已清理 {} 个孤儿 mana 条目 (玩家: {})",
                orphans.size(), player.getName().getString());
    }
}
