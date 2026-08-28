package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP阿努比斯之狼「凋零阶梯」追踪。
 *
 * 设计（用户 2026-07-04 确认，方向 D 凋零共生深化 + 保留并强化负面）：
 * - 凋零每多持续 2 秒升一阶，每级 +10% 增伤（一阶+10% / 二阶+20% / 三阶+30%）。
 * - 阶梯只升不降，直到凋零彻底结束（中途受击刷新 duration 不清零 elapsed）。
 * - 自己 + 冥狼造成的伤害都按当前阶梯缩放（mixin 读取）。
 *
 * 阶梯定义：
 *   一阶 T1：elapsed < 40t（2秒）  → 倍率 1.10
 *   二阶 T2：40t ≤ elapsed < 80t    → 倍率 1.20
 *   三阶 T3：elapsed ≥ 80t（4秒+）  → 倍率 1.30
 *
 * 玩家无凋零 → 清除记录，倍率 1.0（不增伤）。
 */
public final class WitherFrenzyManager {

	/** 一阶阈值（含）：凋零持续 0~40t */
	private static final int T1_MAX = 40;
	/** 二阶阈值（含）：凋零持续 40~80t */
	private static final int T2_MAX = 80;

	private static final float MULT_T1 = 1.10f;
	private static final float MULT_T2 = 1.20f;
	private static final float MULT_T3 = 1.30f;

	/** 玩家UUID -> 进入凋零的服务端 tick（首次检测到凋零时记录） */
	private static final Map<UUID, Long> WITHER_START = new ConcurrentHashMap<>();

	// ===== 凋零抗性（伤害 -20% + 间隔 +40%）=====
	/** 玩家UUID -> 凋零 tick 伤害计数（用于跳过计数实现间隔延长） */
	private static final Map<UUID, Integer> WITHER_TICK_COUNT = new ConcurrentHashMap<>();
	/** 凋零伤害减免系数（-20%） */
	private static final float WITHER_DAMAGE_REDUCE = 0.8f;
	/** 跳过周期：每 7 次凋零 tick 跳过 2 次（跳过率 2/7≈28.6% ≈ 间隔 +40%） */
	private static final int SKIP_PERIOD = 7;
	private static final int SKIP_COUNT = 2;

	private WitherFrenzyManager() {
	}

