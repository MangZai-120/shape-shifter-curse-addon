package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.Gson;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyManager;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.NeoState;

/** Observe real incoming packets and the real sound engine for all seven elements and 42 pair orders. */
public final class AnomalyFeedbackClientProbe {
    private static final Map<String, String> BASE_SOUNDS = Map.of("fire", "entity.blaze.shoot", "ice", "block.glass.break",
            "lunar", "block.amethyst_block.chime", "curse", "entity.evoker.cast_spell",
            "summon", "entity.evoker.prepare_summon", "void", "block.sculk_catalyst.bloom", "space", "entity.enderman.teleport");
    private static final Map<String, String> REACTION_SOUNDS = Map.ofEntries(
            Map.entry("fire+ice", "block.fire.extinguish"), Map.entry("fire+lunar", "block.amethyst_block.resonate"),
            Map.entry("curse+fire", "entity.blaze.burn"), Map.entry("fire+summon", "entity.vex.charge"),
            Map.entry("fire+void", "block.respawn_anchor.deplete"), Map.entry("fire+space", "block.respawn_anchor.charge"),
            Map.entry("ice+lunar", "block.amethyst_cluster.break"), Map.entry("curse+ice", "entity.illusioner.prepare_blindness"),
            Map.entry("ice+summon", "item.trident.throw"), Map.entry("ice+void", "block.amethyst_block.break"),
            Map.entry("ice+space", "block.chain.place"), Map.entry("curse+lunar", "block.beacon.deactivate"),
            Map.entry("lunar+summon", "entity.allay.item_given"), Map.entry("lunar+void", "block.sculk.charge"),
            Map.entry("lunar+space", "block.conduit.activate"), Map.entry("curse+summon", "entity.evoker.prepare_attack"),
            Map.entry("curse+void", "entity.wither.hurt"), Map.entry("curse+space", "block.end_portal_frame.fill"),
            Map.entry("summon+void", "particle.soul_escape"), Map.entry("space+summon", "block.enchantment_table.use"),
            Map.entry("space+void", "entity.illusioner.mirror_move"));
    private static final String[] ELEMENTS = {"fire", "ice", "lunar", "curse", "summon", "void", "space"};
    private static final Map<String, String> PARTICLES = Map.of("fire", "flame", "ice", "snowflake",
            "lunar", "end_rod", "curse", "witch", "summon", "happy_villager",
            "void", "reverse_portal", "space", "portal");
    private static final Set<String> CUE_SOUNDS = java.util.stream.Stream.concat(BASE_SOUNDS.values().stream(),
            REACTION_SOUNDS.values().stream()).map(id -> "minecraft:" + id).collect(java.util.stream.Collectors.toSet());
    private static final List<Scenario> SCENARIOS = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<Map<String, Object>> PARTICLE_PACKETS = new CopyOnWriteArrayList<>();
    private static final List<String> SOUND_PACKETS = new CopyOnWriteArrayList<>();
    private static final List<String> PLAYED_SOUNDS = new CopyOnWriteArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static volatile boolean finished, observerReady, awaitingObservation, observed;
    private static volatile Throwable failure;
    private static volatile int scenarioIndex = -1, checks;
    private static volatile UUID targetId;
    private static volatile int targetEntityId;
    private static ServerPlayerEntity caster;
    private static PigEntity target;
    private static int serverTicks, observationTicks;
    private static boolean starting;

    private record Scenario(String first, String second) {}

    private AnomalyFeedbackClientProbe() {}

