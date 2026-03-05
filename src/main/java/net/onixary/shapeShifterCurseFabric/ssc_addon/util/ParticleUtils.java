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
}
