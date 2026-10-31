package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.Gson;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.compat.BatEchoFeedback;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Real client packet application after missing-power repair, in a disposable flat world. */
public final class UpstreamCompatibilityClientProbe {
    private static boolean starting;
    private static volatile boolean ready, finished;
    private static volatile Throwable failure;
    private static int ticks, phase, checks;
    private static final long START = System.nanoTime();
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    public static void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                if (++ticks == 60) {
                    var p = server.getPlayerManager().getPlayerList().get(0);
                    server.getOverworld().setTimeOfDay(6000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    p.changeGameMode(GameMode.SURVIVAL);
                    FormUtils._setForm(p, RegPlayerForms.getPlayerForm(FormIdentifiers.ANUBIS_WOLF_SP));
                    ready = true;
                }
            } catch (Throwable e) { fail(e); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 180_000_000_000L) throw new AssertionError("client timeout at phase " + phase);
                if (mc.getOverlay() != null) return;
                if (!starting && mc.currentScreen instanceof TitleScreen) {
                    starting = true; mc.options.pauseOnLostFocus = false;
                    mc.options.getViewDistance().setValue(4); mc.options.getMaxFps().setValue(60);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("UpstreamCompat-" + System.currentTimeMillis(),
                        new LevelInfo("SSC compatibility regression", GameMode.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                        new GeneratorOptions(20261031L, false, false),
                        r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (!ready || mc.player == null || mc.world == null) return;
                if (mc.currentScreen != null) mc.setScreen(null);
                var group = mc.player.getGroup();
                if (phase == 0 && group == EntityGroup.UNDEAD) {
                    check(true, "wolfSP undead power arrived on client"); phase = 1;
                    mc.getServer().execute(() -> {
                        try {
                            var p = mc.getServer().getPlayerManager().getPlayerList().get(0);
                            var holder = PowerHolderComponent.KEY.get(p);
                            var type = PowerTypeRegistry.get(new net.minecraft.util.Identifier("shape-shifter-curse", "undead_group"));
                            holder.removePower(type, FormUtils.getPlayerForm(p).getFormLayer().getRight());
                            PowerHolderComponent.KEY.sync(p);
                        } catch (Throwable e) { fail(e); }
                    });
                } else if (phase == 1 && group != EntityGroup.UNDEAD) {
                    check(true, "client observes missing saved power"); phase = 2;
                    mc.getServer().execute(() -> {
                        try {
                            var p = mc.getServer().getPlayerManager().getPlayerList().get(0);
                            FormUtils._loadForm(p, FormUtils.getPlayerForm(p), false);
                        } catch (Throwable e) { fail(e); }
                    });
                } else if (phase == 2 && group == EntityGroup.UNDEAD) {
                    check(true, "same-form repair sync reaches client"); phase = 3; ready = false;
                    mc.getServer().execute(() -> {
                        try {
                            var p = mc.getServer().getPlayerManager().getPlayerList().get(0);
                            FormUtils._setForm(p, RegPlayerForms.getPlayerForm(FormIdentifiers.GOLDEN_SANDSTORM_SP));
                            var pig = EntityType.PIG.create(p.getServerWorld());
                            pig.setPosition(p.getPos().add(2, 0, 0)); p.getServerWorld().spawnEntity(pig);
                            check(BatEchoFeedback.play(p, pig, 8, .35f), "feedback delivered through real player connection");
                            ready = true;
                        } catch (Throwable e) { fail(e); }
                    });
                } else if (phase == 3 && group == EntityGroup.UNDEAD
                        && FormUtils.getPlayerForm(mc.player).getFormID().equals(FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
                    check(true, "golden form and power both synchronized");
                    Files.writeString(Path.of("upstream-client-result.json"), new Gson().toJson(Map.of("checks", checks, "real_client", true, "component_packets_applied", true)));
                    finished = true; System.out.println("SSC_UPSTREAM_COMPATIBILITY_CLIENT_PASS checks=" + checks); mc.scheduleStop();
                }
            } catch (Throwable e) { fail(e); }
        });
    }
    private static void fail(Throwable e) {
        if (failure != null) return; failure = e; finished = true; e.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }
    public static void verifyResult() {
        if (failure != null) throw new AssertionError("upstream compatibility client test failed", failure);
        if (!finished) throw new AssertionError("client stopped before completion");
    }
}
