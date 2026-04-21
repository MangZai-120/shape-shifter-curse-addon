package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侵蚀烙印客户端状态缓存
 * 存储由服务端通过网络包同步的本地玩家的烙印数据，
 * 用于 entity_glow 的 BiEntity 条件在客户端侧的评估。
 * 这使得发光效果仅对拥有烙印的玩家自己可见。
 * <p>
 * 注意：本类不引用任何 Minecraft 客户端类，可安全存在于两端。
 */
public class ErosionBrandClientState {

	/** 目标UUID → 颜色字符串 (yellow/orange/red/green) */
	private static final Map<UUID, String> BRAND_COLORS = new ConcurrentHashMap<>();

	/**
	 * 替换全部状态（由 S2C 网络包回调调用）
	 */
	public static void update(Map<UUID, String> brandData) {
		BRAND_COLORS.clear();
		BRAND_COLORS.putAll(brandData);
	}

	/**
	 * 检查指定目标是否拥有指定颜色的烙印
	 */
	public static boolean hasColor(UUID targetUuid, String color) {
		return color.equals(BRAND_COLORS.get(targetUuid));
	}

	/**
	 * 清除所有缓存（断线/切换形态时调用）
	 */
	public static void clear() {
		BRAND_COLORS.clear();
	}
}
