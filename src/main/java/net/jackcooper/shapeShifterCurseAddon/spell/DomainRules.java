package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Predicate;

public final class DomainRules {
	private static final double BOUNDARY_EPSILON = 1.0e-9;
	/** 默认蓄力时长（15s）；运行时从 balance 快照读取（systems.domain.charge_ticks）。 */
	public static final int CHARGE_TICKS = 300;
	/** 默认完全体持续时长（40s）；运行时从 balance 快照读取（systems.domain.duration_ticks）。 */
	public static final int DURATION_TICKS = 800;
	/** 默认内层半径（格）；运行时从 balance 快照读取（systems.domain.inner_radius）。 */
	public static final double INNER_RADIUS = 16;
	/** 默认外层半径（格）；运行时从 balance 快照读取（systems.domain.outer_radius）。 */
	public static final double OUTER_RADIUS = 17;
	/** 默认蓄力期最大位移（格）；运行时从 balance 快照读取（systems.domain.max_cast_displacement）。 */
	public static final double MAX_CAST_DISPLACEMENT = 3;
	/** 音效广播范围：原 64 格的两倍；只扩大声音，不改变领域几何。 */
	public static final double SOUND_RANGE = 128;
	/** 扩张起点：默认蓄力第 200t（10s）球壳开始从极小半径生长；运行时从 balance 快照读取（systems.domain.expand_start_tick）。 */
	public static final int EXPAND_START_TICK = 200;
	/** 扩张时长：默认 200t→300t（10s→15s）共 100t，300t 时恰好到完整半径；运行时从 balance 快照读取（systems.domain.expand_duration_ticks）。 */
	public static final int EXPAND_DURATION_TICKS = 100;

	private DomainRules() {}

	// ==== balance 快照读取（systems.domain，双端同源）：物理客户端读 clientSnapshot 镜像，
	// 否则读服务端权威快照，缺快照回退默认常量（测试环境无 Fabric/快照 → 默认值，与原行为一致）。 ====

	private static double balD(String param, double def) {
		boolean physicalClient = false;
		try { physicalClient = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isClientThread(); } catch (Throwable ignored) {}
		if (physicalClient) {
			var cs = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientSnapshot();
			if (cs != null) return cs.getDouble("systems.domain", param);
		}
		var s = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.currentSnapshot();
		return s == null ? def : s.getDouble("systems.domain", param);
	}

	private static int balI(String param, int def) {
		boolean physicalClient = false;
		try { physicalClient = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isClientThread(); } catch (Throwable ignored) {}
		if (physicalClient) {
			var cs = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientSnapshot();
			if (cs != null) return (int) cs.getInt("systems.domain", param);
		}
		var s = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.currentSnapshot();
		return s == null ? def : (int) s.getInt("systems.domain", param);
	}

	/** 内层半径（双端一致）。 */
	public static double innerRadius() { return balD("inner_radius", INNER_RADIUS); }

	/** 外层半径（双端一致）。 */
	public static double outerRadius() { return balD("outer_radius", OUTER_RADIUS); }

	/** 蓄力时长 tick（双端一致）。 */
	public static int chargeTicks() { return balI("charge_ticks", CHARGE_TICKS); }

	/** 完全体持续时长 tick（双端一致）。 */
	public static int durationTicks() { return balI("duration_ticks", DURATION_TICKS); }

	/** 扩张起点 tick（双端一致）。 */
	public static int expandStartTick() { return balI("expand_start_tick", EXPAND_START_TICK); }

	/** 扩张时长 tick（双端一致）。 */
	public static int expandDurationTicks() { return balI("expand_duration_ticks", EXPAND_DURATION_TICKS); }

	/** 蓄力期最大位移（双端一致）。 */
	public static double maxCastDisplacement() { return balD("max_cast_displacement", MAX_CAST_DISPLACEMENT); }

	public static float soundVolume(double distance) {
		return soundVolume(distance, innerRadius(), outerRadius());
	}

	/** 起手快照版：半径由 Field 快照/网络包传入（2026-09-27，双端几何同源）。 */
	public static float soundVolume(double distance, double inner, double outer) {
		if (!Double.isFinite(distance) || distance >= SOUND_RANGE) return 0;
		inner = net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules.distance(inner);
		outer = net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules.distance(outer);
		if (distance <= inner) return (float) (1.0 - 0.2 * Math.max(0, distance) / inner);
		if (distance <= outer) return 0.8f;
		return (float) (0.8 * (SOUND_RANGE - distance) / (SOUND_RANGE - outer));
	}

