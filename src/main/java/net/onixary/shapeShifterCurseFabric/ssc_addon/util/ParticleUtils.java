package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class ParticleUtils {
    private ParticleUtils() {
    }

    public static void spawnParticles(ServerWorld world, ParticleEffect particle, Vec3d pos, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (world == null) return;
        try {
            world.spawnParticles(particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
        }
    }

    public static void spawnParticles(ServerWorld world, ParticleEffect particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (world == null) return;
        try {
            world.spawnParticles(particle, x, y, z, count, offsetX, offsetY, offsetZ, speed);
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
