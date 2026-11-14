package net.jackcooper.shapeShifterCurseAddon.client.hud;

import com.google.gson.GsonBuilder;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Isolated packaged client: real dash packets, synchronized resources and rendered HUD frames. */
public final class AxolotlDashHudClientProbe implements ClientModInitializer {
    private static final Identifier LAND = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_land");
    private static final Identifier WATER = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_water");
    private static final long START = System.nanoTime();
    private static final List<String> screenshots = new ArrayList<>();
    private static final List<Map<String, Object>> scenes = new ArrayList<>();
    private static volatile boolean configured, finished;
    private static volatile int requestedScene = -1, appliedScene = -1;
    private static boolean starting, requested, gestureSent;
    private static int serverTicks, scene, waitTicks, renderedFrames, checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static boolean wet(int value) { return value % 2 == 1; }
    private static Identifier expected() { return wet(scene) ? WATER : LAND; }

    @Override public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, delta) -> {
            var mc = MinecraftClient.getInstance();
            if (finished || !requested || mc.player == null || appliedScene != scene || scene >= 6) return;
            var entries = SkillHudCatalog.entriesForHud(FormIdentifiers.UPGRADE_AXOLOTL, mc.player);
            if (entries.size() == 3 && entries.get(2).skill().resolveCooldown(mc.player).equals(expected())
                    && mc.player.isTouchingWater() == wet(scene)) renderedFrames++;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (finished) return;
                if (System.nanoTime() - START > 240_000_000_000L) throw new AssertionError("Dash HUD client timed out");
                if (!starting && mc.currentScreen instanceof TitleScreen) {
                    starting = true;
                    mc.options.pauseOnLostFocus = false;
                    mc.options.getViewDistance().setValue(3);
                    mc.options.getMaxFps().setValue(60);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                            DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("Dash-HUD-" + System.currentTimeMillis(),
                            new LevelInfo("Dash HUD", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                            new GeneratorOptions(20261114L, false, false),
                            r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (mc.player == null || mc.world == null || !configured
                        || !net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isUpgradeAxolotl(mc.player)) return;
                if (mc.currentScreen != null) mc.setScreen(null);
                mc.options.hudHidden = false;
                var cfg = SSCAddonConfig.client();
                cfg.showCdBar = true; cfg.cdBarPosOffsetX = 24; cfg.cdBarPosOffsetY = 40;
                if (!requested) {
                    requested = true; gestureSent = false; renderedFrames = 0; waitTicks = 0;
                    mc.options.getGuiScale().setValue(2 + scene / 2);
                    mc.onResolutionChanged();
                    mc.inGameHud.getChatHud().clear(false);
                    requestedScene = scene;
                }
                if (++waitTicks > 220) throw new AssertionError("Dash scene " + scene + " failed to synchronize");
                if (appliedScene != scene || mc.player.isTouchingWater() != wet(scene)) return;
                if (scene < 2 && !gestureSent) {
                    for (boolean held : new boolean[]{false, true, false, true, false}) {
                        var buf = new PacketByteBuf(Unpooled.buffer()); buf.writeBoolean(held);
                        ClientPlayNetworking.send(SscAddonNetworking.PACKET_AXOLOTL_SPRINT_KEY, buf);
                    }
                    gestureSent = true; renderedFrames = 0; return;
                }
                int land = PowerUtils.getClientResourceValue(mc.player, LAND);
                int water = PowerUtils.getClientResourceValue(mc.player, WATER);
                int active = wet(scene) ? water : land;
                if (scene < 4 ? active <= 0 : land != 0 || water != 0) return;
                if (waitTicks < 8 || renderedFrames < 2) return;
                var entries = SkillHudCatalog.entriesForHud(FormIdentifiers.UPGRADE_AXOLOTL, mc.player);
                check(entries.size() == 3 && entries.get(2).slot() == SkillHudLayout.Slot.LOWER,
                        "One dash stays in the lower frame beside the main pair");
                var dash = entries.get(2).skill();
                check(dash.resolveCooldown(mc.player).equals(expected()), "Rendered dash selects its current environment resource");
                check(dash.authorityId(mc.player).equals(expected().toString()), "Authority lookup selects the same cooldown");
                var read = SkillCooldownBarRenderer.class.getDeclaredMethod("readCooldown", net.minecraft.entity.player.PlayerEntity.class,
                        Identifier.class, int.class);
                read.setAccessible(true);
                Object cd = read.invoke(new SkillCooldownBarRenderer(), mc.player, dash.resolveCooldown(mc.player), dash.cooldownTotalTicks());
                var remaining = cd.getClass().getDeclaredMethod("remaining"); remaining.setAccessible(true);
                int shown = (int) remaining.invoke(cd);
                check(shown == active, "Renderer reads the native synchronized countdown");
                check(scene < 4 ? shown > 0 : shown == 0, "Active countdown is visible and completed cooldown clears");
                String file = "dash-hud-" + scene + "-" + (wet(scene) ? "water" : "land") + "-gui" + (2 + scene / 2) + ".png";
                try (var shot = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) { shot.writeTo(Path.of(file)); }
                screenshots.add(file);
                scenes.add(Map.of("scene", scene, "wet", wet(scene), "gui_scale", 2 + scene / 2,
                        "land_ticks", land, "water_ticks", water, "displayed_ticks", shown, "frames", renderedFrames));
                scene++; requested = false;
                if (scene == 6) {
                    finished = true;
                    Files.writeString(Path.of("dash-client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                            Map.of("passed", true, "checks", checks, "scenes", scenes, "screenshots", screenshots)));
                    System.out.println("AXOLOTL_DASH_HUD_CLIENT_PASS checks=" + checks);
                    mc.scheduleStop();
                }
            } catch (Throwable failure) { fail(failure); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
                var p = server.getPlayerManager().getPlayerList().get(0);
                if (++serverTicks == 40) {
                    server.getOverworld().setTimeOfDay(6000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    FormUtils.setForm(p, RegPlayerForms.getPlayerForm(FormIdentifiers.UPGRADE_AXOLOTL));
                    var evolution = RegEvolutionComponent.EVOLUTION.get(p);
                    evolution.setRoute("axolotl"); evolution.setRouteStartForm(FormIdentifiers.UPGRADE_AXOLOTL.toString());
                    for (String node : List.of("axolotl_base", "aquatic_adapt", "water_propel", "water_spurt", "water_spear", "vortex_guide")) evolution.unlock(node);
                    RegEvolutionComponent.EVOLUTION.sync(p);
                    for (int x = 5; x <= 27; x++) for (int z = 5; z <= 11; z++)
                        server.getOverworld().setBlockState(new BlockPos(x, 89, z), Blocks.STONE.getDefaultState());
                    for (int x = 22; x <= 26; x++) for (int y = 90; y <= 92; y++) for (int z = 6; z <= 10; z++)
                        server.getOverworld().setBlockState(new BlockPos(x, y, z), Blocks.WATER.getDefaultState());
                    p.setNoGravity(true); p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                }
                if (serverTicks >= 60) configured = true;
                // Keep the screenshot fixture in its selected medium after the dash impulse.
                if (requestedScene >= 0) {
                    Vec3d at = new Vec3d(wet(requestedScene) ? 24.5 : 8.5, 90, 8.5);
                    if (p.getPos().squaredDistanceTo(at) > .01) p.networkHandler.requestTeleport(at.x, at.y, at.z, 0, 0);
                    p.setVelocity(Vec3d.ZERO);
                    if (p.isTouchingWater() == wet(requestedScene)) appliedScene = requestedScene;
                }
                p.setAir(300);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void fail(Throwable failure) {
        if (finished) return;
        finished = true; failure.printStackTrace();
        try { Files.writeString(Path.of("dash-client-result.json"), new GsonBuilder().create().toJson(Map.of("passed", false, "error", failure.toString()))); }
        catch (Exception ignored) { }
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }
}