	public static boolean separates(Vec3d from, Vec3d to, double radius) {
		return radius > 0 && (from.lengthSquared() <= radius * radius) != (to.lengthSquared() <= radius * radius);
	}

	/**
	 * 蓄力扩张半径（2006-09-21 需求）：蓄力第 10 秒起球壳从极小（0.75 格，仅容纳施法者）
	 * 以三次缓出曲线生长，第 15 秒到达 INNER_RADIUS 完整体。10 秒前为 0（未成壳）。
	 * 无参版本读当前 balance 快照（起手前判定用）；领域会话内请用带快照参数的
	 * {@link #expansionRadius(double, int, int, int, double)}（reload 不影响已展开领域）。
	 */
	public static double expansionRadius(double chargeElapsed) {
		return expansionRadius(chargeElapsed, expandStartTick(), chargeTicks(), expandDurationTicks(), innerRadius());
	}

	/** 起手快照版：曲线参数由 Field 快照传入（2026-09-27 审计修复，服务端结算与客户端包值同源）。 */
	public static double expansionRadius(double chargeElapsed, int expandStart, int charge, int expandDuration, double inner) {
		if (chargeElapsed < expandStart) return 0;
		if (chargeElapsed >= charge) return inner;
		double progress = (chargeElapsed - expandStart) / (double) expandDuration;
		return 0.75 + (inner - 0.75) * (1 - Math.pow(1 - progress, 3));
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
		if (endSquared <= radius * radius + BOUNDARY_EPSILON) return false;
		if (startSquared <= radius * radius + BOUNDARY_EPSILON) return true;
		double deltaX = endX - startX;
		double deltaY = endY - startY;
		double deltaZ = endZ - startZ;
		double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		if (lengthSquared == 0) return false;
		double closest = Math.max(0, Math.min(1,
				-(startX * deltaX + startY * deltaY + startZ * deltaZ) / lengthSquared));
		double nearX = startX + deltaX * closest;
		double nearY = startY + deltaY * closest;
		double nearZ = startZ + deltaZ * closest;
		return nearX * nearX + nearY * nearY + nearZ * nearZ < radius * radius - 1.0e-9;
	}

	/** 球内滑行保留切向分量，并允许曲面所需的向内修正；球外起点交给外侧碰撞处理。 */
	public static Vec3d slideInside(Vec3d startRel, Vec3d movement, double innerR) {
		double distance = startRel.length();
		if (innerR <= 0 || distance > innerR || startRel.add(movement).lengthSquared() <= innerR * innerR) return movement;
		double radius = Math.max(0, innerR - 1.0e-7);
		if (distance < 1.0e-9) return movement.normalize().multiply(radius);
		Vec3d normal = startRel.multiply(1.0 / distance);
		double radial = movement.dotProduct(normal);
		Vec3d tangential = movement.subtract(normal.multiply(radial));
		if (tangential.lengthSquared() > radius * radius) tangential = tangential.normalize().multiply(radius);
		double extent = Math.sqrt(Math.max(0, radius * radius - tangential.lengthSquared()));
		double limitedRadial = Math.max(-distance - extent, Math.min(radial, -distance + extent));
		return tangential.add(normal.multiply(limitedRadial));
	}

	public record Shell(Vec3d center, double radius, double outerRadius, boolean complete) {
		/** 兼容构造（测试/未接入快照链路的旧调用点）：outer 读当前 balance，主链路请用 4 参构造传快照。
		 *  ⚠ record 附加构造器首句必须 this(...)：读值须在调用前完成，且用类名限定避免与组件名遮蔽。 */
		public Shell(Vec3d center, double radius, boolean complete) {
			this(center, radius, DomainRules.outerRadius(), complete);
		}

		public boolean blocks(Vec3d from, Vec3d movement, double padding) {
			Vec3d start = from.subtract(center);
			Vec3d end = start.add(movement);
			return complete
					? crosses(start.x, start.y, start.z, end.x, end.y, end.z, padding, radius, outerRadius)
					: crossesOutward(start.x, start.y, start.z, end.x, end.y, end.z, radius);
		}

