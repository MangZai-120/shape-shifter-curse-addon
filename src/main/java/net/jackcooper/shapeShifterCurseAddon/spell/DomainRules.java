package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class DomainRules {
	public static final int CHARGE_TICKS = 300;
	public static final int DURATION_TICKS = 800;
	public static final double INNER_RADIUS = 16;
	public static final double OUTER_RADIUS = 17;
	public static final double MAX_CAST_DISPLACEMENT = 3;
	public static final double SOUND_RANGE = 64;
	/** 扩张起点：蓄力第 200t（10s）球壳开始从极小半径生长。 */
	public static final int EXPAND_START_TICK = 200;
	/** 扩张时长：200t→300t（10s→15s）共 100t，300t 时恰好到完整半径。 */
	public static final int EXPAND_DURATION_TICKS = 100;

	private DomainRules() {}

	public static float soundVolume(double distance) {
		if (!Double.isFinite(distance) || distance >= SOUND_RANGE) return 0;
		if (distance <= INNER_RADIUS) return (float) (1.0 - 0.2 * Math.max(0, distance) / INNER_RADIUS);
		if (distance <= OUTER_RADIUS) return 0.8f;
		return (float) (0.8 * (SOUND_RANGE - distance) / (SOUND_RANGE - OUTER_RADIUS));
	}

	public static boolean separates(Vec3d from, Vec3d to, double radius) {
		return radius > 0 && (from.lengthSquared() <= radius * radius) != (to.lengthSquared() <= radius * radius);
	}

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
		if (endSquared <= radius * radius + 1.0e-9) return false;
		if (startSquared <= radius * radius) return true;
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

	public record Shell(Vec3d center, double radius, boolean complete) {
		public boolean blocks(Vec3d from, Vec3d movement, double padding) {
			Vec3d start = from.subtract(center);
			Vec3d end = start.add(movement);
			return complete
					? crosses(start.x, start.y, start.z, end.x, end.y, end.z, padding)
					: crossesOutward(start.x, start.y, start.z, end.x, end.y, end.z, radius);
		}

		private Vec3d limit(Vec3d from, Vec3d movement, double padding) {
			if (!blocks(from, movement, padding)) return movement;
			Vec3d start = from.subtract(center);
			double distance = start.length();
			if (distance <= radius) {
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
		return result;
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