	/** 每服务端 tick 对每个在线 SP阿努比斯玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		boolean hasWither = player.hasStatusEffect(StatusEffects.WITHER);
		if (hasWither) {
			// 从无到有 → 记录起点；持续中不重置（elapsed 累积）
			WITHER_START.computeIfAbsent(id, k -> Long.valueOf(player.getServer().getTicks()));
		} else {
			// 凋零结束 → 清零，下次重新从 T1 起
			WITHER_START.remove(id);
			resetWitherTickCount(id);
		}
	}

	/**
	 * 取当前凋零阶梯下的伤害倍率（1.0 / 1.1 / 1.2 / 1.3）。
	 * 非 SP阿努比斯或无凋零返回 1.0。
	 */
	public static float getDamageMultiplier(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) return 1.0f;
		if (!player.hasStatusEffect(StatusEffects.WITHER)) return 1.0f;
		Long start = WITHER_START.get(player.getUuid());
		if (start == null) return MULT_T1; // 有凋零但起点未记录（首 tick 前），按 T1
		long elapsed = player.getServer().getTicks() - start;
		if (elapsed < T1_MAX) return MULT_T1;
		if (elapsed < T2_MAX) return MULT_T2;
		return MULT_T3;
	}

	/** 玩家断线 / 变形时清理。 */
	public static void clear(UUID uuid) {
		WITHER_START.remove(uuid);
		WITHER_TICK_COUNT.remove(uuid);
	}

	/**
	 * 凋零伤害抗性：对 SP阿努比斯，凋零造成的伤害应减免为原值的多少倍。
	 * 实现间隔 +40%：每 7 次 tick 跳过 2 次（返回 0），其余正常。
	 * 实现伤害 -20%：未跳过时返回 0.8。
	 * 返回 0 表示本次凋零 tick 伤害完全取消。
	 * 非 SP阿努比斯返回 1.0（不改）。
	 */
	public static float getWitherDamageScale(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) return 1.0f;
		if (!player.hasStatusEffect(StatusEffects.WITHER)) return 1.0f;
		UUID id = player.getUuid();
		int count = WITHER_TICK_COUNT.merge(id, 1, Integer::sum);
		// 跳过周期内最后 SKIP_COUNT 次（实现间隔延长）
		if (count > SKIP_PERIOD - SKIP_COUNT) {
			if (count >= SKIP_PERIOD) WITHER_TICK_COUNT.put(id, 0);
			return 0.0f;
		}
		return WITHER_DAMAGE_REDUCE;
	}

	/** 凋零结束时清理 tick 计数（由 tick() 在凋零结束时统一处理）。 */
	public static void resetWitherTickCount(UUID uuid) {
		WITHER_TICK_COUNT.remove(uuid);
	}

	// ==================== 凋零传染（玩家攻击 + 冥狼攻击共用）====================
	/**
	 * 每次攻击消耗的自身凋零时间（tick）。5 秒 = 100t。
	 */
	private static final int INFECT_COST_TICKS = 100;
	/**
	 * 目标凋零 duration 累加上限（tick）。15 秒 = 300t。
	 */
	private static final int INFECT_TARGET_CAP = 300;

	/**
	 * 凋零传染：消耗 source（SP阿努比斯玩家）身上的凋零时间，转移给 target。
	 * 规则（用户 2026-07-04 确认）：
	 * - source 必须有凋零效果；无凋零则不传染（返回）。
	 * - 每次消耗自身 100t（5秒）凋零 duration：不足 100t 则消耗全部剩余。
	 * - 转移给目标的凋零时间 = 实际消耗量；等级 = source 当前凋零等级。
	 * - 目标已有凋零 → duration 累加，上限 300t（15秒）；等级取较高者。
	 * - source 凋零被消耗到 0 → 效果自然消失（由 vanilla 处理）。
	 * 仅服务端调用。
	 *
	 * @param source SP阿努比斯玩家（自身凋零的来源）
	 * @param target 被攻击的目标
	 * @return 实际转移的凋零 tick 数（0 表示未传染）
	 */
	public static int tryWitherInfect(ServerPlayerEntity source, LivingEntity target) {
		if (source == null || target == null) return 0;
		if (source.getWorld().isClient()) return 0;
		if (!FormUtils.isForm(source, FormIdentifiers.ANUBIS_WOLF_SP)) return 0;
		StatusEffectInstance srcWither = source.getStatusEffect(StatusEffects.WITHER);
		if (srcWither == null) return 0; // 自身无凋零 → 不传染
		if (!(target instanceof LivingEntity)) return 0;

		int srcDuration = srcWither.getDuration();
		int srcAmplifier = srcWither.getAmplifier();
		// 实际消耗：min(剩余, 100t)；至少消耗 1t（避免 0 duration 卡住）
		int cost = Math.min(srcDuration, INFECT_COST_TICKS);
		if (cost <= 0) return 0;

		// 扣减自身凋零 duration：用新实例覆盖（vanilla StatusEffectInstance 不可变 duration）
		int newSrcDuration = Math.max(0, srcDuration - cost);
		if (newSrcDuration <= 0) {
			source.removeStatusEffect(StatusEffects.WITHER);
		} else {
			source.setStatusEffect(
					new StatusEffectInstance(StatusEffects.WITHER, newSrcDuration, srcAmplifier,
							srcWither.isAmbient(), srcWither.shouldShowParticles(), srcWither.shouldShowIcon()),
					source);
		}

		// 给目标附加凋零：累加 duration，上限 300t；等级取较高（带传染源 source 供入梦拦截归因）
		StatusEffectInstance tgtWither = target.getStatusEffect(StatusEffects.WITHER);
		int tgtBase = tgtWither != null ? tgtWither.getDuration() : 0;
		int tgtAmp = tgtWither != null ? Math.max(tgtWither.getAmplifier(), srcAmplifier) : srcAmplifier;
		int tgtNew = Math.min(tgtBase + cost, INFECT_TARGET_CAP);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, tgtNew, tgtAmp), source);
		return cost;
	}
}
