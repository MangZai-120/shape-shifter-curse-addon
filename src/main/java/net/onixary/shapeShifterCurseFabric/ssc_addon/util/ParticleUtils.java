package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class ParticleUtils {
	private ParticleUtils() {
	}

	/**
	 * 强制生成粒子效果，无视客户端粒子设置（最小/减少）
	 */
	public static <T extends ParticleEffect> void spawnParticles(ServerWorld world, T particle, Vec3d pos, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		spawnParticles(world, particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed);
	}

	/**
	 * 强制生成粒子效果，无视客户端粒子设置（最小/减少）
	 */
	public static <T extends ParticleEffect> void spawnParticles(ServerWorld world, T particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		if (world == null) return;
		try {
			// 使用 force=true 的 ParticleS2CPacket，使粒子在"最小"设置下仍然可见
			ParticleS2CPacket packet = new ParticleS2CPacket(particle, true, x, y, z,
					(float) offsetX, (float) offsetY, (float) offsetZ, (float) speed, count);
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.squaredDistanceTo(x, y, z) <= 262144.0) { // 512格范围内
					player.networkHandler.sendPacket(packet);
				}
			}
		} catch (Exception e) {
		}
	}

	public static void spawnSnowflakeParticles(ServerWorld world, Vec3d pos) {
		spawnParticles(world, net.minecraft.particle.ParticleTypes.SNOWFLAKE, pos, 5, 0.2, 0.2, 0.2, 0.02);
	}

	public static void spawnSnowflakeParticles(ServerWorld world, Vec3d pos, int count) {
		spawnParticles(world, net.minecraft.particle.ParticleTypes.SNOWFLAKE, pos, count, 0.3, 0.3, 0.3, 0.1);
	}

	public static void spawnHitParticles(ServerWorld world, Vec3d pos) {
		spawnParticles(world, net.minecraft.particle.ParticleTypes.SNOWFLAKE, pos, 20, 0.5, 0.5, 0.5, 0.1);
		spawnParticles(world, net.minecraft.particle.ParticleTypes.CLOUD, pos, 10, 0.3, 0.3, 0.3, 0.05);
	}

	public static void spawnTeleportParticles(ServerWorld world, Vec3d pos) {
		spawnParticles(world, net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, pos, 20, 0.3, 0.5, 0.3, 0.05);
	}

	public static void spawnSweepAttackParticles(ServerWorld world, Vec3d pos) {
		spawnParticles(world, net.minecraft.particle.ParticleTypes.SWEEP_ATTACK, pos, 1, 0, 0, 0, 0);
	}

	// ===== 水花爆开（仿 RC-4 药水破碎）：水矛落地 / 涡流释放共用 =====
	private static final net.minecraft.particle.DustParticleEffect WATER_CYAN_DUST =
			new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.20f, 0.62f, 0.92f), 1.5f);
	private static final net.minecraft.particle.BlockStateParticleEffect WATER_PRISMARINE =
			new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
					net.minecraft.block.Blocks.PRISMARINE.getDefaultState());

	/**
	 * 仿 RC-4 药水破碎的水花爆开特效：中心水花/泡泡/青色尘埃扩散云 + 向外抛射的水滴与青色碎块（带重力 → 抛物线）。
	 * 全部 force=true，最小粒子设置下也可见；scale 控制规模。
	 */
	public static void spawnWaterBurst(ServerWorld world, double x, double y, double z, double scale) {
		if (world == null) return;
		net.minecraft.util.math.random.Random rnd = world.getRandom();
		spawnParticles(world, net.minecraft.particle.ParticleTypes.SPLASH, x, y, z, (int) (40 * scale), 0.6 * scale, 0.3, 0.6 * scale, 0.2);
		spawnParticles(world, net.minecraft.particle.ParticleTypes.BUBBLE, x, y, z, (int) (20 * scale), 0.5 * scale, 0.3, 0.5 * scale, 0.05);
		spawnParticles(world, WATER_CYAN_DUST, x, y + 0.3, z, (int) (18 * scale), 0.5 * scale, 0.35, 0.5 * scale, 0.02);
		int n = (int) (24 * scale);
		for (int i = 0; i < n; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double horiz = (0.3 + rnd.nextDouble() * 0.5) * scale;
			double vx = Math.cos(ang) * horiz;
			double vz = Math.sin(ang) * horiz;
			double vy = 0.35 + rnd.nextDouble() * 0.45;
			// count=0 → offset 作为速度向量；白色水花与青色海晶石碎块都带重力 → 抛物线
			spawnParticles(world, (i & 1) == 0 ? net.minecraft.particle.ParticleTypes.SPLASH : WATER_PRISMARINE,
					x, y + 0.2, z, 0, vx, vy, vz, 1.0);
		}
	}
}
