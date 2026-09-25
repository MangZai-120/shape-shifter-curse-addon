package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.random.RandomGenerator;

/** Original particle batch geometry, independent of rendering/networking so it can be regression tested. */
public final class VisualRecipe {
    public enum Kind {
        RED_RING(512, true), AMULET_RING(512, true), EMPOWERED_AMULET_RING(512, true),
        NOVA_CHARGE(32, false), FROST_STORM(512, true), SEED_FIELD(32, false);
        public final double range;
        public final boolean force;
        Kind(double range, boolean force) { this.range = range; this.force = force; }
    }
    public enum Particle { SOUL_FIRE, FLAME, LAVA, LARGE_SMOKE, LETHAL_DUST, EDGE_DUST, SNOWFLAKE, CLOUD, GREEN_DUST, HAPPY_VILLAGER, WARPED_SPORE }
    public record Batch(Particle particle, double x, double y, double z, int count,
                        double dx, double dy, double dz, double speed) {}
    @FunctionalInterface public interface Sink { void emit(Batch batch); }
    private VisualRecipe() {}

    /** The owner's near-camera fire can fade; Nova warning outlines and seed boundaries stay intact. */
    public static boolean isDecoration(Kind kind, Batch batch) {
        return switch (kind) {
            case RED_RING, AMULET_RING, EMPOWERED_AMULET_RING -> true;
            case NOVA_CHARGE -> batch.particle() == Particle.LARGE_SMOKE || batch.particle() == Particle.FLAME;
            case FROST_STORM -> true;
            case SEED_FIELD -> false;
        };
    }

    public static void emit(Kind kind, long age, long now, int duration, float width, float eyeHeight,
                            double radius, double outerRadius, RandomGenerator random, Sink sink) {
        if (age < 0 || duration > 0 && age > duration) return;
        switch (kind) {
            case RED_RING, AMULET_RING, EMPOWERED_AMULET_RING -> {
                boolean amulet = kind != Kind.RED_RING;
                batch(sink, Particle.SOUL_FIRE, 0, 0.5, 0, 3, 3, 0.1, 3, 0.02);
                if (!amulet) batch(sink, Particle.FLAME, 0, 0.5, 0, 2, 3, 0.1, 3, 0.02);
                ring(sink, Particle.SOUL_FIRE, age, radius, amulet ? 16 : 20, width, eyeHeight);
                if (amulet) {
                    batch(sink, Particle.FLAME, 0, 0.5, 0, 3, 3, 0.1, 3, 0.02);
                    ring(sink, Particle.FLAME, age, radius, 14, width, eyeHeight);
                }
                for (int i = 0; i < (amulet ? 11 : 9); i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    batch(sink, Particle.FLAME, radius * Math.cos(a), 0.1, radius * Math.sin(a),
                            0, 0, 0.5 * eyeHeight, 0, amulet ? 0.1 : 0.12);
                }
                batch(sink, Particle.LAVA, 0, amulet ? 1 : 1.2, 0, 1, amulet ? 2 : 3, 0.2, amulet ? 2 : 3, 0);
            }
            case NOVA_CHARGE -> {
                if (age % 2 != 0) return;
                double progress = Math.min(1, age / (double) Math.max(1, duration));
                batch(sink, Particle.LARGE_SMOKE, 0, 1, 0, 6, 0.3, 0.4, 0.3, 0.02);
                batch(sink, Particle.FLAME, 0, 0.7, 0, 4, 0.35, 0.4, 0.35, 0.01);
                arc(sink, random, Particle.LETHAL_DUST, radius, now * 0.15, 14 + (int) (progress * 8));
                arc(sink, random, Particle.EDGE_DUST, outerRadius, now * -0.11, 18 + (int) (progress * 10));
            }
            case FROST_STORM -> {
                for (int i = 0; i < 5; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * radius;
                    batch(sink, Particle.SNOWFLAKE, Math.cos(a) * r, random.nextDouble() * 2, Math.sin(a) * r, 1, 0, 0, 0, 0.05);
                }
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.2 + i * Math.PI * 2 / 3;
                    batch(sink, Particle.CLOUD, Math.cos(a) * 2, 1, Math.sin(a) * 2, 1, 0, 0.1, 0, 0);
                }
            }
            case SEED_FIELD -> {
                if (now % 2 == 0) for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8 + age * 0.12;
                    batch(sink, Particle.GREEN_DUST, radius * Math.cos(a), 0.15, radius * Math.sin(a), 1, 0, 0, 0, 0);
                }
                if (now % 6 == 0) {
                    batch(sink, Particle.HAPPY_VILLAGER, 0, 0.8, 0, 1, 0.25, 0.4, 0.25, 0);
                    batch(sink, Particle.WARPED_SPORE, 0, 0.6, 0, 2, 0.35, 0.3, 0.35, 0.01);
                }
            }
        }
    }

    private static void ring(Sink sink, Particle particle, long age, double radius, int count, float width, float eyeHeight) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + age * 0.12;
            batch(sink, particle, radius * Math.cos(a), 0.5, radius * Math.sin(a),
                    1, 0.1 * width, 0.1 * eyeHeight, 0.1 * width, 0.02);
        }
    }
    private static void arc(Sink sink, RandomGenerator random, Particle particle, double radius, double base, int count) {
        double step = Math.PI * 2 / count;
        for (int i = 0; i < count; i++) {
            double a = base + step * i + (random.nextDouble() - 0.5) * step * 0.6;
            double r = radius + (random.nextDouble() - 0.5) * 0.5;
            batch(sink, particle, Math.cos(a) * r, 0.08 + (random.nextDouble() - 0.5) * 0.15,
                    Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }
    private static void batch(Sink sink, Particle particle, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed) {
        sink.emit(new Batch(particle, x, y, z, count, (float) dx, (float) dy, (float) dz, (float) speed));
    }
}
