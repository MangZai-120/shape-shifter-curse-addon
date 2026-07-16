package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端 HUD 资源值的每 tick 缓存。
 * <p>原本每个能量条每帧都遍历玩家全部 {@link VariableIntPower} 找对应资源，
 * 8 个条 = 每帧 8 次遍历；这里按游戏 tick 缓存一次「资源 id → [value, max]」的 map，
 * 同 tick 内所有条 O(1) 查表，tick 变化或玩家变化才重建。
 * 资源值是 tick 级（渲染插值不改变），按 tick 缓存不影响正确性。
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceCache {
    private static PlayerEntity cachedPlayer;
    private static long cachedTick = Long.MIN_VALUE;
    private static final Map<Identifier, int[]> CACHE = new HashMap<>();
    private static final int[] ABSENT = {0, 1};

    private ClientResourceCache() {
    }

    private static void rebuild(PlayerEntity player, long tick) {
        cachedPlayer = player;
        cachedTick = tick;
        CACHE.clear();
        try {
            for (VariableIntPower p : PowerHolderComponent.KEY.get(player).getPowers(VariableIntPower.class)) {
                CACHE.put(p.getType().getIdentifier(), new int[]{p.getValue(), p.getMax()});
            }
        } catch (Exception ignored) {
            // 组件暂不可用时留空 map，查询返回默认
        }
    }

    private static void ensureFresh(PlayerEntity player) {
        long tick = player.getWorld() != null ? player.getWorld().getTime() : cachedTick;
        if (player != cachedPlayer || tick != cachedTick) {
            rebuild(player, tick);
        }
    }

    /** 该玩家是否持有指定 VariableIntPower（走每 tick 缓存）。 */
    public static boolean has(PlayerEntity player, Identifier resourceId) {
        ensureFresh(player);
        return CACHE.containsKey(resourceId);
    }

    /** 取 {@code [value, max]}；不存在返回 {@code {0, 1}}（沿用旧语义）。 */
    public static int[] getValueAndMax(PlayerEntity player, Identifier resourceId) {
        ensureFresh(player);
        int[] v = CACHE.get(resourceId);
        return v != null ? v : ABSENT;
    }

    public static int getValue(PlayerEntity player, Identifier resourceId) {
        return getValueAndMax(player, resourceId)[0];
    }
}
