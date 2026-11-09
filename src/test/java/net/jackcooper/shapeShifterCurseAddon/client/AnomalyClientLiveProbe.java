package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.client.render.FrozenRenderScratchProbe;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;

import com.google.gson.JsonParser;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffects;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyManager;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyNetwork;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyRules;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalySnapshot;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real integrated client, real tracked targets and full packet receivers; no mocks or render disabling. */
public final class AnomalyClientLiveProbe {
    private static final int TARGETS = 32;
    private static final int PERF_START = 340;
    private static final int PHASE_TICKS = 80;
    private static final int WARMUP_TICKS = 20;
    private static final String[] SCENES = {"idle_off_1", "idle_on_1", "idle_on_2", "idle_off_2",
            "active_off_1", "active_on_1", "active_on_2", "active_off_2"};
    private static final AtomicInteger CHECKS = new AtomicInteger();
    private static final List<PigEntity> PIGS = new ArrayList<>();
    private static final List<Vec3d> POSITIONS = new ArrayList<>();
    private static final List<List<Double>> FRAMES = new ArrayList<>();
    private static final List<List<Double>> SERVER_TICKS = new ArrayList<>();
    private static final long[] RENDER_ALLOC_FIRST = new long[SCENES.length], RENDER_ALLOC_LAST = new long[SCENES.length];
    private static final long[] SERVER_ALLOC = new long[SCENES.length];
    private static final List<Map<String, Object>> PHASE_EVENTS = new ArrayList<>();
    private static final Set<String> SHOTS = new HashSet<>();
    private static final Set<Integer> CLIENT_STAGES = new HashSet<>();
    private static final com.sun.management.ThreadMXBean ALLOCATIONS = allocationBean();
    private static volatile int serverTicks, stage, perfScene = -1, perfAge;
    private static volatile UUID actualPlayer, primaryId, secondaryId;
    private static volatile int primaryEntityId, secondaryEntityId;
    private static volatile boolean worldReady, finished, dimensionSeen, configSeen, stateSeen;
    private static volatile boolean disconnected, disconnectRequested;
    private static volatile Throwable failure;
    private static volatile double expectedConfig, sentinelConfig;
    private static volatile AnomalySnapshot expected;
    private static volatile int clientObservedStage;
    private static ServerPlayerEntity player;
    private static Vec3d camera, origin;
    private static PigEntity primary, secondary;
    private static SpellAnomalyContext ice1, ice5, fire1, fire5;
    private static boolean starting, hooksInstalled;
    private static int clientTicks, clientRenderTicks, lastFrameScene = -1, disconnectTicks, dimensionTicks, renderScratchChecks;
    private static long lastFrame, tickStart, tickAllocated;
    private static LivingEntity clientPrimary, clientSecondary;
    private static String originalClientConfig;
    private static int pausePhase, pauseStartedAt, pauseStableTicks, pauseObservedTicks, pauseResumeTicks, pauseLastServerTick;
    private static int pauseServerTick, pauseVanillaFrozenTicks;
    private static long pauseWorldTime;
    private static boolean pauseVerified;
    private static AnomalySnapshot pauseBaseline;

    private AnomalyClientLiveProbe() {}

