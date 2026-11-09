package net.jackcooper.shapeShifterCurseAddon.client.hud;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.trinkets.api.TrinketsApi;
import io.github.apace100.apoli.component.PowerHolderComponent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.jackcooper.shapeShifterCurseAddon.client.screen.BarPositionEditorScreen;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.render.tech.ItemStorePowerRender;
import net.onixary.shapeShifterCurseFabric.render.tech.ItemStorePowerRender.itemStorePowerRenderInterface;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.lwjgl.opengl.GL11;

/** Pixel comparisons against the actual dependency's unmodified renderer in a disposable client world. */
public final class ItemStorePositionClientProbe {
    private static final Identifier SPIDER = new Identifier("shape-shifter-curse", "spider_3");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static boolean starting, finished;
    private static volatile boolean ready;
    private static Throwable failure;
    private static int ticks, serverTicks, stage, sample, checks;
    private static int columns = 1, rows = 1;
    private static Class<?> referenceClass;
    private static Method originalSlot, wrappedSlot;
    private static List<itemStorePowerRenderInterface> powers;
    private static ComparisonScreen screen;
    private static NativeImage reference;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method slot(Class<?> type) throws Exception {
        Method method = type.getDeclaredMethod("renderSlot", DrawContext.class, float.class, itemStorePowerRenderInterface.class);
        method.setAccessible(true);
        return method;
    }

