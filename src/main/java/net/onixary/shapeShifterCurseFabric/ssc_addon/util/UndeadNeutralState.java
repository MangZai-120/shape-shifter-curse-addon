package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP阿努比斯狼亡灵中立机制的共享状态。
 * 记录玩家挑衅亡灵的全局时间戳，供多个Mixin访问。
 */
public final class UndeadNeutralState {

	/**
	 * 失去视野后的脱战计时：30秒
	 */
	public static final long SIGHT_TIMEOUT = 600L;

	/**
	 * 全局挑衅时间戳：玩家UUID -> 最后挑衅的世界时间
	 * 使用ConcurrentHashMap保证多人环境下的线程安全
	 */
	public static final Map<UUID, Long> PROVOKE_TIMESTAMPS = new ConcurrentHashMap<>();

	private UndeadNeutralState() {
	}

	/**
	 * 检查玩家是否处于挑衅状态
	 */
	public static boolean isPlayerProvoked(UUID playerUuid, long worldTime) {
		Long provokeTime = PROVOKE_TIMESTAMPS.get(playerUuid);
		if (provokeTime == null) return false;
		if (worldTime - provokeTime > SIGHT_TIMEOUT) {
			PROVOKE_TIMESTAMPS.remove(playerUuid);
			return false;
		}
		return true;
	}
	/**
	 * 玩家断线时清理状态，防止内存泄漏
	 */
	public static void clearPlayer(UUID playerUuid) {
		PROVOKE_TIMESTAMPS.remove(playerUuid);
	}

	/**
	 * 服务器关闭时清理所有状态
	 */
	public static void clearAll() {
		PROVOKE_TIMESTAMPS.clear();
	}}
