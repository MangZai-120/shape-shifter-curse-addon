/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

/**
 * Trinkets 饰品检测工具：判断实体是否正戴着某饰品。
 * <p>异常安全——非玩家、Trinkets 未就绪或查询异常时一律返回 false，可在服务端 tick 里放心调用。
 */
public final class TrinketUtils {
    private TrinketUtils() {
    }

    /** 该实体（玩家）是否正装备着指定饰品物品。 */
    public static boolean isWearing(LivingEntity entity, Item item) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }
        try {
            return TrinketsApi.getTrinketComponent(player)
                    .map(c -> c.isEquipped(item))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