		private Vec3d limit(Vec3d from, Vec3d movement, double padding) {
			if (!blocks(from, movement, padding)) return movement;
			Vec3d start = from.subtract(center);
			double distance = start.length();
			if (start.lengthSquared() <= radius * radius + BOUNDARY_EPSILON) {
				double wall = complete ? Math.max(distance, Math.max(0.1, radius - padding)) : radius;
				return slideInside(start, movement, wall);
			}
			double low = 0, high = 1;
			for (int iteration = 0; iteration < 24; iteration++) {
				double middle = (low + high) * 0.5;
				if (blocks(from, movement.multiply(middle), padding)) high = middle;
				else low = middle;
			}
			Vec3d contactMovement = movement.multiply(Math.max(0, low - 1.0e-7));
			if (!complete) return contactMovement;
			Vec3d normal = start.add(contactMovement).normalize();
			Vec3d remaining = movement.subtract(contactMovement);
			return contactMovement.add(remaining.subtract(normal.multiply(Math.min(0, remaining.dotProduct(normal)))));
		}
	}

	public static Vec3d limitMovement(Vec3d from, Vec3d movement, double padding, List<Shell> shells) {
		Vec3d result = movement;
		for (int pass = 0; pass < 4; pass++) {
			boolean changed = false;
			for (Shell shell : shells) {
				if (!shell.blocks(from, result, padding)) continue;
				result = shell.limit(from, result, padding);
				changed = true;
			}
			if (!changed) return result;
		}
		return clipMovement(from, result, padding, shells);
	}

	/** 最终碰撞结果只能缩短，不能再添加会被地面、墙角抵消的滑行分量。 */
	public static Vec3d clipMovement(Vec3d from, Vec3d movement, double padding, List<Shell> shells) {
		Vec3d result = movement;
		for (Shell shell : shells) {
			if (!shell.blocks(from, result, padding)) continue;
			double low = 0, high = 1;
			for (int iteration = 0; iteration < 24; iteration++) {
				double middle = (low + high) * 0.5;
				if (shell.blocks(from, result.multiply(middle), padding)) high = middle;
				else low = middle;
			}
			result = result.multiply(Math.max(0, low - 1.0e-7));
		}
		// 多个壳依次裁剪后仍需整体终检（扩张壳允许进入，沿线可行区间不一定从起点连续）。
		for (Shell shell : shells) if (shell.blocks(from, result, padding)) return Vec3d.ZERO;
		return result;
	}

	/** 校正位置必须同时满足球壳和方块约束；找不到时留在原位，仍可向客户端发送纠正包。 */
	public static Vec3d safeCorrection(Vec3d from, Vec3d target, Predicate<Vec3d> allowed) {
		double y = target.y;
		for (int attempt = 0; attempt < 6; attempt++) {
			Vec3d candidate = new Vec3d(target.x, y, target.z);
			if (allowed.test(candidate)) return candidate;
			y = Math.floor(y) + 1.0;
		}
		return from;
	}

	public static double layerProgress(double elapsedTicks, int startTick) {
		double progress = Math.max(0, Math.min(1, (elapsedTicks - startTick) / 24));
		return 1 - Math.pow(1 - progress, 3);
	}

	public static boolean crosses(double startX, double startY, double startZ,
	                              double endX, double endY, double endZ, double padding) {
		return crosses(startX, startY, startZ, endX, endY, endZ, padding, innerRadius(), outerRadius());
	}

	/** 起手快照版：内外层半径由 Field 快照传入（2026-09-27 审计修复，服务端结算与客户端包值同源）。 */
	public static boolean crosses(double startX, double startY, double startZ,
	                              double endX, double endY, double endZ, double padding,
	                              double innerR, double outerR) {
		double inner = Math.max(0.1, innerR - padding);
		double outer = outerR + padding;
		double startSquared = startX * startX + startY * startY + startZ * startZ;
		double endSquared = endX * endX + endY * endY + endZ * endZ;
		// 终点容差与下一步起点分类必须相同，否则一步极小越界后会被当成壳外实体放行。
		// 贴内墙时容差锚定在实际半径，不能用每步略微外移后的起点继续向外累积。
		if (startSquared <= innerR * innerR + BOUNDARY_EPSILON) {
			return endSquared > Math.max(inner * inner, Math.min(innerR * innerR, startSquared)) + BOUNDARY_EPSILON;
		}
		double deltaX = endX - startX;
		double deltaY = endY - startY;
		double deltaZ = endZ - startZ;
		double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		if (lengthSquared == 0) return false;
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
