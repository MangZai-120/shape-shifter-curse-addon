package net.jackcooper.shapeShifterCurseAddon.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;

/**
 * 法术演出粒子采样工具（jackcooper，2026-09-23 抽取）：
 * 原先 6 个法术各自复制了一份环形/球面采样数学（CorruptMist / LunarVeil / VoidErosion /
 * VoidDevour 的 spawnRing、FlameNova / FrostNova 的 spawnSphere），改密度/层数要改多处
 * 且已经出现「烈焰改了冰霜忘改」的轮回——统一收敛到本类，各法术按参数取用。
 *
 * <p>几何与原各处实现逐字一致（斐波那契球面、黄金角、均匀圆周），仅参数化。</p>
 */
public final class SpellFxUtils {

	private SpellFxUtils() {}

	/**
	 * 沿水平圆周均匀撒粒子（原 CorruptMist/LunarVeil/VoidErosion/VoidDevour 的 spawnRing 同款）。
	 *
	 * @param radius 圆周半径（格）
	 * @param count  圆周上粒子数
	 */
	public static void ring(ServerWorld world, ParticleEffect particle,
	                        double x, double y, double z, double radius, int count) {
		for (int i = 0; i < count; i++) {
			double angle = 2 * Math.PI * i / count;
			world.spawnParticles(particle,
					x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
					1, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/**
	 * 双层球面粒子爆开（原 FlameNova/FrostNova 的 spawnSphere 同款几何：斐波那契球面均匀采样）。
	 * 外层满半径 + 内层 0.65 倍半径 + 中心填充。
	 *
	 * @param outerParticle 外层球面粒子（如 FLAME / SNOWFLAKE）
	 * @param innerParticle 内层球面粒子（如 LAVA / SNOWFLAKE）
	 * @param centerParticle 中心填充粒子（如 CAMPFIRE_COSY_SMOKE / CLOUD）
	 * @param centerCount 中心填充粒子数（原烈焰 6 / 冰霜 8）
	 */
	public static void sphere(ServerWorld world, double cx, double cy, double cz, double radius,
	                          ParticleEffect outerParticle, ParticleEffect innerParticle,
	                          ParticleEffect centerParticle, int centerCount) {
		// 外层球面：沿球面按表面积近似均匀分布
		int outerCount = (int) Math.max(24, radius * radius * 12);
		for (int i = 0; i < outerCount; i++) {
			double[] dir = fibonacciSphereDir(i, outerCount);
			world.spawnParticles(outerParticle,
					cx + dir[0] * radius, cy + dir[1] * radius, cz + dir[2] * radius,
					1, 0.02, 0.02, 0.02, 0.001);
		}
		// 内层球面（0.65 倍半径）：增加球体厚度感
		int innerCount = outerCount / 2;
		double innerR = radius * 0.65;
		for (int i = 0; i < innerCount; i++) {
			double[] dir = fibonacciSphereDir(i, innerCount);
			world.spawnParticles(innerParticle,
					cx + dir[0] * innerR, cy + dir[1] * innerR, cz + dir[2] * innerR,
					1, 0.02, 0.02, 0.02, 0.001);
		}
		// 中心填充：蘑菇云状/云雾
		world.spawnParticles(centerParticle,
				cx, cy + 0.3, cz, centerCount, radius * 0.3, 0.2, radius * 0.3, 0.01);
	}

	/** 斐波那契球面方向向量：极角 acos 均匀 + 黄金角方位（第 i / count 个点）。 */
	private static double[] fibonacciSphereDir(int i, int count) {
		double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / count);   // 极角均匀
		double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;      // 黄金角方位
		return new double[]{
				Math.sin(phi) * Math.cos(theta),
				Math.cos(phi),
				Math.sin(phi) * Math.sin(theta)};
	}
}
