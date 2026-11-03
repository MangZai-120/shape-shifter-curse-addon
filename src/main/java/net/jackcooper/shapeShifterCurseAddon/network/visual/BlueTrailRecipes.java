package net.jackcooper.shapeShifterCurseAddon.network.visual;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.random.RandomGenerator;

/** Original SP blue-ring and projectile batch geometry, shared by client and legacy fallback. */
public final class BlueTrailRecipes {
    private BlueTrailRecipes() {}

    /**
     * BLUE_FIRE_RING params: radius, add ordinary flame layer (0/1).
     * SSC_WEB_TRAIL params: authoritative SSC Tier; the client entity's unsynchronized Tier is unused.
     * Other trails have no parameters. Coordinates are relative to the synchronized scene origin.
     */
    public static void emit(ParametricVisuals.Kind kind, int age, long worldTime, float width,
                            float eye, double[] params, RandomGenerator random, SceneEmitter sink) {
        if (age < 0) return;
        switch (kind) {
            case BLUE_FIRE_RING -> {
                if (age % 4 != 0) return;
                double radius = params[0];
                blueLayer(ParticleTypes.SOUL_FIRE_FLAME, radius, width, eye, sink);
                if (params.length > 1 && params[1] != 0) {
                    blueLayer(ParticleTypes.FLAME, radius, width, eye, sink);
                }
            }
            case SSC_WEB_TRAIL -> {
                int tier = (int) params[0];
                switch (tier) {
                    case 1 -> trail(ParticleTypes.ASH, 3, .05, .01, sink);
                    case 2 -> trail(ParticleTypes.SPIT, 1, .05, .01, sink);
                    case 3 -> trail(ParticleTypes.CLOUD, 2, .05, .01, sink);
                    default -> { /* SSC emits no trail for an unsupported Tier. */ }
                }
            }
            case FIRE_BOLT_TRAIL -> trail(ParticleTypes.FLAME, 2, .1, .01, sink);
            case LUNAR_BOLT_TRAIL -> trail(ParticleTypes.END_ROD, 1, .02, .002, sink);
            default -> { }
        }
    }

    private static void blueLayer(ParticleEffect particle, double radius, float width, float eye,
                                  SceneEmitter sink) {
        // The command's center batch uses absolute spread; the SSC circle scales spread by size.
        sink.emit(particle, 0, .5, 0, 10, 3, .1, 3, .02, true, false);
        for (int i = 0; i < 64; i++) {
            double angle = 2 * Math.PI * i / 64;
            sink.emit(particle, radius * Math.cos(angle), .5, radius * Math.sin(angle),
                    1, .1 * width, .1 * eye, .1 * width, .02, true, false);
        }
    }

    private static void trail(ParticleEffect particle, int count, double spread, double speed,
                               SceneEmitter sink) {
        // These three original trails were ordinary projectile cues, outside decoration scopes.
        sink.emit(particle, 0, 0, 0, count, spread, spread, spread, speed, false, true);
    }
}