    public static void install() {
        for (String ignored : SCENES) {
            FRAMES.add(new ArrayList<>()); SERVER_TICKS.add(new ArrayList<>());
        }
        Arrays.fill(RENDER_ALLOC_FIRST, -1); Arrays.fill(RENDER_ALLOC_LAST, -1);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { clientTick(client); } catch (Throwable error) { fail(error); }
        });
        WorldRenderEvents.START.register(context -> {
            long now = System.nanoTime(); int scene = perfScene;
            if (scene >= 0 && scene < SCENES.length && perfAge >= WARMUP_TICKS && perfAge < PHASE_TICKS) {
                long allocated = allocated();
                if (RENDER_ALLOC_FIRST[scene] < 0) RENDER_ALLOC_FIRST[scene] = allocated;
                RENDER_ALLOC_LAST[scene] = allocated;
                if (lastFrame != 0 && lastFrameScene == scene) FRAMES.get(scene).add((now - lastFrame) / 1e6);
                lastFrameScene = scene; lastFrame = now;
            } else { lastFrame = 0; lastFrameScene = -1; }
            clientRenderTicks++;
        });
    }

    private static void installServerHooks() {
        if (hooksInstalled) return;
        hooksInstalled = true;
        // Register at the actual title screen, after mod initialization. END therefore includes production handlers.
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            tickStart = System.nanoTime(); tickAllocated = allocated();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { serverTick(server); } catch (Throwable error) { fail(error); }
        });
    }

    private static void clientTick(MinecraftClient client) throws Exception {
        clientTicks++;
        if (failure != null || finished) return;
        if (disconnectRequested) {
            if (client.world == null && ++disconnectTicks >= 4) {
                check(AnomalyClient.trackedCount() == 0, "Real disconnect clears the client anomaly mirror");
                disconnected = true; finish(client);
            }
            return;
        }
        if (client.getOverlay() != null) return;
        if (!starting && client.currentScreen instanceof TitleScreen) {
            starting = true; installServerHooks();
            Files.deleteIfExists(Path.of("anomaly-client-live-result.json"));
            client.options.pauseOnLostFocus = false;
            client.options.getViewDistance().setValue(6);
            client.options.getEnableVsync().setValue(false);
            client.options.getMaxFps().setValue(260);
            client.options.language = "zh_cn";
            originalClientConfig = AnomalyRules.snapshotJson();
            var sentinel = JsonParser.parseString(originalClientConfig).getAsJsonObject();
            var levels = sentinel.getAsJsonObject("spells").getAsJsonArray("ssc_addon:fire_bolt");
            expectedConfig = levels.get(0).getAsDouble();
            sentinelConfig = expectedConfig == 499 ? 498 : expectedConfig + 1;
            levels.set(0, new com.google.gson.JsonPrimitive(sentinelConfig));
            AnomalyRules.acceptClientSnapshot(sentinel.toString());
            check(AnomalyRules.clientAmount("fire_bolt", 1) == sentinelConfig,
                    "Only the client mirror is seeded with a distinguishable pre-connection config");
            String name = "Anomaly-Client-Acceptance-" + System.currentTimeMillis();
            client.createIntegratedServerLoader().createAndStart(name,
                    new LevelInfo(name, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(20261103L, false, false),
                    registry -> registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if (client.world == null || client.player == null) return;
        if (pauseTick(client)) return;
        if (client.currentScreen != null) client.setScreen(null);
        if (clientRenderTicks > 20) {
            actualPlayer = client.player.getUuid(); worldReady = true;
        }
        if (serverTicks > 1190) throw new AssertionError("Client acceptance exceeded the 1200-tick budget");
        if (!configSeen && AnomalyRules.clientAmount("fire_bolt", 1) == expectedConfig
                && expectedConfig != sentinelConfig) {
            configSeen = true;
            check(client.getNetworkHandler() != null, "The actual CONFIG receiver replaced the seeded client config");
        }
        if (client.world.getRegistryKey() == World.NETHER && stage >= 100) {
            // This probe registers before mod initialization; let the production END tick observer see the new world.
            if (++dimensionTicks < 3) return;
            check(AnomalyClient.trackedCount() == 0, "Changing the real client dimension clears old target mirrors");
            check(AnomalyRules.clientAmount("fire_bolt", 1) == expectedConfig,
                    "Changing dimensions retains the connected server's config");
            dimensionSeen = true;
            disconnectRequested = true;
            client.world.disconnect();
            client.disconnect(); client.setScreen(new TitleScreen());
            return;
        }
        if (primaryId != null) {
            Entity current = client.world.getEntityById(primaryEntityId);
            if (current instanceof LivingEntity living && current.getUuid().equals(primaryId)) clientPrimary = living;
        }
        if (secondaryId != null) {
            Entity current = client.world.getEntityById(secondaryEntityId);
            if (current instanceof LivingEntity living && current.getUuid().equals(secondaryId)) clientSecondary = living;
        }
        int currentStage = stage;
        if (currentStage > 0 && currentStage < 10 && !CLIENT_STAGES.contains(currentStage))
            observeStage(client, currentStage);
        if (perfScene >= 0 && perfScene < SCENES.length) {
            client.player.setYaw(0); client.player.setPitch(22);
        }
    }

    private static void observeStage(MinecraftClient client, int observed) throws Exception {
        if (clientPrimary == null) return;
        var view = AnomalyClient.snapshot(clientPrimary);
        boolean ready = switch (observed) {
            case 1 -> view != null && value(view, "ice") == ice1.amount();
            case 2 -> view != null && value(view, "ice") == ice1.amount() && value(view, "fire") == fire1.amount();
            case 3, 6 -> view != null && effect(view, "ice") && clientPrimary.hasStatusEffect(SscAddon.FROZEN);
            case 4 -> view != null && effect(view, "frozen_recovery") && !effect(view, "ice")
                    && clientPrimary.hasStatusEffect(SscAddon.FROZEN_RECOVERY);
            case 5, 7 -> view != null && !effect(view, "ice") && !effect(view, "frozen_recovery")
                    && !clientPrimary.hasStatusEffect(SscAddon.FROZEN);
            case 8 -> view != null && effect(view, "frost_slow") && view.fullElements().containsAll(Set.of("ice", "fire"));
            case 9 -> client.world.getEntityById(primaryEntityId) == null
                    && (AnomalyClient.snapshot(clientPrimary) == null || AnomalyClient.snapshot(clientPrimary).empty());
            default -> false;
        };
        if (!ready) return;
        if (observed == 1) {
            renderScratchChecks = FrozenRenderScratchProbe.run(client);
            CHECKS.addAndGet(renderScratchChecks);
        }
        CLIENT_STAGES.add(observed); clientObservedStage = observed; stateSeen = true;
        if (observed != 9) {
            check(view.maximum() == 500, "Real STATE packet retains the five-hundred maximum at stage " + observed);
            check(view == AnomalyClient.snapshot(clientPrimary), "Repeated display reads reuse one immutable projection in the same client tick");
            AnomalySnapshot authoritative = expected;
            check(authoritative != null && view.values().equals(authoritative.values()),
                    "Client elemental values match the immutable server snapshot at stage " + observed);
            check(view.remainingTicks() >= 0 && view.remainingTicks() <= 400,
                    "Client countdown stays within the twenty-second boundary");
            if (observed <= 2) check(view.remainingTicks() >= 360,
                    "The real cross-element packet reports a freshly reset twenty-second timer");
            if (observed == 3 || observed == 6)
                check(view.linkRemaining() > 50 && view.linkRemaining() <= 100,
                        "The client receives the independent five-second link window");
            if (observed == 8) check(view.linkRemaining() == 0 && view.fullRemainingTicks() > 50,
                    "Reacted partners remain full while their link eligibility is consumed");
        }
        if (observed == 3) screenshot(client, "anomaly-01-frozen.png");
        if (observed == 4) screenshot(client, "anomaly-02-shattered.png");
        if (observed == 8) screenshot(client, "anomaly-03-reaction.png");
        if (observed == 3 && pausePhase == 0) {
            check(client.getServer() != null && !client.getServer().isRemote(),
                    "The actual pause regression uses an integrated server that has not opened to LAN");
            pausePhase = 1; pauseStartedAt = clientTicks; pauseLastServerTick = serverTicks;
            client.setScreen(new GameMenuScreen(true));
        }
    }

    /** Keep the real menu open while the integrated world is paused; the ordinary driver closes other screens. */
    private static boolean pauseTick(MinecraftClient client) throws Exception {
        if (pausePhase == 0 || pausePhase == 4) return false;
        if (clientTicks - pauseStartedAt > 100) throw new AssertionError("Actual GameMenu pause/resume did not settle within one hundred client ticks");
        if (pausePhase == 1) {
            check(client.currentScreen instanceof GameMenuScreen && client.currentScreen.shouldPause(),
                    "The vanilla GameMenuScreen stays open during the pause regression");
            if (!client.isPaused()) return true;
            pauseStableTicks = serverTicks == pauseLastServerTick ? pauseStableTicks + 1 : 0;
            pauseLastServerTick = serverTicks;
            if (pauseStableTicks < 3) return true;
            pauseBaseline = AnomalyClient.snapshot(clientPrimary);
            check(pauseBaseline != null && pauseBaseline.remainingTicks() > 0 && pauseBaseline.linkRemaining() > 0
                            && effect(pauseBaseline, "ice"),
                    "The paused client has live accumulation, link-window and freeze counters to observe");
            pauseServerTick = serverTicks; pauseWorldTime = client.world.getTime();
            pauseVanillaFrozenTicks = clientPrimary.getStatusEffect(SscAddon.FROZEN).getDuration();
            screenshot(client, "anomaly-04-paused.png");
            pausePhase = 2;
            return true;
        }
        if (pausePhase == 2) {
            check(client.isPaused() && serverTicks == pauseServerTick && client.world.getTime() == pauseWorldTime,
                    "Client callbacks continue while both real world clocks stay paused");
            AnomalySnapshot view = AnomalyClient.snapshot(clientPrimary);
            check(view != null && view.values().equals(pauseBaseline.values()), "Pause preserves elemental accumulation values");
            check(view.remainingTicks() == pauseBaseline.remainingTicks(), "Pause preserves the twenty-second display counter");
            check(view.linkRemaining() == pauseBaseline.linkRemaining(), "Pause preserves the five-second link display counter");
            check(view.effects().equals(pauseBaseline.effects()), "Pause preserves every projected anomaly-effect counter");
            check(clientPrimary.hasStatusEffect(SscAddon.FROZEN)
                            && clientPrimary.getStatusEffect(SscAddon.FROZEN).getDuration() == pauseVanillaFrozenTicks,
                    "The real vanilla freeze duration also stays paused");
            if (++pauseObservedTicks >= 10) {
                client.setScreen(null);
                pausePhase = 3;
            }
            return true;
        }
        if (client.isPaused()) return true;
        if (++pauseResumeTicks < 3 || serverTicks <= pauseServerTick) return true;
        AnomalySnapshot resumed = AnomalyClient.snapshot(clientPrimary);
        check(resumed != null && resumed.remainingTicks() < pauseBaseline.remainingTicks()
                        && resumed.linkRemaining() < pauseBaseline.linkRemaining(),
                "Closing the real pause menu resumes both anomaly display clocks");
        check(resumed.remainingTicks() >= pauseBaseline.remainingTicks() - 20,
                "Resuming does not charge the paused client callbacks as elapsed combat time");
        check(clientPrimary.hasStatusEffect(SscAddon.FROZEN)
                        && clientPrimary.getStatusEffect(SscAddon.FROZEN).getDuration() < pauseVanillaFrozenTicks,
                "The real vanilla freeze countdown resumes with simulation time");
        pauseVerified = true; pausePhase = 4;
        return false;
    }

    private static void serverTick(MinecraftServer server) {
        if (finished || failure != null || !worldReady || server.getPlayerManager().getPlayerList().isEmpty()) return;
        serverTicks++;
        if (serverTicks > 1200) throw new AssertionError("Native client test exceeded 1200 active server ticks");
        if (serverTicks == 1) setup(server);
        if (serverTicks == 40) {
            requireTrackedPlayer(server);
            AnomalyManager.enqueue(ice1, primary); AnomalyManager.drain(server); publish(1);
        }
        if (serverTicks == 70) {
            requireClientStage(1); AnomalyManager.enqueue(fire1, primary); AnomalyManager.drain(server); publish(2);
        }
        if (serverTicks == 100) {
            requireClientStage(2); AnomalyManager.enqueue(ice5, primary); AnomalyManager.drain(server);
        }
        if (serverTicks == 104) {
            AnomalyManager.enqueue(ice1, primary); AnomalyManager.drain(server); publish(3);
        }
        if (serverTicks == 125) {
            requireClientStage(3); primary.timeUntilRegen = 0;
            check(primary.damage(primary.getDamageSources().generic(), 1), "The tracked target takes real breaking damage");
            publish(4);
        }
        if (serverTicks == 145) {
            requireClientStage(4); primary.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED, 20));
        }
        if (serverTicks == 148) publish(5);
        if (serverTicks == 170) {
            requireClientStage(5); AnomalyManager.clear(primary);
            for (int i = 0; i < 3; i++) AnomalyManager.enqueue(ice1, primary);
            AnomalyManager.drain(server); publish(6);
        }
        if (serverTicks == 195) {
            requireClientStage(6); primary.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED, 20));
        }
        if (serverTicks == 198) publish(7);
        if (serverTicks == 225) {
            requireClientStage(7); AnomalyManager.enqueue(fire5, primary); AnomalyManager.enqueue(fire5, primary);
            AnomalyManager.drain(server); publish(8);
        }
        if (serverTicks == 250) {
            requireClientStage(8); primary.discard(); stage = 9;
        }
        if (serverTicks == 278) {
            requireClientStage(9); AnomalyManager.enqueue(ice1, secondary); AnomalyManager.drain(server);
        }
        if (serverTicks == 293) {
            check(secondary.damage(secondary.getDamageSources().generic(), 10000), "Second real tracked target receives lethal damage");
        }
        if (serverTicks == 325) {
            check(!secondary.isAlive(), "The second tracked target actually died");
            check(configSeen && stateSeen, "The live client has exercised the full CONFIG and STATE receivers");
        }
        if (serverTicks >= PERF_START && serverTicks < PERF_START + SCENES.length * PHASE_TICKS) {
            int scene = (serverTicks - PERF_START) / PHASE_TICKS;
            int age = (serverTicks - PERF_START) % PHASE_TICKS;
            if (scene != perfScene) startPhase(scene);
            perfAge = age;
            if (scene >= 4) stressFourHits();
            if (age >= WARMUP_TICKS) {
                SERVER_TICKS.get(scene).add((System.nanoTime() - tickStart) / 1e6);
                long allocated = allocated();
                if (allocated >= 0 && tickAllocated >= 0) SERVER_ALLOC[scene] += Math.max(0, allocated - tickAllocated);
            }
        }
        if (serverTicks == PERF_START + SCENES.length * PHASE_TICKS) {
            perfScene = -1; AnomalyManager.clearAll();
            check(PIGS.stream().filter(Entity::isAlive).count() == TARGETS,
                    "All eight measurements kept the same thirty-two rendered living targets");
            check(configSeen && stateSeen, "Native packet evidence was retained through the comparison");
        }
        if (serverTicks == PERF_START + SCENES.length * PHASE_TICKS + 10) {
            check(clientSecondary == null || !clientSecondary.isAlive() || clientObservedStage >= 9,
                    "Real client tracked the lifetime test entities");
            AnomalyManager.enqueue(ice1, PIGS.get(0)); AnomalyManager.drain(server);
        }
        if (serverTicks == PERF_START + SCENES.length * PHASE_TICKS + 30) {
            stage = 100;
            player.teleport(server.getWorld(World.NETHER), 0.5, 90, 0.5, 0, 0);
        }
    }

    private static void setup(MinecraftServer server) {
        player = server.getPlayerManager().getPlayer(actualPlayer);
        check(player != null && player.networkHandler != null,
                "The server actor is the actually connected integrated client player");
        var world = player.getServerWorld(); origin = player.getPos();
        expectedConfig = AnomalyRules.amount("fire_bolt", 1);
        server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        server.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        world.setTimeOfDay(6000); world.setWeather(0, 100000, false, false);
        camera = origin.add(0, 3.8, -8);
        player.getAbilities().flying = true; player.getAbilities().allowFlying = true; player.sendAbilitiesUpdate();
        player.setNoGravity(true); player.teleport(world, camera.x, camera.y, camera.z, 0, 22);
        for (int i = 0; i < TARGETS; i++) {
            Vec3d position = origin.add((i % 8 - 3.5) * 1.1, 0, 2 + (i / 8) * 1.4);
            POSITIONS.add(position); PIGS.add(spawn(position));
        }
        primary = PIGS.get(11); secondary = PIGS.get(12);
        primaryId = primary.getUuid(); secondaryId = secondary.getUuid();
        primaryEntityId = primary.getId(); secondaryEntityId = secondary.getId();
        ice1 = context("frost_spike", 1); ice5 = context("frost_spike", 5);
        fire1 = context("fire_bolt", 1); fire5 = context("fire_bolt", 5);
    }

    private static PigEntity spawn(Vec3d position) {
        var pig = EntityType.PIG.create(player.getServerWorld());
        if (pig == null) throw new AssertionError("Could not create a real pig");
        pig.setAiDisabled(true); pig.setNoGravity(true); pig.setSilent(true);
        pig.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1024); pig.setHealth(1024);
        pig.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        pig.refreshPositionAndAngles(position.x, position.y, position.z, 180, 0);
        check(player.getServerWorld().spawnEntity(pig), "A real fixed-scene target was spawned");
        return pig;
    }

    private static SpellAnomalyContext context(String spell, int level) {
        return SpellAnomalyHits.create(player, SpellRegistry.get(spell), level);
    }

    private static void requireTrackedPlayer(MinecraftServer server) {
        check(player == server.getPlayerManager().getPlayer(actualPlayer), "No mock player is used for packet acceptance");
        check(ServerPlayNetworking.canSend(player, AnomalyNetwork.STATE), "The real client registered the complete STATE channel");
        check(ServerPlayNetworking.canSend(player, AnomalyNetwork.CONFIG), "The real client registered the complete CONFIG channel");
        check(configSeen, "The real CONFIG packet replaced the deliberately different pre-connection client table");
    }

    private static void publish(int nextStage) {
        expected = AnomalyManager.snapshot(primary); stage = nextStage;
    }

    private static void requireClientStage(int needed) {
        check(clientObservedStage >= needed, "Real client observed acceptance stage " + needed + " before the next server action");
    }

    private static void startPhase(int scene) {
        AnomalyManager.clearAll();
        for (int i = 0; i < TARGETS; i++) {
            PigEntity pig = PIGS.get(i);
            if (!pig.isAlive() || pig.isRemoved()) { pig = spawn(POSITIONS.get(i)); PIGS.set(i, pig); }
            pig.clearStatusEffects(); pig.setHealth(1024); pig.timeUntilRegen = 0;
            Vec3d position = POSITIONS.get(i);
            pig.refreshPositionAndAngles(position.x, position.y, position.z, 180, 0);
            pig.setVelocity(Vec3d.ZERO);
        }
        player.teleport(player.getServerWorld(), camera.x, camera.y, camera.z, 0, 22);
        AnomalyManager.setEnabledForTesting(scene % 4 == 1 || scene % 4 == 2);
        PHASE_EVENTS.add(Map.of("scene", SCENES[scene], "targets", TARGETS,
                "camera", List.of(camera.x, camera.y, camera.z), "yaw", 0, "pitch", 22,
                "render_distance_chunks", 6, "hits_per_tick", scene >= 4 ? 4 : 0,
                "controls", "NoAI, no gravity, full knockback resistance; every active tick restores all target health before identical original hits"));
        perfAge = 0; perfScene = scene;
        System.out.println("ANOMALY_CLIENT_PERF scene=" + SCENES[scene]);
    }

    private static void stressFourHits() {
        // MAX_HEALTH clamps at 1024; matched healing keeps dense genuine AOE from changing the scene.
        for (PigEntity pig : PIGS) {
            check(pig.isAlive() && !pig.isRemoved(), "Every matched activity tick retains its original living target");
            pig.setHealth(pig.getMaxHealth());
        }
        for (int hit = 0; hit < 4; hit++) {
            int sequence = (serverTicks - PERF_START) * 4 + hit;
            PigEntity target = PIGS.get(sequence % TARGETS);
            SpellAnomalyContext context = (sequence / TARGETS & 1) == 0 ? ice5 : fire5;
            target.timeUntilRegen = 0;
            if (target.damage(SpellDamageSource.of(target.getDamageSources(), player), 1))
                SpellAnomalyHits.hit(context, target);
        }
    }

    private static double value(AnomalySnapshot snapshot, String type) { return snapshot.values().getOrDefault(type, 0.0); }
    private static boolean effect(AnomalySnapshot snapshot, String id) {
        return snapshot.effects().stream().anyMatch(effect -> effect.id().equals(id));
    }

    private static void screenshot(MinecraftClient client, String name) throws Exception {
        if (!SHOTS.add(name)) return;
        Files.createDirectories(Path.of("screenshots"));
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(Path.of("screenshots", name));
        }
    }

    private static Map<String, Object> statistics(List<Double> samples) {
        check(samples.size() >= 20, "A native measurement contains enough real samples");
        var sorted = new ArrayList<>(samples); sorted.sort(Double::compareTo);
        double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        var result = new LinkedHashMap<String, Object>();
        result.put("samples", samples.size()); result.put("mean_ms", mean);
        result.put("p95_ms", sorted.get(Math.min(sorted.size() - 1, (int)Math.floor(sorted.size() * .95))));
        result.put("max_ms", sorted.get(sorted.size() - 1));
        return result;
    }

    private static void finish(MinecraftClient client) throws Exception {
        check(dimensionSeen && disconnected && configSeen && stateSeen,
                "Actual dimension/disconnect cleanup and both production packet receivers completed");
        check(pauseVerified && pauseObservedTicks >= 10,
                "The actual single-player pause menu preserved and resumed all anomaly display clocks");
        check(CLIENT_STAGES.containsAll(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)),
                "Every state, timer, freeze, shatter, purification, reaction and unload observation completed");
        var report = new LinkedHashMap<String, Object>();
        report.put("kind", "native-integrated-client-flat-world");
        report.put("checks", CHECKS.get()); report.put("targets", TARGETS);
        report.put("render_scratch_checks", renderScratchChecks);
        report.put("pause_countdown_verified", pauseVerified);
        report.put("paused_client_callbacks", pauseObservedTicks);
        report.put("real_player_uuid", actualPlayer); report.put("config_channel_received", configSeen);
        report.put("state_channel_received", stateSeen); report.put("dimension_cleanup", dimensionSeen);
        report.put("disconnect_cleanup", disconnected); report.put("observed_stages", CLIENT_STAGES);
        report.put("screenshots", SHOTS); report.put("scene_controls", PHASE_EVENTS);
        report.put("sampling", "Eight ABBA phases, each 80 ticks, first 20 excluded; load/teleport/disconnect excluded; normal rendering retained");
        var scenes = new LinkedHashMap<String, Object>();
        for (int scene = 0; scene < SCENES.length; scene++) {
            var frame = statistics(FRAMES.get(scene));
            frame.put("mean_fps", 1000.0 / ((Number)frame.get("mean_ms")).doubleValue());
            long first = RENDER_ALLOC_FIRST[scene], last = RENDER_ALLOC_LAST[scene];
            long renderBytes = first < 0 || last < first ? -1 : last - first;
            frame.put("render_thread_allocated_bytes", renderBytes);
            frame.put("render_thread_allocated_bytes_per_sample_frame", renderBytes < 0 ? -1 : renderBytes / (double)FRAMES.get(scene).size());
            var server = statistics(SERVER_TICKS.get(scene));
            server.put("server_thread_allocated_bytes", ALLOCATIONS == null ? -1 : SERVER_ALLOC[scene]);
            scenes.put(SCENES[scene], Map.of("frame_interval", frame, "server_tick", server));
        }
        report.put("scenes", scenes);
        var combined = new LinkedHashMap<String, Object>();
        String[] groups = {"idle_off", "idle_on", "active_off", "active_on"};
        for (String group : groups) {
            var frames = new ArrayList<Double>(); var ticks = new ArrayList<Double>();
            for (int scene = 0; scene < SCENES.length; scene++) if (SCENES[scene].startsWith(group + "_")) {
                frames.addAll(FRAMES.get(scene)); ticks.addAll(SERVER_TICKS.get(scene));
            }
            var frameStats = statistics(frames);
            frameStats.put("mean_fps", 1000.0 / ((Number)frameStats.get("mean_ms")).doubleValue());
            combined.put(group, Map.of("frame_interval", frameStats, "server_tick", statistics(ticks)));
        }
        report.put("combined", combined);
        report.put("limitation", "Single integrated-client hardware/scene comparison; does not assert identical performance on all hardware or remote multiplayer.");
        report.put("checks", CHECKS.get());
        Files.writeString(Path.of("anomaly-client-live-result.json"), NeoState.JSON.toJson(report));
        finished = true;
        System.out.println("ANOMALY_CLIENT_LIVE_PASS checks=" + CHECKS.get());
        client.scheduleStop();
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean extended && extended.isThreadAllocatedMemorySupported()) {
            if (!extended.isThreadAllocatedMemoryEnabled()) extended.setThreadAllocatedMemoryEnabled(true);
            return extended;
        }
        return null;
    }

    private static long allocated() {
        return ALLOCATIONS == null ? -1 : ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static void check(boolean value, String message) {
        CHECKS.incrementAndGet(); if (!value) throw new AssertionError(message);
    }

    private static void fail(Throwable error) {
        if (failure != null) return;
        failure = error; finished = true; error.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Anomaly native client acceptance failed", failure);
        if (!finished || !Files.isRegularFile(Path.of("anomaly-client-live-result.json")))
            throw new AssertionError("The native client closed before anomaly acceptance completed");
    }
}
