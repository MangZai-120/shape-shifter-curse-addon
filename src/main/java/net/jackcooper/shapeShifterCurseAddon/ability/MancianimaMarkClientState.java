package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契灵标记客户端缓存。
 * 由 S2C 包 {@link MancianimaMarkManager#PACKET_MARK_SYNC} 同步：
 * 仅本地玩家作为标记者时，对应被标记目标的颜色（yellow/orange/red）。
 */
public final class MancianimaMarkClientState {
    private static final Map<UUID, String> COLORS = new ConcurrentHashMap<>();
    private MancianimaMarkClientState() {}

    /** 整体替换；空 map 等价于清空 */
    public static void update(Map<UUID, String> snapshot) {
        COLORS.clear();
        if (snapshot != null && !snapshot.isEmpty()) COLORS.putAll(snapshot);
    }

    public static void clear() { COLORS.clear(); }

    /** 客户端 BiEntity 条件查询入口 */
    public static boolean hasColor(UUID targetUuid, String color) {
        if (targetUuid == null || color == null) return false;
        String c = COLORS.get(targetUuid);
        return color.equals(c);
    }

    /** 调试用，禁止外部修改返回值 */
    public static Map<UUID, String> snapshot() { return new HashMap<>(COLORS); }
}
