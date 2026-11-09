package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Category;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/** Matched real server-world idle/activity comparison; it makes no renderer or FPS claim. */
public final class AnomalyPerformanceWorldProbe {
    private static final int TARGETS = 64;
    private static final int HITS_PER_TICK = 16;
    private static final int INITIAL_WARMUP = 80;
    private static final int PHASE_TICKS = 100;
    private static final int PHASE_WARMUP = 20;
    private static final String AMOUNT_MODE = System.getProperty("ssca.anomaly.performance.amounts", "stress250");
    private static final int SPELL_LEVEL = Math.max(1, Math.min(5, Integer.getInteger("ssca.anomaly.performance.level", 1)));
    private static final boolean PROFILE = Boolean.parseBoolean(System.getProperty("ssca.anomaly.performance.jfr", "true"));
    private static final String[] ELEMENTS = {"fire", "ice", "lunar", "curse", "summon", "void", "space"};
    private static final String[] CONFIGURED_SPELLS = {"fire_bolt", "frost_spike", "moonlight_arrow", "curse_mark",
            "summon_lunar_spirit_bolt", "void_devour", "domain_pulse"};
    private static final String[] LABELS = {"idle_off_1", "idle_on_1", "idle_on_2", "idle_off_2",
            "mixed_off_1", "mixed_on_1", "mixed_on_2", "mixed_off_2"};
    private static final List<PigEntity> FIXTURES = new ArrayList<>();
    private static final Vec3d[] POSITIONS = new Vec3d[TARGETS];
    private static final SpellAnomalyContext[][] CONTEXTS = new SpellAnomalyContext[TARGETS][ELEMENTS.length];
    private static final AnomalyProbeSupport.Checks CHECK = new AnomalyProbeSupport.Checks();
    private static final AnomalyProbeSupport.Samples[] ALL = new AnomalyProbeSupport.Samples[LABELS.length];
    private static final AnomalyProbeSupport.Samples[] STEADY = new AnomalyProbeSupport.Samples[LABELS.length];
    private static final List<Map<String, Object>> DETAILS = new ArrayList<>();
    private static TestContext test;
    private static ServerPlayerEntity caster;
    private static AnomalyProbeSupport.AllocationCounter allocations;
    private static long serverThreadId, startNanos, startBytes, phaseQueries, phaseDamageCalls;
    private static int age, phase, localTick, acceptedHits;
    private static boolean complete, previousEnabled;
    private static Recording recording;
    private static PhaseEvent phaseEvent;

    @Name("ssc_addon.AnomalyPerformancePhase")
    @Label("SSCA anomaly performance phase")
    @Category({"SSCA", "Anomaly"})
    static final class PhaseEvent extends Event {
        @Label("Phase") public String label;
        @Label("Runtime enabled") public boolean enabled;
        @Label("Activity") public boolean activity;
        @Label("Amount mode") public String amountMode;
        @Label("Sequence") public int sequence;
    }

    private AnomalyPerformanceWorldProbe() {}

    public static void start(TestContext context) {
        test = context;
        AnomalyProbeSupport.later(context, 70, AnomalyPerformanceWorldProbe::install);
    }

