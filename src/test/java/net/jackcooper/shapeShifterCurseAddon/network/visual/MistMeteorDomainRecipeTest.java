package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jackcooper.shapeShifterCurseAddon.network.visual.recipes.MistMeteorDomainRecipes;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Original counts, spatial distributions and phase boundaries; no performance claims from these checks. */
public final class MistMeteorDomainRecipeTest {
    private static int checks;
    private record Batch(ParticleEffect particle, double x, double y, double z, int count,
                         double dx, double dy, double dz, double speed, boolean decoration, boolean projectile) {}

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        mistDensityAndExpiry();
        meteorWarning();
        domainTimeline();
        repeatableSamples();
        Path report = Path.of("build/reports/network-sync/mist-meteor-domain.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "Recipe checks: " + checks + "\n"
                + "Mist original batches: L1 828; L5 3483.\n"
                + "Meteor original warning batches: radius 3 => 310; radius 5.001 => 510.\n"
                + "Domain original edge batches: radius 24 => 7074; radius 32 => 10658.\n"
                + "Domain original sustaining ambient particles: ordinary 240; enhanced 400.\n"
                + "These are geometry/lifecycle checks, not live visual or frame-time acceptance.\n");
        System.out.println("MistMeteorDomainRecipeTest: " + checks + " checks");
    }

    private static SceneEmitter collect(List<Batch> output) {
        return (p, x, y, z, n, dx, dy, dz, s, d, projectile) ->
                output.add(new Batch(p, x, y, z, n, dx, dy, dz, s, d, projectile));
    }

    private static List<Batch> mist(long remaining, double radius, long seed) {
        List<Batch> output = new ArrayList<>();
        MistMeteorDomainRecipes.mist(remaining, radius, new Random(seed), collect(output));
        return output;
    }

    private static void mistDensityAndExpiry() {
        int[] durations = {120, 220, 123};
        double[] radii = {3, 4.5, 3};
        int[] expected = {828, 3483, 864};
        for (int mode = 0; mode < durations.length; mode++) {
            int batches = 0;
            for (int age = 1; age <= durations[mode]; age++) {
                List<Batch> frame = mist(durations[mode] - age, radii[mode], age);
                batches += frame.size();
                check(frame.isEmpty() == (age == durations[mode] || (durations[mode] - age) % 5 != 0),
                        "Mist uses remaining-time phase and stops before zero");
                for (Batch b : frame) {
                    check(b.x * b.x + b.z * b.z <= radii[mode] * radii[mode] + 1e-12,
                            "Mist samples remain inside the original disk");
                    check(b.y >= .1 && b.y < 1 && b.decoration && !b.projectile && b.count == 1,
                            "Mist height, self-decoration and single-particle batch remain intact");
                    check(b.dx == (float) .15 && b.dy == (float) .1 && b.dz == (float) .15,
                            "Mist preserves ParticleS2CPacket float spread");
                    check(b.particle == ParticleTypes.HAPPY_VILLAGER ? b.speed == 0
                            : b.particle == ParticleTypes.DRAGON_BREATH && b.speed == (float) .002,
                            "Mist preserves green/purple speed");
                }
            }
            check(batches == expected[mode], "Full mist lifecycle batch count: " + mode);
        }
        check(mist(0, 3, 0).isEmpty() && mist(-5, 3, 0).isEmpty(), "Expired mist never emits a final wave");
        long green = 0;
        double squaredRadius = 0;
        long count = 0;
        for (int seed = 0; seed < 1000; seed++) for (Batch b : mist(5, 4.5, seed)) {
            if (b.particle == ParticleTypes.HAPPY_VILLAGER) green++;
            squaredRadius += (b.x * b.x + b.z * b.z) / (4.5 * 4.5);
            count++;
        }
        check(Math.abs(green / (double) count - .25) < .01, "Mist green share stays at one quarter");
        check(Math.abs(squaredRadius / count - .5) < .01, "Mist remains uniform by area, including its perimeter");
    }

    private static void meteorWarning() {
        double[] radii = {3, 5.001, .5};
        int[] ringCounts = {30, 50, 12};
        for (int mode = 0; mode < radii.length; mode++) {
            int total = 0;
            for (int age = 1; age <= 10; age++) {
                List<Batch> frame = new ArrayList<>();
                MistMeteorDomainRecipes.meteorWarning(age, radii[mode], collect(frame));
                check(frame.size() == ringCounts[mode] + 1, "Meteor warning keeps ring density and center smoke");
                total += frame.size();
                for (int i = 0; i < ringCounts[mode]; i++) {
                    Batch b = frame.get(i);
                    near(Math.hypot(b.x, b.z), radii[mode] * .95, "Meteor ring radius");
                    check(b.particle == ParticleTypes.FLAME && b.y == .1 && b.count == 1
                            && !b.decoration && !b.projectile, "Warning boundary remains unaffected by self avoidance");
                }
                near(frame.get(0).x, Math.cos(age * .15) * radii[mode] * .95, "Meteor angular phase follows entity age");
                Batch smoke = frame.get(frame.size() - 1);
                check(smoke.particle == ParticleTypes.LARGE_SMOKE && smoke.x == 0 && smoke.z == 0
                        && smoke.y == .5 && smoke.count == 3 && smoke.speed == (float) .02,
                        "Meteor keeps the original three-particle central smoke cloud");
            }
            check(total == (ringCounts[mode] + 1) * 10, "Meteor ten-tick warning total");
        }
    }

    private static void domainTimeline() {
        int[] maxRadii = {24, 32};
        int[] edgeTotals = {7074, 10658};
        int[] phaseLimits = {300, 500};
        int[] ambientTotals = {240, 400};
        for (int mode = 0; mode < maxRadii.length; mode++) {
            int edgeCount = 0;
            for (int tick = 1; tick <= maxRadii[mode] * 4; tick++) {
                double r = tick * .25;
                List<Batch> frame = new ArrayList<>();
                MistMeteorDomainRecipes.domainEdge(r, collect(frame));
                edgeCount += frame.size();
                verifyEdge(frame, r);
            }
            for (int tick = 1; tick <= maxRadii[mode] * 4; tick++) {
                double r = maxRadii[mode] - tick * .25;
                List<Batch> frame = new ArrayList<>();
                MistMeteorDomainRecipes.domainEdge(r, collect(frame));
                edgeCount += frame.size();
                verifyEdge(frame, r);
            }
            check(edgeCount == edgeTotals[mode], "Domain complete expansion/retraction count");
            int ambientCount = 0;
            for (int age = 0; age < phaseLimits[mode] / 2; age++) {
                int counter = 2 + age * 2;
                List<Batch> frame = new ArrayList<>();
                MistMeteorDomainRecipes.domainAmbient(counter, maxRadii[mode], new Random(age), collect(frame));
                check(frame.size() == (counter % 5 == 0 ? 8 : 0), "Domain actual double-increment phase cadence");
                ambientCount += frame.size();
                for (Batch b : frame) check(b.particle == ParticleTypes.SOUL && b.count == 1
                        && b.decoration && !b.projectile && b.y >= 0 && b.y < 3
                        && Math.hypot(b.x, b.z) <= maxRadii[mode], "Domain ambient shape and self decoration");
            }
            check(ambientCount == ambientTotals[mode], "Original domain sustaining duration and density");
        }
    }

    private static void verifyEdge(List<Batch> frame, double r) {
        check(r >= 1 || frame.isEmpty(), "No domain edge is drawn below radius one");
        check(frame.size() <= 56, "Original edge per-tick particle cap");
        for (Batch b : frame) {
            near(Math.hypot(b.x, b.z), r, "Domain edge stays aligned to converted blocks");
            check(!b.decoration && !b.projectile && b.count == 1, "Domain edge remains visible around the owner");
            check(b.particle == ParticleTypes.SOUL_FIRE_FLAME ? b.y == 1 && b.dy == (float) .3
                    : b.particle == ParticleTypes.SOUL && b.y == .5 && b.dy == (float) .4,
                    "Domain edge preserves both vertical layers");
        }
    }

    private static void repeatableSamples() {
        for (int tick = 0; tick < 100; tick++) {
            long seed = 0x53f6bc11L ^ tick;
            List<Batch> expected = mist(5, 4.5, seed);
            List<Batch> actual = new ArrayList<>();
            Random jitter = new Random(seed ^ 0x71f2L);
            MistMeteorDomainRecipes.mist(5, 4.5, new Random(seed), (p, x, y, z, n, dx, dy, dz, s, d, projectile) -> {
                // A different viewer can accept/reject clouds and consume unrelated jitter samples.
                if (x > 0) for (int i = 0; i < 6; i++) jitter.nextGaussian();
                actual.add(new Batch(p, x, y, z, n, dx, dy, dz, s, d, projectile));
            });
            check(expected.equals(actual), "Viewer culling/jitter does not change later recipe samples");
        }
    }

    private static void near(double actual, double expected, String reason) {
        check(Math.abs(actual - expected) < 1e-10, reason + ": " + actual + " / " + expected);
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