    private static void loadReference() throws Exception {
        // Rename only the class owner, leaving SSC's method bodies and shared interface untouched.
        // The renamed class is not a Mixin target, so it supplies SSC's real original renderer.
        String source = ItemStorePowerRender.class.getName().replace('.', '/');
        String target = "net/jackcooper/shapeShifterCurseAddon/client/hud/OriginalSscItemStoreRenderer";
        Path file = FabricLoader.getInstance().getModContainer("shape-shifter-curse").orElseThrow()
                .findPath(source + ".class").orElseThrow();
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(Files.readAllBytes(file)).accept(new ClassRemapper(writer, new SimpleRemapper(source, target)), 0);
        class ReferenceLoader extends ClassLoader {
            ReferenceLoader() { super(ItemStorePowerRender.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass(target.replace('/', '.'), bytes, 0, bytes.length); }
        }
        referenceClass = new ReferenceLoader().define(writer.toByteArray());
        originalSlot = slot(referenceClass);
        wrappedSlot = slot(ItemStorePowerRender.class);
    }

    private static void next() { stage++; ticks = 0; }

    private static String name() {
        return new String[]{"default-implicit-100", "explicit-100-large-gui", "reset-narrow-gui", "multi-row-left-edge", "custom-offset-100"}[sample];
    }

    private static void configure(MinecraftClient client) throws Exception {
        var config = SSCAddonConfig.client();
        var base = ShapeShifterCurseFabric.clientConfig;
        base.itemStorePowerPosType = 8;
        base.itemStorePowerPosOffsetX = -120;
        base.itemStorePowerPosOffsetY = 1;
        config.showSscItemStoreHud = true;
        config.hudScalePercent.remove("item_store");
        columns = rows = 1;
        client.options.getGuiScale().setValue(sample == 0 || sample == 4 ? 2 : 4);
        if (sample == 2) client.getWindow().setWindowedSize(1280, 960);
        client.onResolutionChanged();
        if (sample == 1) HudScaling.set(config, 7, 100);
        if (sample == 2) {
            HudScaling.set(config, 7, 150);
            config.showSscItemStoreHud = false;
            base.itemStorePowerPosOffsetX = 30;
            base.itemStorePowerPosOffsetY = -60;
            var editor = new BarPositionEditorScreen(null);
            client.setScreen(editor);
            Method reset = BarPositionEditorScreen.class.getDeclaredMethod("doResetSection", int.class);
            reset.setAccessible(true);
            reset.invoke(editor, 7);
            check(base.itemStorePowerPosType == 8 && base.itemStorePowerPosOffsetX == -120 && base.itemStorePowerPosOffsetY == 1,
                    "Reset uses SSC's actual default anchor and offsets");
            check(config.showSscItemStoreHud && HudScaling.get(config, 7) == 100, "Reset restores visible native size");
        }
        if (sample == 3) { columns = 2; rows = 3; }
        if (sample == 4) { base.itemStorePowerPosOffsetX = -90; base.itemStorePowerPosOffsetY = -17; }
        field(ItemStorePowerRender.class, "NowCol").setInt(null, columns);
        field(ItemStorePowerRender.class, "NowRow").setInt(null, rows);
        var view = HudScaling.view(7);
        check(view.scale() == 1 && view.source().equals(view.bounds()), "Native-size render and editor bounds preserve SSC coordinates: " + name());
        screen = new ComparisonScreen();
        client.setScreen(screen);
    }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server);
            state.tutorialPolicyInitialized = true;
            state.tutorialAutoEnabled = false;
            state.markDirty();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || ready || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                if (++serverTicks < 40) return;
                var player = server.getPlayerManager().getPlayerList().get(0);
                if (serverTicks == 40) {
                    server.getOverworld().setTimeOfDay(18000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    FormUtils._setForm(player, RegPlayerForms.getPlayerForm(SPIDER));
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_FENCE));
                    player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.ARROW, 64));
                }
                var slots = TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory();
                var hand = slots.get("hand").get("extra_hand");
                if (hand == null || hand.size() == 0) return;
                hand.setStack(0, new ItemStack(Items.TOTEM_OF_UNDYING));
                hand.markDirty();
                ready = true;
            } catch (Throwable error) { fail(error); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 180_000_000_000L) throw new AssertionError("Item store pixel comparison timed out at stage " + stage);
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true;
                    client.options.pauseOnLostFocus = false;
                    client.options.getViewDistance().setValue(4);
                    client.options.getMaxFps().setValue(60);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                    client.createIntegratedServerLoader().createAndStart("Spider-Hud-" + System.currentTimeMillis(),
                            new LevelInfo("Spider HUD position comparison", GameMode.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                            new GeneratorOptions(20261106L, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (!ready || client.player == null || client.world == null) return;
                ticks++;
                switch (stage) {
                    case 0 -> {
                        if (!FormUtils.getPlayerForm(client.player).getFormID().equals(SPIDER)) return;
                        powers = PowerHolderComponent.KEY.get(client.player).getPowers().stream()
                                .filter(power -> power.isActive() && power instanceof itemStorePowerRenderInterface)
                                .map(itemStorePowerRenderInterface.class::cast).toList();
                        if (powers.isEmpty() || powers.stream().noneMatch(power -> power.getStack().isOf(Items.TOTEM_OF_UNDYING))) return;
                        check(powers.size() == 1 && powers.get(0).getSlot() == 0, "The real SSC spider has the expected extra-offhand slot");
                        loadReference();
                        client.setScreen(null);
                        next();
                    }
                    case 1 -> {
                        if (ticks < 70) return;
                        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(Path.of("spider-hud-native.png")); }
                        configure(client);
                        next();
                    }
                    case 2 -> {
                        if (ticks < 8) return;
                        reference = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
                        reference.writeTo(Path.of("ssc-" + name() + ".png"));
                        screen.referenceMode = false;
                        next();
                    }
                    case 3 -> {
                        if (ticks < 8) return;
                        try (var actual = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                            actual.writeTo(Path.of("ssca-" + name() + ".png"));
                            int different = 0, colored = 0;
                            int firstRow = Math.max(0, actual.getHeight() - (int) Math.ceil(80 * client.getWindow().getScaleFactor()));
                            for (int y = firstRow; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
                                if (reference.getColor(x, y) != actual.getColor(x, y)) different++;
                                if (actual.getColor(x, y) != actual.getColor(actual.getWidth() - 1, firstRow)) colored++;
                            }
                            check(colored > 100, "The compared frames contain actual slot artwork and item pixels");
                            check(different == 0, "SSC/SSCA storage slot pixels match exactly: " + name() + ", changed pixels=" + different);
                            RESULTS.add(Map.of("case", name(), "changed_pixels", different, "width", screen.width, "height", screen.height));
                        }
                        reference.close(); reference = null;
                        if (++sample < 5) { configure(client); stage = 2; ticks = 0; }
                        else next();
                    }
                    case 4 -> {
                        var config = SSCAddonConfig.client();
                        for (int scale : new int[]{50, 75, 125, 150, 200}) {
                            HudScaling.set(config, 7, scale);
                            var view = HudScaling.view(7);
                            check(Math.abs(view.scale() - scale / 100f) < .001f, "Custom storage scale remains available: " + scale);
                            var box = view.bounds();
                            check(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= screen.width && box.y() + box.height() <= screen.height,
                                    "Custom scaling retains screen-edge clamping");
                        }
                        Files.writeString(Path.of("item-store-position-result.json"), new Gson().toJson(Map.of(
                                "passed", true, "checks", checks, "real_spider", true, "ssc_dependency_bytecode_reference", true, "pixel_comparisons", RESULTS)));
                        finished = true;
                        System.out.println("ITEM_STORE_POSITION_CLIENT_PASS checks=" + checks + " pixel_comparisons=" + RESULTS.size());
                        client.scheduleStop();
                    }
                    default -> throw new AssertionError("Unknown stage " + stage);
                }
            } catch (Throwable error) { fail(error); }
        });
    }

    private static final class ComparisonScreen extends Screen {
        private boolean referenceMode = true;
        ComparisonScreen() { super(Text.literal("SSC storage-slot pixel reference")); }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            try {
                context.fill(0, 0, width, height, 0xff30433a);
                context.draw();
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
                RenderSystem.enableBlend();
                Class<?> owner = referenceMode ? referenceClass : ItemStorePowerRender.class;
                field(owner, "NowCol").setInt(null, columns);
                field(owner, "NowRow").setInt(null, rows);
                Method method = referenceMode ? originalSlot : wrappedSlot;
                for (var power : powers) method.invoke(null, context, 0f, power);
                context.draw();
                RenderSystem.disableBlend();
            } catch (Throwable error) { fail(error); }
        }
        @Override public boolean shouldPause() { return false; }
    }

    private static void fail(Throwable error) {
        if (finished) return;
        failure = error; finished = true;
        if (reference != null) reference.close();
        error.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Storage-slot pixel comparison failed", failure);
        if (!finished) throw new AssertionError("Storage-slot pixel comparison did not finish");
    }
}