    private static void install() {
        complete = false;
        age = -1;
        phase = -1;
        acceptedHits = 0;
        recording = null;
        phaseEvent = null;
        CHECK.count = 0;
        FIXTURES.clear();
        DETAILS.clear();
        allocations = new AnomalyProbeSupport.AllocationCounter();
        serverThreadId = Thread.currentThread().getId();
        previousEnabled = AnomalyManager.enabled();
        AnomalyManager.clearAll();
        AnomalyManager.setEnabledForTesting(true);
        var center = test.getAbsolutePos(new BlockPos(4, 35, 4)).toCenterPos();
        test.getWorld().getServer().getGameRules().get(GameRules.MAX_ENTITY_CRAMMING)
                .set(0, test.getWorld().getServer());
        caster = test.createMockCreativeServerPlayerInWorld();
        caster.setNoGravity(true);
        caster.getAbilities().invulnerable = true;
        caster.refreshPositionAndAngles(center.x + 14, center.y, center.z, 0, 0);
        for (int i = 0; i < TARGETS; i++) {
            POSITIONS[i] = center.add((i % 8) * .65, 0, (i / 8) * .65);
            FIXTURES.add(AnomalyProbeSupport.pig(test, POSITIONS[i], 1000));
            for (int element = 0; element < ELEMENTS.length; element++)
                CONTEXTS[i][element] = new SpellAnomalyContext(
                        new Identifier("ssc_addon", "configured".equals(AMOUNT_MODE)
                                ? CONFIGURED_SPELLS[element] : "performance_" + ELEMENTS[element]), ELEMENTS[element],
                        "configured".equals(AMOUNT_MODE) ? SPELL_LEVEL : 5,
                        contribution(element), caster.getUuid(), UUID.randomUUID());
        }
        for (int i = 0; i < LABELS.length; i++) {
            ALL[i] = new AnomalyProbeSupport.Samples(PHASE_TICKS);
            STEADY[i] = new AnomalyProbeSupport.Samples(PHASE_TICKS - PHASE_WARMUP);
        }
        ServerTickEvents.START_SERVER_TICK.register(AnomalyPerformanceWorldProbe::beginTick);
        ServerTickEvents.END_SERVER_TICK.register(AnomalyPerformanceWorldProbe::endTick);
    }

    private static boolean isEnabled(int stage) {
        int relative = stage % 4;
        return relative == 1 || relative == 2;
    }

    private static void changePhase(int next) {
        if (PROFILE && recording == null) startRecording();
        AnomalyManager.clearAll();
        AnomalyManager.setEnabledForTesting(isEnabled(next));
        AnomalyManager.resetMetrics();
        phaseQueries = AnomalyEffects.diagnostics().localQueries();
        phaseDamageCalls = AnomalyEffects.diagnostics().reactionDamageCalls();
        phase = next;
        if (PROFILE) {
            phaseEvent = new PhaseEvent();
            phaseEvent.label = LABELS[phase];
            phaseEvent.enabled = isEnabled(phase);
            phaseEvent.activity = phase >= 4;
            phaseEvent.amountMode = AMOUNT_MODE;
            phaseEvent.sequence = phase;
            phaseEvent.begin();
        }
        System.out.println("ANOMALY_PERF_PHASE " + LABELS[phase]);
    }

    private static double contribution(int element) {
        return switch (AMOUNT_MODE) {
            case "stress250", "stress" -> 250;
            case "full500" -> 500;
            case "configured" -> AnomalyRules.syntheticAmount(CONFIGURED_SPELLS[element], SPELL_LEVEL);
            default -> throw new IllegalArgumentException("Unknown anomaly performance amount mode: " + AMOUNT_MODE);
        };
    }

