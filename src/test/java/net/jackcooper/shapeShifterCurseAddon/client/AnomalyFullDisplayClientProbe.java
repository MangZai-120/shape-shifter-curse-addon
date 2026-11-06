package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyManager;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyNetwork;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.NeoState;

/** Real entity tracking, packet delivery, full badges, shared expiry and post-lock accumulation. */
public final class AnomalyFullDisplayClientProbe {
    private static final long START = System.nanoTime();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static boolean starting, finished;
    private static volatile Throwable failure;
    private static volatile int phase;
    private static volatile UUID targetId;
    private static volatile int targetEntityId;
    private static int serverTicks, checks, matchingPhase, matchingTicks;
    private static PigEntity target;
    private static ServerPlayerEntity caster;
    private static long pairedReactions;

    private static synchronized void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    private static void hit(String element, double amount) {
        var context = new SpellAnomalyContext(new Identifier("ssc_addon", "full_display_probe"), element, 1,
                amount, caster.getUuid(), UUID.randomUUID());
        AnomalyManager.enqueue(context, target);
        AnomalyManager.drain(caster.getServer());
    }

    private static void publish(int next) {
        phase = next;
        AnomalyNetwork.sync(target);
    }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server);
            state.tutorialPolicyInitialized = true;
            state.tutorialAutoEnabled = false;
            state.markDirty();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                serverTicks++;
                if (serverTicks == 40) {
                    caster = server.getPlayerManager().getPlayerList().get(0);
                    var origin = caster.getPos();
                    caster.getServerWorld().setTimeOfDay(6000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    caster.getAbilities().flying = true;
                    caster.getAbilities().allowFlying = true;
                    caster.sendAbilitiesUpdate();
                    caster.teleport(caster.getServerWorld(), origin.x, origin.y + 2, origin.z - 4, 0, 20);
                    target = EntityType.PIG.create(caster.getServerWorld());
                    target.setAiDisabled(true);
                    target.setNoGravity(true);
                    target.noClip = true;
                    target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
                    target.setHealth(1000);
                    target.refreshPositionAndAngles(origin.x, origin.y, origin.z + 4, 180, 0);
                    check(caster.getServerWorld().spawnEntity(target), "A real tracked target spawns");
                    targetEntityId = target.getId();
                    targetId = target.getUuid();
                }
                if (serverTicks == 60) { hit("fire", 500); publish(1); }
                if (serverTicks == 100) {
                    long before = AnomalyManager.metrics().reactions();
                    float health = target.getHealth();
                    hit("ice", 500);
                    check(!target.hasStatusEffect(SscAddon.FROZEN) && target.getHealth() == health - 8,
                            "Second ice directly applies frostfire without its standalone freeze");
                    pairedReactions = AnomalyManager.metrics().reactions();
                    check(pairedReactions == before + 1, "The first fire-ice reaction still happens");
                    publish(2);
                }
                if (serverTicks == 120) {
                    long before = AnomalyManager.metrics().triggers();
                    hit("fire", 500); hit("ice", 500);
                    check(AnomalyManager.metrics().triggers() == before && AnomalyManager.snapshot(target).fullRemainingTicks() == 80,
                            "Repeated hits cannot trigger or extend a locked pair");
                }
                if (serverTicks == 130) { hit("curse", 125); publish(3); }
                if (serverTicks == 150) {
                    hit("space", 500);
                    check(AnomalyManager.metrics().reactions() == pairedReactions, "The third element cannot reuse either locked partner");
                    check(AnomalyManager.snapshot(target).fullRemainingTicks() == 100, "A third trigger restarts the whole group's five seconds");
                    publish(4);
                }
                if (serverTicks == 249) check(AnomalyManager.snapshot(target).fullRemainingTicks() == 1, "The server exposes the full group's last tick");
                if (serverTicks == 250) {
                    var snapshot = AnomalyManager.snapshot(target);
                    check(snapshot.fullElements().isEmpty() && snapshot.values().equals(Map.of("curse", 125.0)),
                            "Full expiry leaves unrelated partial buildup intact");
                    publish(5);
                }
                if (serverTicks == 270) { hit("fire", 250); publish(6); }
            } catch (Throwable error) { fail(error); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 180_000_000_000L) throw new AssertionError("Full anomaly display timed out at phase " + phase);
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true;
                    client.options.pauseOnLostFocus = false;
                    client.options.getMaxFps().setValue(60);
                    client.options.getViewDistance().setValue(4);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                    client.createIntegratedServerLoader().createAndStart("Anomaly-Full-" + System.currentTimeMillis(),
                            new LevelInfo("Full anomaly display acceptance", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                            new GeneratorOptions(20261106L, false, false),
                            registry -> registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (client.world == null || client.player == null || targetId == null) return;
                if (client.currentScreen != null) client.setScreen(null);
                if (!(client.world.getEntityById(targetEntityId) instanceof net.minecraft.entity.LivingEntity observed)
                        || !targetId.equals(observed.getUuid())) return;
                var view = AnomalyClient.snapshot(observed);
                int current = phase;
                if (view == null || current == 0 || SEEN.contains(current)) return;
                boolean matches = switch (current) {
                    case 1 -> view.fullElements().equals(Set.of("fire")) && view.remainingTicks() == 0;
                    case 2 -> view.fullElements().equals(Set.of("fire", "ice")) && view.linkRemaining() == 0;
                    case 3 -> view.fullElements().equals(Set.of("fire", "ice")) && view.values().getOrDefault("curse", 0.0) == 125;
                    case 4 -> view.fullElements().equals(Set.of("fire", "ice", "space"));
                    case 5 -> view.fullElements().isEmpty() && view.values().equals(Map.of("curse", 125.0));
                    case 6 -> view.fullElements().isEmpty() && view.values().equals(Map.of("curse", 125.0, "fire", 250.0));
                    default -> false;
                };
                if (!matches) return;
                // 收包发生在绘制之前，等待数帧后截图，避免截到上一阶段的徽章。
                if (matchingPhase != current) { matchingPhase = current; matchingTicks = 0; }
                if (++matchingTicks < 4) return;
                check(view == AnomalyClient.snapshot(observed), "Same-tick display reads reuse the cached immutable snapshot");
                check(view.values().equals(AnomalyClient.accumulation(observed)), "Rendered badges use the same authoritative values as the snapshot");
                check(AnomalyClient.accumulationTargets().contains(observed), "The world-badge target list includes full-only and mixed targets");
                for (String element : view.fullElements()) check(view.values().get(element) == 500, "Held badges stay completely full: " + element);
                if (!view.fullElements().isEmpty()) check(view.fullRemainingTicks() > 0 && view.fullRemainingTicks() <= 100, "Full display uses a separate bounded five-second clock");
                if (current == 2) {
                    var field = net.minecraft.client.gui.hud.InGameHud.class.getDeclaredField("overlayMessage");
                    field.setAccessible(true);
                    var cue = (net.minecraft.text.Text) field.get(client.inGameHud);
                    String name = net.minecraft.text.Text.translatable("anomaly.ssc_addon.reaction.fire_ice").getString();
                    check(cue != null && cue.getString().contains(name) && !cue.getString().contains("ssc_addon"),
                            "A real server actionbar packet displays the localized frostfire reaction");
                    check(Integer.parseInt(client.particleManager.getDebugString()) > 0,
                            "The real reaction packets create visible client particles");
                    var language = net.minecraft.util.Language.getInstance();
                    for (String pair : net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyEffects.registeredReactions().keySet())
                        check(language.hasTranslation("anomaly.ssc_addon.reaction." + pair.replace('+', '_')),
                                "Each registered reaction has a translated cue: " + pair);
                }
                String name = "anomaly-full-phase-" + current + ".png";
                try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(Path.of(name)); }
                SHOTS.add(name); SEEN.add(current);
                System.out.println("ANOMALY_FULL_CLIENT_PHASE " + current);
                if (current == 6) {
                    check(SEEN.equals(Set.of(1, 2, 3, 4, 5, 6)), "Every real packet and display phase was observed");
                    Files.writeString(Path.of("anomaly-full-client-result.json"), new Gson().toJson(Map.of(
                            "passed", true, "checks", checks, "real_packets", true, "phases", SEEN, "screenshots", SHOTS)));
                    finished = true;
                    System.out.println("ANOMALY_FULL_DISPLAY_CLIENT_PASS checks=" + checks);
                    client.scheduleStop();
                }
            } catch (Throwable error) { fail(error); }
        });
    }

    private static void fail(Throwable error) {
        if (finished) return;
        failure = error; finished = true;
        error.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Full anomaly display failed", failure);
        if (!finished) throw new AssertionError("Full anomaly display did not finish");
    }
}
