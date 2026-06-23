package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装死回血给予的「黄心」存留 + 衰减管理。
 * 规则（用户 2026-06-23 确认）：
 * - 仅作用于「装死给的黄心」这部分预算，其它来源（如金苹果）的黄心不动。
 * - 计时从「装死结束」那一刻起算，存留 30 秒。
 * - 30 秒后开始衰减：每秒减 1 颗心（= 2 点吸收 HP），直接用 setAbsorptionAmount 扣，无受击特效。
 */
public class PlayDeadAbsorptionManager {
	// 「装死黄心」预算（只有这部分会在 30s 后衰减）
	private static final Map<UUID, Float> BUDGET = new ConcurrentHashMap<>();
	// 衰减开始的服务端 tick（= 装死结束那刻 + 30s）
	private static final Map<UUID, Long> DECAY_START = new ConcurrentHashMap<>();

	private static final int RETAIN_TICKS = 600;      // 30 秒存留
	private static final int DECAY_INTERVAL = 20;     // 每秒衰减一次
	private static final float DECAY_PER_SEC = 2.0f;  // 每次减 1 颗心 = 2 HP

	private PlayDeadAbsorptionManager() {
	}

	/** 装死期间每次给黄心时调用，累加「装死黄心」预算；并把衰减计时清零（结束后再重新计 30s）。 */
	public static void addAbsorption(PlayerEntity player, float delta) {
		if (delta <= 0f) {
			return;
		}
		UUID id = player.getUuid();
		BUDGET.merge(id, delta, Float::sum);
		DECAY_START.remove(id);
	}

	public static void tick(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		Float budgetObj = BUDGET.get(id);
		if (budgetObj == null) {
			return;
		}

		// 预算不能超过实际黄心（被打掉/死亡会减少实际黄心）
		float actual = player.getAbsorptionAmount();
		float budget = Math.min(budgetObj, actual);
		if (budget <= 0f) {
			BUDGET.remove(id);
			DECAY_START.remove(id);
			return;
		}

		// 仍在装死 → 还在累积，不衰减；计时起点留到结束后再设
		if (player.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
			BUDGET.put(id, budget);
			DECAY_START.remove(id);
			return;
		}

		// 装死已结束 → 确保衰减起点已设（结束那刻 + 30s）
		long now = player.getServer().getTicks();
		long decayStart = DECAY_START.computeIfAbsent(id, k -> now + RETAIN_TICKS);

		// 到点后每秒减 2 HP（代码直接扣，无受击特效）
		if (now >= decayStart && (now - decayStart) % DECAY_INTERVAL == 0) {
			float reduce = Math.min(budget, DECAY_PER_SEC);
			player.setAbsorptionAmount(Math.max(0f, actual - reduce));
			budget -= reduce;
		}

		if (budget <= 0f) {
			BUDGET.remove(id);
			DECAY_START.remove(id);
		} else {
			BUDGET.put(id, budget);
		}
	}
}
