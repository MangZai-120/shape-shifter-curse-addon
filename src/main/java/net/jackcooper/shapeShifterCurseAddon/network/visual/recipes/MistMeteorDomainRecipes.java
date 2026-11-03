package net.jackcooper.shapeShifterCurseAddon.network.visual.recipes;

import net.jackcooper.shapeShifterCurseAddon.network.visual.SceneEmitter;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.random.RandomGenerator;

/** Particle batch geometry only; the server remains responsible for every gameplay effect. */
public final class MistMeteorDomainRecipes {
    private MistMeteorDomainRecipes() {}

    /** Coordinates are relative to the caster's feet, preserving the original uniform disk. */
    public static void mist(long ticksRemaining, double radius, RandomGenerator random, SceneEmitter sink) {
        if (ticksRemaining <= 0 || ticksRemaining % 5 != 0) return;
        int count = (int) Math.max(10, radius * radius * 4);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = 0.1 + random.nextDouble() * 0.9;
            boolean green = random.nextInt(4) == 0;
            batch(sink, green ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.DRAGON_BREATH,
                    x, y, z, 1, 0.15, 0.1, 0.15, green ? 0 : 0.002, true);
        }
    }

    /** Coordinates are relative to the fixed impact position, rather than the hovering meteor. */
    public static void meteorWarning(long entityAge, double radius, SceneEmitter sink) {
        int ringCount = (int) Math.max(12, radius * 10);
        for (int i = 0; i < ringCount; i++) {
            double angle = i * 2 * Math.PI / ringCount + entityAge * 0.15;
            double r = radius * 0.95;
            batch(sink, ParticleTypes.FLAME, Math.cos(angle) * r, 0.1, Math.sin(angle) * r,
                    1, 0.02, 0.02, 0.02, 0.005, false);
        }
        batch(sink, ParticleTypes.LARGE_SMOKE, 0, 0.5, 0, 3, 0.3, 0.5, 0.3, 0.02, false);
    }

    /** Relative to (center block X + .5, centerY, center block Z + .5). */
    public static void domainEdge(double radius, SceneEmitter sink) {
        if (radius < 1) return;
        int flameCount = Math.min((int) (radius * 2), 32);
        for (int i = 0; i < flameCount; i++) {
            double angle = Math.PI * 2 * i / flameCount;
            batch(sink, ParticleTypes.SOUL_FIRE_FLAME, Math.cos(angle) * radius, 1, Math.sin(angle) * radius,
                    1, 0, 0.3, 0, 0.01, false);
        }
        int soulCount = Math.min((int) (radius * 1.5), 24);
        for (int i = 0; i < soulCount; i++) {
            double angle = Math.PI * 2 * i / soulCount;
            batch(sink, ParticleTypes.SOUL, Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius,
                    1, 0.2, 0.4, 0.2, 0.02, false);
        }
    }

    /** Keep the existing linear-radius distribution and the skill's actual phase counter. */
    public static void domainAmbient(long phaseCounter, double radius, RandomGenerator random, SceneEmitter sink) {
        if (phaseCounter % 5 != 0) return;
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * radius;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = random.nextDouble() * 3;
            batch(sink, ParticleTypes.SOUL, x, y, z, 1, 0.5, 0.5, 0.5, 0.02, true);
        }
    }

    private static void batch(SceneEmitter sink, ParticleEffect particle, double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed, boolean decoration) {
        // ParticleS2CPacket narrowed these four values to float before the old client generated jitter.
        sink.emit(particle, x, y, z, count, (float) dx, (float) dy, (float) dz, (float) speed, decoration, false);
    }
}
