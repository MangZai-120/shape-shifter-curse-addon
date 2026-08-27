package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契灵激怒追踪器（服务端内存，进程内）：
 * - 当契灵主动攻击某 mob 时，记录 (mobUUID -> 激怒它的玩家 UUID)
 * - mob 仅在被激怒后才能将契灵设为攻击目标（坚守者/铁傀儡 不受此限制）
 * - mob 自然丢失目标（vanilla setTarget(null)）时移除条目，等同"逃离重置"
 */
public final class MancianimaAggroTracker {
	private MancianimaAggroTracker() {}

	private static final Map<UUID, UUID> ANGERED = new ConcurrentHashMap<>();

	public static void provoke(UUID mobUuid, UUID playerUuid) {
		if (mobUuid == null || playerUuid == null) return;
		ANGERED.put(mobUuid, playerUuid);
	}

	public static boolean isAngered(UUID mobUuid, UUID playerUuid) {
		if (mobUuid == null || playerUuid == null) return false;
		UUID p = ANGERED.get(mobUuid);
		return p != null && p.equals(playerUuid);
	}

	public static void forget(UUID mobUuid) {
		if (mobUuid == null) return;
		ANGERED.remove(mobUuid);
	}
}
