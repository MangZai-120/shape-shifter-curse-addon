package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.LaserBeamEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荧光幼灵主要技能「法阵激光」管理器。
 *
 * <p>按 sp_primary：形态正确 + 不在 CD + 当前没有活跃激光实体 → 生成 {@link LaserBeamEntity}
 * （其自驱蓄力/释放/消退状态机 + 定身 + 伤害 + CD）。防止同一玩家重复触发。
 */
public final class FluorescentLaserManager {

	private static final Map<UUID, LaserBeamEntity> ACTIVE = new ConcurrentHashMap<>();

	private FluorescentLaserManager() {
	}

	/** 客户端「按下主要技能键」时调用。 */
	public static void onKeyPress(ServerPlayerEntity player) {
		if (!FormUtils.isAxolotlFluorescent(player)) return;
		// CD 中不可用
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return;
		// 已有活跃激光 → 忽略
		LaserBeamEntity existing = ACTIVE.get(player.getUuid());
		if (existing != null && existing.isAlive()) return;
		if (!(player.getWorld() instanceof ServerWorld sw)) return;
		LaserBeamEntity laser = new LaserBeamEntity(sw, player);
		sw.spawnEntity(laser);
		ACTIVE.put(player.getUuid(), laser);
	}

	public static void onPlayerDisconnect(UUID uuid) {
		LaserBeamEntity e = ACTIVE.remove(uuid);
		if (e != null) e.discard();
	}

	public static void clearAll() {
		for (LaserBeamEntity e : ACTIVE.values()) e.discard();
		ACTIVE.clear();
	}
}