    public static void install() {
        SCENARIOS.add(new Scenario("fire", "partial"));
        for (String element : ELEMENTS) SCENARIOS.add(new Scenario(element, ""));
        for (int a = 0; a < ELEMENTS.length; a++) for (int b = a + 1; b < ELEMENTS.length; b++) {
            SCENARIOS.add(new Scenario(ELEMENTS[a], ELEMENTS[b]));
            SCENARIOS.add(new Scenario(ELEMENTS[b], ELEMENTS[a]));
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server);
            state.tutorialPolicyInitialized = true;
            state.tutorialAutoEnabled = false;
            state.markDirty();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                if (++serverTicks == 40) {
                    caster = server.getPlayerManager().getPlayerList().get(0);
                    var origin = caster.getPos();
                    var world = caster.getServerWorld();
                    server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    world.setTimeOfDay(6000);
                    target = EntityType.PIG.create(world);
                    check(target != null, "Create a real target");
                    target.setAiDisabled(true); target.setNoGravity(true); target.setSilent(true);
                    target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
                    target.refreshPositionAndAngles(origin.x, origin.y, origin.z + 3, 180, 0);
                    check(world.spawnEntity(target), "Spawn a real tracked target");
                    targetId = target.getUuid(); targetEntityId = target.getId();
                    caster.getAbilities().flying = true; caster.getAbilities().allowFlying = true;
                    caster.sendAbilitiesUpdate();
                    caster.teleport(world, origin.x, origin.y + 2, origin.z - 4, 0, 18);
                }
                if (!observerReady || target == null || awaitingObservation && !observed) return;
                if (scenarioIndex + 1 >= SCENARIOS.size()) return;
                AnomalyManager.clearAll(); target.clearStatusEffects();
                target.setHealth(1000); target.timeUntilRegen = 0;
                PARTICLE_PACKETS.clear(); SOUND_PACKETS.clear(); PLAYED_SOUNDS.clear();
                observed = false;
                Scenario scene = SCENARIOS.get(++scenarioIndex);
                if (scene.second.equals("partial")) {
                    hit(scene.first, 250);
                    check(AnomalyManager.snapshot(target).values().get(scene.first) == 250,
                            "Partial buildup remains below the threshold");
                } else {
                    hit(scene.first, 500);
                    if (!scene.second.isEmpty()) {
                        long reactions = AnomalyManager.metrics().reactions();
                        hit(scene.second, 500);
                        check(AnomalyManager.metrics().reactions() == reactions + 1,
                                "Same-tick second element triggers a reaction");
                    }
                    long triggers = AnomalyManager.metrics().triggers();
                    hit(scene.first, 500);
                    if (!scene.second.isEmpty()) hit(scene.second, 500);
                    check(AnomalyManager.metrics().triggers() == triggers, "Held elements cannot retrigger cues");
                }
                awaitingObservation = true;
            } catch (Throwable error) { fail(error); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 240_000_000_000L) throw new AssertionError("Feedback client timed out");
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true;
                    Files.deleteIfExists(Path.of("anomaly-feedback-client-result.json"));
                    client.options.pauseOnLostFocus = false;
                    if (!Boolean.getBoolean("ssca.audit.nativeEnvironment")) {
                        client.options.getMaxFps().setValue(60); client.options.getViewDistance().setValue(4);
                    }
                    client.getSoundManager().registerListener((sound, set) -> {
                        String id = sound.getId().toString();
                        if (CUE_SOUNDS.contains(id)) PLAYED_SOUNDS.add(id);
                    });
                    String name = "Anomaly-Feedback-" + System.currentTimeMillis();
                    client.createIntegratedServerLoader().createAndStart(name,
                            new LevelInfo(name, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                                    new GameRules(), DataConfiguration.SAFE_MODE),
                            new GeneratorOptions(20261107L, false, false),
                            registry -> registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (client.world == null || client.player == null || targetId == null) return;
                if (client.currentScreen != null) client.setScreen(null);
                if (!observerReady) {
                    if (client.world.getEntityById(targetEntityId) == null) return;
                    var field = java.util.Arrays.stream(ClientConnection.class.getDeclaredFields())
                            .filter(candidate -> candidate.getType() == Channel.class).findFirst().orElseThrow();
                    field.setAccessible(true);
                    Channel channel = (Channel) field.get(client.getNetworkHandler().getConnection());
                    channel.eventLoop().submit(() -> channel.pipeline().addBefore("packet_handler", "ssca_feedback_observer",
                            new ChannelInboundHandlerAdapter() {
                                @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                                    if (message instanceof ParticleS2CPacket packet && (packet.getCount() == 24 || packet.getCount() == 16))
                                        PARTICLE_PACKETS.add(Map.of("type", Registries.PARTICLE_TYPE.getId(packet.getParameters().getType()).toString(),
                                                "count", packet.getCount(), "forced", packet.isLongDistance()));
                                    if (message instanceof PlaySoundS2CPacket packet) {
                                        String id = packet.getSound().value().getId().toString();
                                        if (CUE_SOUNDS.contains(id)) SOUND_PACKETS.add(id);
                                    }
                                    super.channelRead(ctx, message);
                                }
                            })).sync();
                    observerReady = true;
                }
                if (!awaitingObservation || observed) { observationTicks = 0; return; }
                if (++observationTicks < 12) return;
                Scenario scene = SCENARIOS.get(scenarioIndex);
                boolean partial = scene.second.equals("partial"), reaction = !scene.second.isEmpty() && !partial;
                check(PARTICLE_PACKETS.size() == (partial ? 0 : reaction ? 3 : 1),
                        "One single burst, or first burst plus two reaction bursts: " + scene + " " + PARTICLE_PACKETS);
                if (!partial) {
                    String baseSound = "minecraft:" + BASE_SOUNDS.get(scene.first);
                    check(PARTICLE_PACKETS.get(0).get("type").equals("minecraft:" + PARTICLES.get(scene.first)),
                            "First element uses its own particle");
                    check(!SOUND_PACKETS.isEmpty() && SOUND_PACKETS.get(0).equals(baseSound),
                            "A single anomaly sends its element's exact trigger sound");
                    check(PLAYED_SOUNDS.contains(baseSound), "The actual client sound engine plays the element's cue");
                    // AsyncParticles can move live particles into GPU buffers; its CPU queue count can be zero.
                    // Native runs retain exact particle-packet checks and screenshots for visual inspection.
                    if (!(Boolean.getBoolean("ssca.audit.nativeEnvironment")
                            && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("asyncparticles")))
                        check(Integer.parseInt(client.particleManager.getDebugString()) > 0,
                                "The vanilla particle manager contains real client particles");
                }
                if (reaction) {
                    var pair = Set.of("minecraft:" + PARTICLES.get(scene.first), "minecraft:" + PARTICLES.get(scene.second));
                    var actual = new java.util.HashSet<String>();
                    for (int i = 1; i < PARTICLE_PACKETS.size(); i++) {
                        var packet = PARTICLE_PACKETS.get(i);
                        check(packet.get("count").equals(16), "Reaction burst is separate from the standalone burst");
                        actual.add((String) packet.get("type"));
                    }
                    check(actual.equals(pair), "Both corresponding elements arrive in the reaction cue");
                    String key = scene.first.compareTo(scene.second) < 0 ? scene.first + "+" + scene.second : scene.second + "+" + scene.first;
                    String reactionSound = "minecraft:" + REACTION_SOUNDS.get(key);
                    check(!reactionSound.equals("minecraft:" + BASE_SOUNDS.get(scene.first))
                                    && !reactionSound.equals("minecraft:" + BASE_SOUNDS.get(scene.second)),
                            "A reaction cue differs from both constituent base cues");
                    check(SOUND_PACKETS.equals(List.of("minecraft:" + BASE_SOUNDS.get(scene.first), reactionSound)),
                            "The immediate reaction uses its exact pair cue without the second standalone cue");
                    check(PLAYED_SOUNDS.contains(SOUND_PACKETS.get(1)), "The actual sound engine plays the distinct reaction cue");
                } else check(SOUND_PACKETS.size() == (partial ? 0 : 1), "Partial and single triggers do not play a reaction sound");
                for (var packet : PARTICLE_PACKETS) check(packet.get("forced").equals(true), "Cue packets force visible particles");
                RESULTS.add(Map.of("first", scene.first, "second", scene.second, "particles", List.copyOf(PARTICLE_PACKETS),
                        "sound_packets", List.copyOf(SOUND_PACKETS), "played_sounds", List.copyOf(PLAYED_SOUNDS)));
                if (scenarioIndex == 1 || scenarioIndex == 8) {
                    String name = "anomaly-feedback-" + (reaction ? "reaction" : "single") + ".png";
                    try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(Path.of(name)); }
                    SHOTS.add(name);
                }
                System.out.println("ANOMALY_FEEDBACK_SCENARIO_PASS " + scenarioIndex + " " + scene);
                if (scenarioIndex + 1 == SCENARIOS.size()) {
                    Files.writeString(Path.of("anomaly-feedback-client-result.json"), new Gson().toJson(Map.of(
                            "passed", true, "checks", checks, "single_elements", 7, "reaction_orders", 42,
                            "partial_buildup_tested", true, "immediate_reaction_tested", true,
                            "locked_retrigger_tested", true, "scenarios", RESULTS, "screenshots", SHOTS,
                            "particle_rendering_validation", Boolean.getBoolean("ssca.audit.nativeEnvironment")
                                    ? "Exact particle packets and manual screenshot inspection; GPU queues are not a live render count"
                                    : "Vanilla particle manager count and screenshots")));
                    finished = true;
                    System.out.println("ANOMALY_FEEDBACK_CLIENT_PASS checks=" + checks);
                    client.scheduleStop();
                } else { observationTicks = 0; observed = true; }
            } catch (Throwable error) { fail(error); }
        });
    }

    private static void hit(String element, double amount) {
        AnomalyManager.enqueue(new SpellAnomalyContext(new Identifier("ssc_addon", "feedback_probe"), element,
                1, amount, caster.getUuid(), UUID.randomUUID()), target);
        AnomalyManager.drain(caster.getServer());
    }

    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    private static void fail(Throwable error) {
        if (failure != null) return;
        failure = error; finished = true; error.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Anomaly feedback acceptance failed", failure);
        if (!finished || !Files.isRegularFile(Path.of("anomaly-feedback-client-result.json")))
            throw new AssertionError("Feedback client closed before completing acceptance");
    }
}
