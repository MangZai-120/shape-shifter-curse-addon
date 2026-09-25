package net.jackcooper.shapeShifterCurseAddon.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Batch;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Particle;

/** Deterministic recipe regression plus bounded-latency timing simulation; not a live-server benchmark. */
public final class NetworkPredictionTest {
    private static int checks;
    private static final StringBuilder REPORT = new StringBuilder();
    public static void main(String[] args) throws Exception {
        fireRecipes();
        otherRecipes();
        countdownBoundaries();
        protocolAndSnapshots();
        simulateCountdowns();
        simulateVisuals();
        log("Recipe/boundary assertions: " + checks);
        log("Timing conditions: 20 TPS, ordered reliable delivery, 0-50 ms one-way delay, client clock offset -1/0/+1 tick.");
        log("Scope: deterministic simulation; this does not certify arbitrary latency, server stalls, or an unmeasured live session.");
        Path report = Path.of("build/reports/network-sync/regression.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, REPORT, StandardCharsets.UTF_8);
    }

    private static void fireRecipes() throws Exception {
        JsonObject fixture;
        try (var stream = NetworkPredictionTest.class.getResourceAsStream("/network/original-fire-ring.json")) {
            if (stream == null) throw new AssertionError("Missing pre-change particle fixture");
            fixture = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Kind[] kinds = {Kind.RED_RING, Kind.AMULET_RING, Kind.EMPOWERED_AMULET_RING};
        String[] names = {"red", "amulet", "empowered"};
        for (int mode = 0; mode < kinds.length; mode++) for (int frame = 0; frame < 240; frame++) {
            float width = frame % 2 == 0 ? 0.6f : 1.3f, eye = frame % 3 == 0 ? 0.35f : 1.62f;
            long age = 10000 + frame;
            List<Batch> actual = recipe(kinds[mode], age, age, 0, width, eye, mode == 0 ? 6 : 3.6, 0, frame);
            List<Batch> expected = originalFire(fixture.getAsJsonArray(names[mode]), age, width, eye, frame);
            check(actual.size() == expected.size(), "Original fire-ring batch count");
            for (int i = 0; i < actual.size(); i++) equivalent(actual.get(i), expected.get(i));
            int particles = actual.stream().mapToInt(b -> Math.max(1, b.count())).sum();
            check(particles == (mode == 0 ? 35 : 48), "Original visible fire-ring density");
        }
        log("Fire rings: 720 frames compared against the original JSON, including directed flames, scaled spread and random angles.");
    }

    private static List<Batch> originalFire(JsonArray actions, long age, float width, float eye, long seed) {
        List<Batch> result = new ArrayList<>();
        Random random = new Random(seed);
        for (var element : actions) {
            JsonObject a = element.getAsJsonObject();
            if (a.get("type").getAsString().equals("apoli:execute_command")) {
                String[] c = a.get("command").getAsString().split(" ");
                result.add(new Batch(particle(c[1]), 0, Double.parseDouble(c[3].substring(1)), 0,
                        Integer.parseInt(c[9]), (float) Double.parseDouble(c[5]), (float) Double.parseDouble(c[6]),
                        (float) Double.parseDouble(c[7]), (float) Double.parseDouble(c[8])));
                continue;
            }
            check(a.get("force").getAsBoolean(), "Original force visibility");
            int samples = a.get("sample_count").getAsInt();
            double radius = a.get("radius").getAsDouble();
            var spread = a.getAsJsonObject("spread");
            for (int i = 0; i < samples; i++) {
                double angle = a.has("random_angle") && a.get("random_angle").getAsBoolean()
                        ? random.nextDouble() * Math.PI * 2
                        : Math.PI * 2 * i / samples + age * a.get("rotation_speed").getAsDouble();
                result.add(new Batch(particle(a.get("particle").getAsString()), radius * Math.cos(angle),
                        a.has("offset_y") ? a.get("offset_y").getAsDouble() : 0.5, radius * Math.sin(angle),
                        a.get("count").getAsInt(), (float) (spread.get("x").getAsDouble() * width),
                        (float) (spread.get("y").getAsDouble() * eye), (float) (spread.get("z").getAsDouble() * width),
                        a.get("speed").getAsFloat()));
            }
        }
        return result;
    }

    private static Particle particle(String id) {
        return switch (id) {
            case "minecraft:soul_fire_flame" -> Particle.SOUL_FIRE;
            case "minecraft:flame" -> Particle.FLAME;
            case "minecraft:lava" -> Particle.LAVA;
            default -> throw new AssertionError("Unexpected fixture particle " + id);
        };
    }

    private static void otherRecipes() {
        for (int tick = 0; tick <= 220; tick++) {
            var nova = recipe(Kind.NOVA_CHARGE, tick, 10000 + tick, 100, 0.6f, 1.62f, 5, 12, tick);
            int expectedNova = tick <= 100 && tick % 2 == 0 ? 34 + (int) (tick / 100.0 * 8) + (int) (tick / 100.0 * 10) : 0;
            check(nova.size() == expectedNova, "Nova cadence, density and deadline");
            for (Batch b : nova) if (b.particle() == Particle.LETHAL_DUST || b.particle() == Particle.EDGE_DUST) {
                double radius = b.particle() == Particle.LETHAL_DUST ? 5 : 12;
                check(Math.abs(Math.hypot(b.x(), b.z()) - radius) <= 0.25 + 1e-9, "Nova original radial jitter");
                check(b.y() >= 0.005 && b.y() <= 0.155, "Nova original vertical jitter");
            }
            var storm = recipe(Kind.FROST_STORM, tick, tick, 200, 1, 1, 3.5, 0, tick);
            check(storm.size() == (tick <= 200 ? 8 : 0), "Storm density and deadline");
            for (Batch b : storm) {
                if (b.particle() == Particle.SNOWFLAKE)
                    check(Math.hypot(b.x(), b.z()) <= 3.5 && b.y() >= 0 && b.y() <= 2, "Storm volume preserved");
                else check(Math.abs(Math.hypot(b.x(), b.z()) - 2) < 1e-9 && b.y() == 1, "Storm orbit preserved");
            }
            var seed = recipe(Kind.SEED_FIELD, tick, 10000 + tick, 200, 1, 1, 1, 0, tick);
            int expectedSeed = tick <= 200 ? ((10000 + tick) % 2 == 0 ? 8 : 0) + ((10000 + tick) % 6 == 0 ? 2 : 0) : 0;
            check(seed.size() == expectedSeed, "Seed original world-time cadence");
            for (Batch b : seed) if (b.particle() == Particle.GREEN_DUST)
                check(Math.abs(Math.hypot(b.x(), b.z()) - 1) < 1e-9 && b.y() == 0.15, "Seed orbit preserved");
        }
        check(Kind.RED_RING.range == 512 && Kind.RED_RING.force, "Fire-ring far visibility");
        check(Kind.FROST_STORM.range == 512 && Kind.FROST_STORM.force, "Storm far visibility");
        check(Kind.NOVA_CHARGE.range == 32 && !Kind.NOVA_CHARGE.force, "Nova original particle settings");
        check(Kind.SEED_FIELD.range == 32 && !Kind.SEED_FIELD.force, "Seed original particle settings");
    }

    private static List<Batch> recipe(Kind kind, long age, long now, int duration, float width, float eye,
                                       double radius, double outer, long seed) {
        List<Batch> result = new ArrayList<>();
        VisualRecipe.emit(kind, age, now, duration, width, eye, radius, outer, new Random(seed), result::add);
        return result;
    }

    private static void countdownBoundaries() {
        CountdownValue timer = new CountdownValue(200, 5_000_000, 0, true);
        check(timer.at(5_000_020) == 180, "Server uptime independent countdown");
        check(timer.at(4_999_999) == 200, "Do not predict backwards");
        check(timer.at(Long.MAX_VALUE) == 0, "Long idle clamps to minimum");
        check(new CountdownValue(50, 100, 0, false).at(10000) == 50, "Paused timer stays paused");
        check(timer.agreesWith(new CountdownValue(180, 5_000_020, 0, true)), "Expected decrement needs no packet");
        check(!timer.agreesWith(new CountdownValue(300, 5_000_020, 0, true)), "Reset requires immediate packet");
        check(!timer.agreesWith(new CountdownValue(180, 5_000_020, 0, false)), "Pause requires immediate packet");
        check(!timer.agreesWith(new CountdownValue(0, 5_000_020, 0, false)), "Early clear requires immediate packet");
    }

    private record Delivery(int arrival, CountdownValue clock) {}

    private static void protocolAndSnapshots() {
        var id = java.util.UUID.fromString("729091c4-d5eb-46b1-86d0-3bdc28dff115");
        var key = new CountdownSync.Key(id, new net.minecraft.util.Identifier("my_addon", "form_sp_primary_cd"));
        var state = new CountdownSync.State(key, 391, new CountdownValue(600, 5000000, 0, true));
        var buf = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            state.write(buf);
            check(state.equals(CountdownSync.State.read(buf, 5000000)), "Countdown packet round trip");
            check(!buf.isReadable(), "Countdown packet consumes exactly its fields");
            var next = new CountdownSync.State(key, 391, new CountdownValue(580, 5000020, 0, true));
            check(CountdownSync.sameState(List.of(state), List.of(next)), "Linear CD needs no redundant state update");
            check(!CountdownSync.sameState(List.of(state), List.of()), "Removed CD sends a clearing snapshot");
            check(!CountdownSync.sameState(List.of(), List.of(state)), "New watcher receives current CD");
            check(!CountdownSync.sameState(List.of(state), List.of(new CountdownSync.State(key, 392, state.clock()))),
                    "Respawn entity ID requires a fresh snapshot");
            for (Kind kind : Kind.values()) {
                buf.clear();
                var view = new SustainedVisuals.View(new SustainedVisuals.Key(id, kind), new java.util.UUID(31, 47), 39,
                        new net.minecraft.util.math.Vec3d(-120.5, 64.1, 80.2), 4900000, 200, 0.6f, 1.62f, 3.6, 12);
                view.write(buf);
                check(view.equals(SustainedVisuals.View.read(buf)) && !buf.isReadable(), "Visual packet round trip: " + kind);
                check(!SustainedVisuals.sameState(List.of(view), List.of()), "Visual stop/leave produces empty snapshot");
                check(!SustainedVisuals.sameState(List.of(), List.of(view)), "Late join receives current visual");
            }
        } finally { buf.release(); }
        log("Protocol round trips and snapshot membership/respawn checks passed.");
    }

    private static void simulateCountdowns() {
        long total = 0, good = 0, packets = 0;
        for (int offset = -1; offset <= 1; offset++) for (int scenario = 0; scenario < 40; scenario++) {
            Random random = new Random(98761L + scenario);
            ArrayDeque<Delivery> queue = new ArrayDeque<>();
            CountdownValue serverAnchor = null, clientAnchor = new CountdownValue(0, 0, 0, false);
            int value = 0, sentAt = -100, lastArrival = 0;
            long localGood = 0;
            for (int tick = 0; tick < 10000; tick++) {
                boolean running = tick % 211 >= 7;
                if (tick % 137 == 0) value = 200 + random.nextInt(700); // reset/extend
                if (tick % 293 == 0) value = 0; // cancellation/clear
                if (tick % 97 == 0) value = Math.max(0, value - 40); // sudden reduction
                if (running) value = Math.max(0, value - 1);
                CountdownValue actual = new CountdownValue(value, 5_000_000L + tick, 0, running && value > 0);
                if (serverAnchor == null || !serverAnchor.agreesWith(actual) || tick - sentAt >= 20) {
                    serverAnchor = actual; sentAt = tick;
                    lastArrival = Math.max(lastArrival, tick + random.nextInt(2));
                    queue.add(new Delivery(lastArrival, actual)); packets++;
                }
                while (!queue.isEmpty() && queue.peek().arrival <= tick) clientAnchor = queue.remove().clock;
                if (Math.abs(clientAnchor.at(5_000_000L + tick + offset) - value) <= 1) { good++; localGood++; }
                total++;
            }
            check(localGood >= 9500, "Each countdown latency/clock scenario must meet 95%, not only the aggregate");
        }
        log(String.format(java.util.Locale.ROOT, "Countdown timing: %,d / %,d samples within 1 tick (%.3f%%); %,d anchor packets vs %,d per-tick updates.",
                good, total, 100.0 * good / total, packets, total));
    }

    private record VisualDelivery(int arrival, boolean active, long epoch) {}
    private static void simulateVisuals() {
        long good = 0, total = 0;
        for (int offset = -1; offset <= 1; offset++) for (int scenario = 0; scenario < 40; scenario++) {
            Random random = new Random(scenario);
            ArrayDeque<VisualDelivery> queue = new ArrayDeque<>();
            boolean previous = false, clientActive = false;
            long clientEpoch = 0, localGood = 0;
            int lastArrival = 0;
            for (int tick = 0; tick < 10000; tick++) {
                int age = tick % 320 - 20;
                boolean active = age >= 0 && age < 180;
                long epoch = 5_000_000L + tick - age;
                if (active != previous || active && tick % 20 == 0) {
                    lastArrival = Math.max(lastArrival, tick + random.nextInt(2));
                    queue.add(new VisualDelivery(lastArrival, active, epoch));
                }
                previous = active;
                while (!queue.isEmpty() && queue.peek().arrival <= tick) {
                    var packet = queue.remove(); clientActive = packet.active; clientEpoch = packet.epoch;
                }
                long clientAge = Math.max(0, 5_000_000L + tick + offset - clientEpoch);
                if (clientActive == active && (!active || Math.abs(clientAge - age) <= 1)) { good++; localGood++; }
                total++;
            }
            check(localGood >= 9500, "Each visual lifecycle scenario must meet 95%");
        }
        log(String.format(java.util.Locale.ROOT, "Visual timing including starts/stops: %,d / %,d samples within 1 tick (%.3f%%).", good, total, 100.0 * good / total));
    }

    private static void equivalent(Batch a, Batch b) {
        check(a.particle() == b.particle() && a.count() == b.count(), "Particle type/count preserved");
        double[] x = {a.x(), a.y(), a.z(), a.dx(), a.dy(), a.dz(), a.speed()};
        double[] y = {b.x(), b.y(), b.z(), b.dx(), b.dy(), b.dz(), b.speed()};
        for (int i = 0; i < x.length; i++) check(Math.abs(x[i] - y[i]) < 1e-9, "Original geometry/spread/velocity preserved");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void log(String message) { System.out.println(message); REPORT.append(message).append('\n'); }
}
