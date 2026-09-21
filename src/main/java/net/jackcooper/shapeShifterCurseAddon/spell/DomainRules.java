package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.math.Vec3d;

public final class DomainRules {
	public static final int CHARGE_TICKS = 300;
	public static final int DURATION_TICKS = 800;
	public static final double INNER_RADIUS = 16;
	public static final double OUTER_RADIUS = 17;
	public static final double MAX_CAST_DISPLACEMENT = 3;
	/** 扩张起点：蓄力第 200t（10s）球壳开始从极小半径生长。 */
	public static final int EXPAND_START_TICK = 200;
	/** 扩张时长：200t→300t（10s→15s）共 100t，300t 时恰好到完整半径。 */
	public static final int EXPAND_DURATION_TICKS = 100;

	private DomainRules() {}

	/**
	 * 蓄力扩张半径（2006-09-21 需求）：蓄力第 10 秒起球壳从极小（0.75 格，仅容纳施法者）
	 * 以三次缓出曲线生长，第 15 秒到达 INNER_RADIUS 完整体。10 秒前为 0（未成壳）。
	 */
	public static double expansionRadius(double chargeElapsed) {
		if (chargeElapsed < EXPAND_START_TICK) return 0;
		if (chargeElapsed >= CHARGE_TICKS) return INNER_RADIUS;
		double progress = (chargeElapsed - EXPAND_START_TICK) / EXPAND_DURATION_TICKS;
		return 0.75 + (INNER_RADIUS - 0.75) * (1 - Math.pow(1 - progress, 3));
	}

	/**
	 * 单向阀判定（扩张期专用，2006-09-21 需求）：扩张期间外部可进、已在内部的不能出。
	 * 半径 r < INNER_RADIUS：起点（严格按中心距，不叠 padding）在壳内→终点出壳=拦截；
	 * 起点在壳外→终点入壳=放行（单向）。完全体后由 {@link #crosses} 双向封锁。
	 */
	public static boolean crossesOutward(double startX, double startY, double startZ,
	                                     double endX, double endY, double endZ, double radius) {
		if (radius <= 0) return false;
		double startSquared = startX * startX + startY * startY + startZ * startZ;
		double endSquared = endX * endX + endY * endY + endZ * endZ;
		return startSquared <= radius * radius && endSquared > radius * radius + 1.0e-9;
	}

	/**
	 * 球面滑行钳制（2026-09-22 反馈修正）：整体二分缩放会把斜向移动的切向分量一并吃掉，
	 * 表现为「贴墙卡死/容易卡住」。起点在壳内的实体撞壳时改为只压缩径向分量、完整保留
	 * 切向分量——像贴着一面球形墙滑行。起点不在壳内（壳外/壳间）时原样返回，由调用方
	 * 走二分兜底。推导：t⊥n 时 |start+t+k·n|² = s²+|t|²+2ks+k²，解得 k_max = −s+√(R²−|t|²)。
	 */
	public static Vec3d slideInside(Vec3d startRel, Vec3d movement, double innerR) {
		double s = startRel.length();
		if (innerR <= 0 || s > innerR) return movement;
		Vec3d n;
		double r;
		if (s < 1.0e-6) {
			double magnitude = movement.length();
			if (magnitude < 1.0e-9) return movement;
			n = movement.multiply(1.0 / magnitude);
			r = magnitude;
		} else {
			n = startRel.multiply(1.0 / s);
			r = movement.dotProduct(n);
		}
		Vec3d tangential = movement.subtract(n.multiply(r));
		double kMax = s < 1.0e-6
				? innerR
				: -s + Math.sqrt(Math.max(0.0, innerR * innerR - tangential.lengthSquared()));
		if (r <= kMax) return movement;
		return tangential.add(n.multiply(Math.max(0.0, Math.min(r, kMax))));
	}

	public static double layerProgress(double elapsedTicks, int startTick) {
		double progress = Math.max(0, Math.min(1, (elapsedTicks - startTick) / 24));
		return 1 - Math.pow(1 - progress, 3);
	}

	public static boolean crosses(double startX, double startY, double startZ,
	                              double endX, double endY, double endZ, double padding) {
		double inner = Math.max(0.1, INNER_RADIUS - padding);
		double outer = OUTER_RADIUS + padding;
		double startSquared = startX * startX + startY * startY + startZ * startZ;
		double endSquared = endX * endX + endY * endY + endZ * endZ;
		if (startSquared <= INNER_RADIUS * INNER_RADIUS) return endSquared > Math.max(inner * inner, startSquared) + 1.0e-9;
		double deltaX = endX - startX;
		double deltaY = endY - startY;
		double deltaZ = endZ - startZ;
		double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		if (lengthSquared < 1.0e-12) return false;
		double closest = Math.max(0, Math.min(1,
				-(startX * deltaX + startY * deltaY + startZ * deltaZ) / lengthSquared));
		double nearX = startX + deltaX * closest;
		double nearY = startY + deltaY * closest;
		double nearZ = startZ + deltaZ * closest;
		return nearX * nearX + nearY * nearY + nearZ * nearZ < Math.min(outer * outer, startSquared) - 1.0e-9;
	}

	public static float receivedMultiplier(boolean friendly) {
		return friendly ? 0.5f : 1.35f;
	}

	public static float dealtMultiplier(boolean friendly) {
		return friendly ? 1.5f : 0.75f;
	}
}