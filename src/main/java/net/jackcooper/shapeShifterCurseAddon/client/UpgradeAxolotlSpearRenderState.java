package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「投掷水矛」蓄力期的客户端渲染状态。
 *
 * <p>服务端在玩家起跃/定身蓄力时通过 S2C 广播（对追踪者 + 自身），客户端据此维护
 * 一份「正在蓄力的玩家 UUID 集合」。手持水矛渲染 mixin 与举矛姿势 mixin 查询本集合，
 * 对本地玩家（第一人称）与远程玩家（第三人称）都可靠、O(1)。</p>
 */
@Environment(EnvType.CLIENT)
public final class UpgradeAxolotlSpearRenderState {
	private static final Set<UUID> CHARGING = ConcurrentHashMap.newKeySet();

	private UpgradeAxolotlSpearRenderState() {
	}

	public static void set(UUID id, boolean charging) {
		if (id == null) return;
		if (charging) {
			CHARGING.add(id);
		} else {
			CHARGING.remove(id);
		}
	}

	public static boolean isCharging(UUID id) {
		return id != null && CHARGING.contains(id);
	}

	/** 退出世界 / 断开连接时清空，避免残留。 */
	public static void clear() {
		CHARGING.clear();
	}
}