    private static void startRecording() {
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("SSCA anomaly server comparison");
            recording.enable("jdk.ObjectAllocationSample").withStackTrace();
            recording.enable("jdk.ObjectAllocationInNewTLAB").withStackTrace();
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace();
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            recording.enable(PhaseEvent.class);
            // Profile startup happens before any timed phase and before its twenty warmup ticks.
            recording.start();
        } catch (Exception failure) {
            if (recording != null) recording.close();
            recording = null;
            throw new IllegalStateException("Cannot start anomaly performance recording", failure);
        }
    }

    private static void beginTick(MinecraftServer server) {
        if (complete || server != test.getWorld().getServer()) return;
        try {
            age++;
            if (age >= INITIAL_WARMUP) {
                int next = (age - INITIAL_WARMUP) / PHASE_TICKS;
                if (next >= LABELS.length) { finish(); return; }
                if (next != phase) changePhase(next);
                localTick = (age - INITIAL_WARMUP) % PHASE_TICKS;
            }
            // Capture before the same fixture and base-hit schedule used in every mode.
            startBytes = allocations.current(serverThreadId);
            startNanos = System.nanoTime();
            for (int i = 0; i < TARGETS; i++) {
                var target = FIXTURES.get(i);
                if (!target.isAlive() || target.isRemoved())
                    throw new AssertionError("The same target must remain alive in every comparison phase");
                target.setHealth(target.getMaxHealth());
                target.setVelocity(Vec3d.ZERO);
                if (!target.getPos().equals(POSITIONS[i])) target.setPosition(POSITIONS[i]);
            }
            if (age < INITIAL_WARMUP || phase >= 4) ordinaryHits();
        } catch (Throwable failure) { fail(failure); }
    }

    private static void ordinaryHits() {
        int frame = age < INITIAL_WARMUP ? age : localTick;
        int first = frame * HITS_PER_TICK % TARGETS;
        for (int hit = 0; hit < HITS_PER_TICK; hit++) {
            int index = (first + hit) % TARGETS;
            var target = FIXTURES.get(index);
            int element = Math.floorMod(index + frame / 8, ELEMENTS.length);
            target.timeUntilRegen = 0;
            if (SpellHitHelper.hitRaw(caster, target, 1, 0, CONTEXTS[index][element])
                    != SpellHitHelper.HitResult.HIT)
                throw new AssertionError("The matched underlying spell hit was rejected");
            acceptedHits++;
        }
    }

    private static void endTick(MinecraftServer server) {
        if (complete || phase < 0 || server != test.getWorld().getServer()) return;
        try {
            long nanos = System.nanoTime() - startNanos;
            long bytes = AnomalyProbeSupport.AllocationCounter.difference(startBytes,
                    allocations.current(serverThreadId));
            ALL[phase].add(nanos, bytes);
            if (localTick >= PHASE_WARMUP) STEADY[phase].add(nanos, bytes);
            if (localTick == PHASE_TICKS - 1) {
                if (phaseEvent != null) { phaseEvent.end(); phaseEvent.commit(); phaseEvent = null; }
                phaseSummary();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void phaseSummary() {
        var metrics = AnomalyManager.metrics();
        var diagnostics = AnomalyEffects.diagnostics();
        var row = new LinkedHashMap<String, Object>();
        row.put("label", LABELS[phase]);
        row.put("enabled", isEnabled(phase));
        row.put("ordinary_hits", phase < 4 ? 0 : HITS_PER_TICK * PHASE_TICKS);
        row.put("all_ticks", ALL[phase].statistics());
        row.put("steady_ticks", STEADY[phase].statistics());
        row.put("enqueued", metrics.enqueued());
        row.put("processed", metrics.processed());
        row.put("triggers", metrics.triggers());
        row.put("reactions", metrics.reactions());
        row.put("packets", metrics.packets());
        row.put("local_queries", diagnostics.localQueries() - phaseQueries);
        row.put("reaction_damage_calls", diagnostics.reactionDamageCalls() - phaseDamageCalls);
        row.put("tracked_targets", metrics.tracked());
        row.put("pending", metrics.pending());
        DETAILS.add(row);
        CHECK.that(metrics.pending() == 0, "A completed server tick never retains unprocessed hit events");
        if (phase < 4 || !isEnabled(phase)) {
            CHECK.that(metrics.enqueued() == 0 && metrics.triggers() == 0 && metrics.reactions() == 0,
                    "Idle/off modes do not perform hidden anomaly work");
            CHECK.that(diagnostics.localQueries() == phaseQueries,
                    "Idle/off modes never issue area entity queries");
        } else {
            CHECK.that(metrics.enqueued() == HITS_PER_TICK * PHASE_TICKS
                            && metrics.processed() == metrics.enqueued(),
                    "Active runtime processes every matched base hit without dropping or limiting events");
            if (!"configured".equals(AMOUNT_MODE)) {
                CHECK.that(metrics.triggers() > 0 && metrics.reactions() > 0,
                        "Active stress measurements include genuine base effects and cross-element reactions");
                CHECK.that(diagnostics.localQueries() > phaseQueries
                                && diagnostics.reactionDamageCalls() > phaseDamageCalls,
                        "Active stress measurements include actual local reaction areas and extra damage");
            }
        }
    }

    private static void finish() {
        complete = true;
        try {
            CHECK.that(DETAILS.size() == 8, "All eight interleaved comparison phases completed");
            CHECK.that(acceptedHits == HITS_PER_TICK * (INITIAL_WARMUP + 4 * PHASE_TICKS),
                    "The same scheduled underlying damage was executed in both active comparison modes");
            var groups = new LinkedHashMap<String, AnomalyProbeSupport.Samples>();
            var steady = new LinkedHashMap<String, AnomalyProbeSupport.Samples>();
            for (String name : List.of("idle_off", "idle_on", "mixed_off", "mixed_on")) {
                groups.put(name, new AnomalyProbeSupport.Samples(PHASE_TICKS * 2));
                steady.put(name, new AnomalyProbeSupport.Samples((PHASE_TICKS - PHASE_WARMUP) * 2));
            }
            for (int i = 0; i < LABELS.length; i++) {
                String name = (i < 4 ? "idle_" : "mixed_") + (isEnabled(i) ? "on" : "off");
                for (int sample = 0; sample < ALL[i].size; sample++)
                    groups.get(name).add(ALL[i].nanos[sample], ALL[i].bytes[sample]);
                for (int sample = 0; sample < STEADY[i].size; sample++)
                    steady.get(name).add(STEADY[i].nanos[sample], STEADY[i].bytes[sample]);
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("kind", "real-server-world-matched-anomaly-runtime-off-on");
            result.put("measurement_scope", "START_SERVER_TICK to END_SERVER_TICK, excludes scheduler sleep and earlier start-event callbacks");
            result.put("targets", TARGETS);
            result.put("amount_mode", AMOUNT_MODE);
            result.put("configured_spell_level", SPELL_LEVEL);
            var amounts = new LinkedHashMap<String, Double>();
            for (int i = 0; i < ELEMENTS.length; i++) amounts.put(ELEMENTS[i], contribution(i));
            result.put("per_hit_contributions", amounts);
            result.put("base_hits_per_active_tick", HITS_PER_TICK);
            result.put("accepted_base_hits_including_warmup", acceptedHits);
            result.put("initial_activity_warmup_ticks", INITIAL_WARMUP);
            result.put("checks", CHECK.count);
            result.put("phases", DETAILS);
            var combined = new LinkedHashMap<String, Object>();
            groups.forEach((name, samples) -> combined.put(name,
                    Map.of("all_ticks", samples.statistics(), "steady_ticks", steady.get(name).statistics())));
            result.put("combined", combined);
            result.put("client_fps_or_real_network_measurement", false);
            result.put("jfr_profile_enabled", PROFILE);
            if (PROFILE) result.put("jfr", "anomaly-performance-world.jfr");
            result.put("notes", List.of("Runtime off keeps the existing spell-hit transport and underlying damage enabled.",
                    "The same 64 real targets, health/position resets, base damage and hit schedule are used in every mode.",
                    "Active on includes genuine effects, reactions, periodic local queries and normal effect updates.",
                    "All-tick summaries include the initial target-state allocations; steady summaries exclude each phase's first twenty ticks.",
                    "The GameTest mock has no negotiated anomaly client channel; packet delivery latency and client rendering are not measured."));
            AnomalyProbeSupport.report("anomaly-performance-world-result.json", result);
            closeRecording(false);
            System.out.println("ANOMALY_PERFORMANCE_WORLD_PASS " + net.jackcooper.shapeShifterCurseAddon.spell.NeoState.JSON.toJson(result));
            cleanup();
            test.complete();
        } catch (Throwable failure) { fail(failure); }
    }

    private static void fail(Throwable failure) {
        complete = true;
        closeRecording(true);
        cleanup();
        failure.printStackTrace();
        test.throwGameTestException(failure.toString());
    }

    private static void cleanup() {
        AnomalyManager.clearAll();
        for (PigEntity target : FIXTURES) target.discard();
        FIXTURES.clear();
        AnomalyManager.setEnabledForTesting(previousEnabled);
    }

    private static void closeRecording(boolean failed) {
        if (recording == null) return;
        try {
            if (phaseEvent != null) { phaseEvent.end(); phaseEvent.commit(); phaseEvent = null; }
            if (recording.getState() == RecordingState.RUNNING) recording.stop();
            recording.dump(Path.of(failed ? "anomaly-performance-world-failed.jfr" : "anomaly-performance-world.jfr"));
        } catch (Exception failure) {
            if (!failed) throw new IllegalStateException("Cannot save anomaly performance recording", failure);
            failure.printStackTrace();
        } finally {
            recording.close();
            recording = null;
        }
    }
}
