package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenuV2;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import net.ssca.neomagic.NeoState;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.GeoBone;

/** Actual transformations, menu input, saves, world rendering, and mesh UV coverage. */
public final class FormColorCoverageClientProbe {
    private static final Path OUT = Path.of("color-coverage");
    private static final int MAGENTA = 0xffee00dd, GREEN = 0xff00ee22;
    private static final boolean REQUIRE_COMPLETE = Boolean.getBoolean("ssca.colorCoverage.requireComplete");
    private static final long START = System.nanoTime();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static List<IForm> forms;
    private static FormColorSelectMenuV2 menu;
    private static Map<String, Object> current;
    private static volatile boolean serverReady;
    private static volatile FormTextureUtils.ColorSetting serverColor;
    private static volatile Throwable failure;
    private static boolean starting, finished;
    private static int stage, ticks, index, checks;
    private static String worldName;

    private static FormTextureUtils.ColorSetting palette(int color) {
        return new FormTextureUtils.ColorSetting(color, color, color, color, color, false, false, false);
    }

    private static FormTextureUtils.ColorSetting distinctPalette() {
        return new FormTextureUtils.ColorSetting(0xffe04040, 0xff30b060, 0xff4060d0,
                0xffffcc00, 0xff20ccff, false, false, false);
    }

    private static boolean repairedForm() {
        Identifier id = forms.get(index).getFormID();
        return !id.equals(FormIdentifiers.AXOLOTL_ALING) && !id.equals(FormIdentifiers.FAMILIAR_FOX_RED)
                && !id.equals(FormIdentifiers.SHEEP_FORM);
    }

    private static FormTextureUtils.ColorSetting abgr(FormTextureUtils.ColorSetting color) {
        return new FormTextureUtils.ColorSetting(FormTextureUtils.ARGB2ABGR(color.primaryColor()),
                FormTextureUtils.ARGB2ABGR(color.accentColor1()), FormTextureUtils.ARGB2ABGR(color.accentColor2()),
                FormTextureUtils.ARGB2ABGR(color.eyeColorA()), FormTextureUtils.ARGB2ABGR(color.eyeColorB()),
                color.primaryGreyReverse(), color.accent1GreyReverse(), color.accent2GreyReverse());
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message + " form=" + index + " stage=" + stage);
    }

    private static Object field(String name) throws Exception {
        Field field = FormColorSelectMenuV2.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(menu);
    }

    private static Path folder() throws Exception {
        Path folder = OUT.resolve(String.format("%02d-%s", index, forms.get(index).getFormID().toString().replace(':', '-')));
        Files.createDirectories(folder);
        return folder;
    }

    private static void shot(MinecraftClient client, String name) throws Exception {
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(folder().resolve(name + ".png"));
        }
    }

    private static void click(int x, int y) {
        check(menu.mouseClicked(x, y, 0), "Real menu click accepted");
        menu.mouseReleased(x, y, 0);
    }

    private static void enterAllColors(int color) throws Exception {
        enterColors(palette(color));
    }

    private static void enterColors(FormTextureUtils.ColorSetting colors) throws Exception {
        int[] values = {colors.primaryColor(), colors.accentColor1(), colors.accentColor2(), colors.eyeColorA(), colors.eyeColorB()};
        int x = menu.width / 2 - 210, y = menu.height / 2 - 113;
        for (int channel = 0; channel < 5; channel++) {
            click(x + 290, y + 44 + 15 * channel);
            check((int) field("SliderIndex") == channel, "Clicked editor for requested channel");
            TextFieldWidget hex = (TextFieldWidget) field("SliderTextBox");
            check(hex.visible && hex.active, "Color hex field is available");
            hex.setText(String.format("#%08X", values[channel]));
            ButtonWidget enabled = (ButtonWidget) field("SliderAButton");
            check(enabled.visible, "Layer toggle is displayed");
            click(x + 280, y + 196);
        }
        check(menu.getColorSetting(false).equals(colors), "All five real editor fields retain their input");
    }

    private static int[] pixels(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) pixels[y * image.getWidth() + x] = image.getColor(x, y);
        }
        return pixels;
    }

    private static Map<String, boolean[]> bonePixels(FormModel model, boolean slim, int width, int height) {
        Map<String, boolean[]> bones = new LinkedHashMap<>();
        for (GeoBone bone : model.getBakedModel(model.getModelResource(slim)).topLevelBones()) {
            markBone(bone, width, height, bones);
        }
        return bones;
    }

    private static void markBone(GeoBone bone, int width, int height, Map<String, boolean[]> bones) {
        boolean[] covered = new boolean[width * height];
        for (var cube : bone.getCubes()) {
            if (cube.quads() == null) continue;
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                var a = quad.vertices()[0].position();
                var b = quad.vertices()[1].position();
                var c = quad.vertices()[2].position();
                double abx = b.x() - a.x(), aby = b.y() - a.y(), abz = b.z() - a.z();
                double acx = c.x() - a.x(), acy = c.y() - a.y(), acz = c.z() - a.z();
                double nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
                if (nx * nx + ny * ny + nz * nz < 1.0e-16) continue;
                double minU = Double.POSITIVE_INFINITY, minV = Double.POSITIVE_INFINITY;
                double maxU = Double.NEGATIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
                for (var vertex : quad.vertices()) {
                    minU = Math.min(minU, vertex.texU() * width); maxU = Math.max(maxU, vertex.texU() * width);
                    minV = Math.min(minV, vertex.texV() * height); maxV = Math.max(maxV, vertex.texV() * height);
                }
                boolean pointU = maxU - minU < 0.001, pointV = maxV - minV < 0.001;
                int x0 = (int)Math.floor(minU + (pointU ? 0 : 0.0001));
                int y0 = (int)Math.floor(minV + (pointV ? 0 : 0.0001));
                int x1 = pointU ? x0 + 1 : (int)Math.ceil(maxU - 0.0001);
                int y1 = pointV ? y0 + 1 : (int)Math.ceil(maxV - 0.0001);
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    covered[Math.floorMod(y, height) * width + Math.floorMod(x, width)] = true;
                }
            }
        }
        if (!bone.getCubes().isEmpty()) bones.put(bone.getName(), covered);
        for (GeoBone child : bone.getChildBones()) markBone(child, width, height, bones);
    }

    private static Map<String, Object> coverageLayer(FormModel model, boolean slim, String name,
                                                    Identifier texture, Identifier mask) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", name); result.put("texture", texture.toString());
        result.put("mask", mask == null ? null : mask.toString());
        if (REQUIRE_COMPLETE && repairedForm()) check(mask != null, "Every material layer has a color mask");
        try (NativeImage original = FormTextureUtils.toNativeImage(texture)) {
            check(original != null, "Original layer loads");
            int w = original.getWidth(), h = original.getHeight();
            int[] base = pixels(original), pink = base.clone(), green = base.clone();
            original.writeTo(folder().resolve(name + "-original.png"));
            if (mask != null) {
                try (var p = FormTextureUtils.BakeTextureNoMemLeak(texture, mask, abgr(palette(MAGENTA)), model.UseMultiplyMask);
                     var g = FormTextureUtils.BakeTextureNoMemLeak(texture, mask, abgr(palette(GREEN)), model.UseMultiplyMask)) {
                    pink = pixels(p.getImage()); green = pixels(g.getImage());
                    p.getImage().writeTo(folder().resolve(name + "-magenta.png"));
                    g.getImage().writeTo(folder().resolve(name + "-green.png"));
                }
                try (var disabled = FormTextureUtils.BakeTextureNoMemLeak(texture, mask,
                        abgr(palette(0x00ffffff)), model.UseMultiplyMask)) {
                    check(Arrays.equals(base, pixels(disabled.getImage())), "Disabling all color layers preserves original appearance");
                }
            }
            boolean geo = name.equals("texture") || name.equals("fullbright_texture");
            Map<String, boolean[]> bones = geo ? bonePixels(model, slim, w, h) : new LinkedHashMap<>();
            boolean[] used = new boolean[w * h];
            if (geo) for (boolean[] bone : bones.values()) {
                for (int i = 0; i < used.length; i++) used[i] |= bone[i];
            } else Arrays.fill(used, true);
            int visible = 0, meshVisible = 0, meshFixed = 0, noMask = 0;
            int[] rawMask = null;
            if (mask != null) try (NativeImage image = FormTextureUtils.toNativeImage(mask)) { rawMask = pixels(image); }
            List<List<Integer>> fixedCoordinates = new ArrayList<>();
            boolean[] fixed = new boolean[w * h];
            for (int i = 0; i < base.length; i++) {
                if ((base[i] >>> 24) == 0) continue;
                visible++;
                if (!used[i]) continue;
                meshVisible++;
                fixed[i] = base[i] == pink[i] && base[i] == green[i];
                if (fixed[i]) {
                    meshFixed++;
                    fixedCoordinates.add(List.of(i % w, i / w));
                    if (rawMask == null || (rawMask[i] >>> 24) != 1 && (rawMask[i] >>> 24) != 2
                            && (rawMask[i] & 0x00ffffff) == 0) noMask++;
                }
            }
            List<Map<String, Object>> byBone = new ArrayList<>();
            for (var bone : bones.entrySet()) {
                int shown = 0, unchanged = 0;
                for (int i = 0; i < base.length; i++) if (bone.getValue()[i] && (base[i] >>> 24) > 0) {
                    shown++; if (fixed[i]) unchanged++;
                }
                if (unchanged > 0) byBone.add(Map.of("bone", bone.getKey(), "visible_uv_pixels", shown, "unchanged_uv_pixels", unchanged));
            }
            byBone.sort((a, b) -> Integer.compare((int)b.get("unchanged_uv_pixels"), (int)a.get("unchanged_uv_pixels")));
            result.put("texture_visible_pixels", visible); result.put("mesh_used_visible_pixels", meshVisible);
            result.put("unchanged_used_pixels_in_both_colors", meshFixed); result.put("unchanged_due_to_missing_mask", noMask);
            result.put("unchanged_uv_coordinates", fixedCoordinates); result.put("bones_with_unchanged_parts", byBone);
            int[] channels = new int[5];
            if (rawMask != null) for (int i = 0; i < base.length; i++) {
                if (!used[i] || base[i] >>> 24 == 0) continue;
                int m = rawMask[i];
                int channel = m >>> 24 == 1 ? 3 : m >>> 24 == 2 ? 4 : (m & 0xff0000) != 0 ? 2
                        : (m & 0xff00) != 0 ? 1 : (m & 0xff) != 0 ? 0 : -1;
                if (channel >= 0) channels[channel]++;
            }
            result.put("used_pixels_by_channel", channels);
            if (REQUIRE_COMPLETE && repairedForm()) check(noMask == 0, "No visible model UV pixel retains an unassigned original material: " + texture);
        }
        return result;
    }

    private static FormModel model(MinecraftClient client) {
        IForm form = forms.get(index);
        var layer = form.getRenderLayerOverride() == null ? form.getFormLayer() : form.getRenderLayerOverride();
        var renderer = FormRenderUtils.getFormRenderer(layer.getLeft(), layer.getRight());
        if (renderer == null) return null;
        renderer.setPlayer(client.player, client.player.getModel().equals("slim"));
        return renderer.realModel;
    }

    private static Identifier[] textures(FormModel model, boolean slim) {
        return slim ? new Identifier[]{model.TextureResource_Slim, model.OverlayTextureResource_Slim,
                model.FullBrightTextureResource_Slim, model.EmissiveTextureResource_Slim}
                : new Identifier[]{model.TextureResource, model.OverlayTextureResource,
                model.FullBrightTextureResource, model.EmissiveTextureResource};
    }

    private static Identifier[] masks(FormModel model, boolean slim) {
        return slim ? new Identifier[]{model.TextureMaskResource_Slim, model.OverlayTextureMaskResource_Slim,
                model.FullBrightTextureMaskResource_Slim, model.EmissiveTextureMaskResource_Slim}
                : new Identifier[]{model.TextureMaskResource, model.OverlayTextureMaskResource,
                model.FullBrightTextureMaskResource, model.EmissiveTextureMaskResource};
    }

    private static void coverage(MinecraftClient client) throws Exception {
        FormModel model = model(client);
        List<Map<String, Object>> layers = new ArrayList<>();
        if (model != null) {
            boolean slim = model.useSlim(client.player.getModel().equals("slim"));
            Identifier[] textures = textures(model, slim), masks = masks(model, slim);
            String[] names = {"texture", "overlay", "fullbright_texture", "emissive_overlay"};
            for (int l = 0; l < names.length; l++) {
                if (textures[l] == null || textures[l].getPath().equals("textures/missing.png")) continue;
                layers.add(coverageLayer(model, slim, names[l], textures[l], masks[l]));
            }
        }
        current.put("layers", layers);
        if (REQUIRE_COMPLETE && repairedForm()) {
            int[] channels = new int[5];
            for (var layer : layers) {
                int[] counts = (int[]) layer.get("used_pixels_by_channel");
                for (int i = 0; i < 5; i++) channels[i] += counts[i];
            }
            for (int i = 0; i < 5; i++) check(channels[i] > 0, "Every color slot controls visible model pixels: slot " + i);
        }
    }

    private static void verifyRenderedTextures(MinecraftClient client, int color, String key) {
        verifyRenderedTextures(client, palette(color), key);
    }

    private static void verifyRenderedTextures(MinecraftClient client, FormTextureUtils.ColorSetting color, String key) {
        FormModel model = model(client);
        if (model == null) { current.put(key, "no_ssc_model"); return; }
        boolean slim = model.useSlim(client.player.getModel().equals("slim"));
        Identifier[] sources = textures(model, slim), masks = masks(model, slim);
        Identifier[] actual = {model.getTextureResource(slim), model.getOverlayTextureResource(slim),
                model.getFullBrightTextureResource(slim), model.getEmissiveTextureResource(slim)};
        int verified = 0;
        for (int layer = 0; layer < actual.length; layer++) {
            if (sources[layer] == null || sources[layer].getPath().equals("textures/missing.png")) continue;
            if (masks[layer] == null) check(sources[layer].equals(actual[layer]), "Unmasked layer retains source identifier");
            else {
                check(client.getTextureManager().getTexture(actual[layer]) instanceof NativeImageBackedTexture,
                        "Renderer resolves a real baked custom texture");
                var rendered = (NativeImageBackedTexture) client.getTextureManager().getTexture(actual[layer]);
                try (var expected = FormTextureUtils.BakeTextureNoMemLeak(sources[layer], masks[layer],
                        abgr(color), model.UseMultiplyMask)) {
                    check(Arrays.equals(pixels(rendered.getImage()), pixels(expected.getImage())),
                            "Renderer custom texture matches the selected palette");
                }
            }
            verified++;
        }
        current.put(key, verified);
        Identifier form = forms.get(index).getFormID();
        if (REQUIRE_COMPLETE && (form.equals(FormIdentifiers.ALLAY_SP) || form.equals(FormIdentifiers.FALLEN_ALLAY_SP))) {
            Identifier skin = client.player.getSkinTexture();
            check(skin.getPath().contains("ssca_allay_skin"), "Exposed allay skin uses the working/saved palette: " + skin);
            String skinKey = key.replace("_layers_verified", "_skin");
            current.put(skinKey, skin.toString());
            if (key.startsWith("saved_")) check(skin.toString().equals(current.get(skinKey.replace("saved_", "preview_"))),
                    "Exposed skin uses the same texture in preview and after saving");
        }
    }

    private static void verifyGreenFramebuffer(MinecraftClient client) {
        try (NativeImage frame = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            int scale = frame.getHeight() / client.currentScreen.height;
            int unexpected = 0;
            for (int half = 0; half < 2; half++) {
                int y0 = half * frame.getHeight() / 2 + 60 * scale;
                int y1 = (half + 1) * frame.getHeight() / 2 - 5 * scale;
                for (int y = y0; y < y1; y++) for (int x = 0; x < frame.getWidth(); x++) {
                    int pixel = frame.getColor(x, y);
                    if ((pixel & 255) > 100 && (pixel >>> 16 & 255) > 100) unexpected++;
                }
            }
            current.put("unexpected_original_color_framebuffer_pixels", unexpected);
            check(unexpected == 0, "Rendered green model has no bright original-color pixels, including point-UV claws and skin cuffs");
        }
    }

    private static void switchForm(MinecraftClient client) {
        serverReady = false; serverColor = null;
        Identifier id = forms.get(index).getFormID();
        client.setScreen(null);
        client.getServer().execute(() -> {
            try {
                var player = client.getServer().getPlayerManager().getPlayerList().get(0);
                player.clearStatusEffects();
                player.getInventory().clear();
                if (FormIdentifiers.SHEEP_FORM.equals(id)) player.addStatusEffect(new StatusEffectInstance(SscAddon.SHEEP_FORM, 12000));
                else FormUtils._setForm(player, RegPlayerForms.getPlayerForm(id));
                var skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
                skin.setKeepOriginalSkin(false); skin.setEnableFormColor(true);
                skin.setFormColor(abgr(palette(0x00ffffff)));
                RegPlayerSkinComponent.SKIN_SETTINGS.sync(player);
                player.getAbilities().allowFlying = true;
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
                serverReady = true;
            } catch (Throwable error) { failure = error; }
        });
    }

    private static void sampleServerColor(MinecraftClient client) {
        serverColor = null;
        client.getServer().execute(() -> {
            try {
                var player = client.getServer().getPlayerManager().getPlayerList().get(0);
                serverColor = RegPlayerSkinComponent.SKIN_SETTINGS.get(player).getFormColor();
            } catch (Throwable error) { failure = error; }
        });
    }

    private static void next(int nextStage) { stage = nextStage; ticks = 0; }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server); state.tutorialPolicyInitialized = true; state.tutorialAutoEnabled = false;
            server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
            server.getOverworld().setTimeOfDay(6000);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (failure != null) throw new AssertionError("Server-side fixture failed", failure);
                if (System.nanoTime() - START > 1_200_000_000_000L) throw new AssertionError("Coverage run timed out");
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true; Files.createDirectories(OUT);
                    client.options.pauseOnLostFocus = false;
                    client.options.getMaxFps().setValue(60); client.options.getViewDistance().setValue(3);
                    worldName = "Form-Color-Coverage-" + System.currentTimeMillis();
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                            DataConfiguration.SAFE_MODE.enabledFeatures());
                    client.createIntegratedServerLoader().createAndStart(worldName,
                            new LevelInfo("Full form color coverage", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), data), new GeneratorOptions(2026110702L, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (client.player == null || client.world == null) return;
                ticks++;
                if (ticks % 200 == 0) System.out.println("COLOR_COVERAGE_WAIT stage=" + stage + " form=" + index
                        + " current=" + FormUtils.getPlayerForm(client.player).getFormID());
                switch (stage) {
                    case 0 -> {
                        if (ticks < 60) return;
                        ShapeShifterCurseFabric.clientConfig.disableUnlockCheckInFormColorSelectMenu = true;
                        forms = RegPlayerForms.playerForms.values().stream()
                                .filter(form -> form.getFormID().getNamespace().equals("my_addon")).toList();
                        switchForm(client); next(1);
                    }
                    case 1 -> {
                        if (!serverReady || ticks < 10 || !FormUtils.getPlayerForm(client.player).getFormID().equals(forms.get(index).getFormID())) return;
                        current = new LinkedHashMap<>(); current.put("form", forms.get(index).getFormID().toString());
                        current.put("name", forms.get(index).getContentText(CodexData.ContentType.NAME).getString());
                        current.put("actual_transformation_verified", true);
                        current.put("original_skin", client.player.getSkinTexture().toString());
                        menu = new FormColorSelectMenuV2(Text.literal("Full body color check")); client.setScreen(menu);
                        check(menu.getForm().getFormID().equals(forms.get(index).getFormID()), "Menu opens on the actual transformed form");
                        coverage(client); next(2);
                    }
                    case 2 -> {
                        if (ticks < 4) return;
                        shot(client, "01-original-menu");
                        if (REQUIRE_COMPLETE && repairedForm()) {
                            enterColors(distinctPalette());
                            check(menu.getColorSetting(true).equals(abgr(distinctPalette())), "Five-slot preview preserves eye A/B and RGB/ABGR ordering");
                            next(12);
                        } else { enterAllColors(MAGENTA); next(3); }
                    }
                    case 3 -> {
                        if (ticks < 4) return;
                        verifyRenderedTextures(client, MAGENTA, "preview_magenta_layers_verified");
                        shot(client, "02-magenta-menu"); menu.close(); next(4);
                    }
                    case 4 -> { if (ticks >= 8) { sampleServerColor(client); next(5); } }
                    case 5 -> {
                        if (serverColor == null || ticks < 4) return;
                        check(serverColor.equals(abgr(palette(MAGENTA))), "Magenta palette reaches the actual server player");
                        check(RegPlayerSkinComponent.SKIN_SETTINGS.get(client.player).getFormColor().equals(serverColor), "Saved magenta palette synchronizes to client");
                        check(!FormTextureUtils.useTempFormTexture && !FormTextureUtils.useTempFormModel, "Closing menu releases preview overrides");
                        verifyRenderedTextures(client, MAGENTA, "saved_magenta_layers_verified");
                        client.setScreen(new ViewsScreen("保存后：洋红色")); next(6);
                    }
                    case 6 -> {
                        if (ticks < 4) return;
                        shot(client, "03-magenta-saved-four-views"); client.setScreen(null);
                        client.options.setPerspective(Perspective.THIRD_PERSON_FRONT); next(7);
                    }
                    case 7 -> {
                        if (ticks < 4) return;
                        shot(client, "04-magenta-saved-world");
                        menu = new FormColorSelectMenuV2(Text.literal("Saved color read back")); client.setScreen(menu);
                        check(menu.getColorSetting(false).equals(palette(MAGENTA)), "Reopened menu retains the saved five-slot palette");
                        enterAllColors(GREEN); next(8);
                    }
                    case 8 -> {
                        if (ticks < 4) return;
                        verifyRenderedTextures(client, GREEN, "preview_green_layers_verified");
                        shot(client, "05-green-menu"); menu.close(); next(9);
                    }
                    case 9 -> { if (ticks >= 8) { sampleServerColor(client); next(10); } }
                    case 10 -> {
                        if (serverColor == null || ticks < 4) return;
                        check(serverColor.equals(abgr(palette(GREEN))), "Green palette reaches actual server player");
                        check(RegPlayerSkinComponent.SKIN_SETTINGS.get(client.player).getFormColor().equals(serverColor), "Saved green palette synchronizes to client");
                        verifyRenderedTextures(client, GREEN, "saved_green_layers_verified");
                        client.setScreen(new ViewsScreen("保存后：绿色")); next(11);
                    }
                    case 11 -> {
                        if (ticks < 4) return;
                        if (REQUIRE_COMPLETE && repairedForm()) verifyGreenFramebuffer(client);
                        shot(client, "06-green-saved-four-views");
                        current.put("save_roundtrip_verified", true); RESULTS.add(current);
                        Files.writeString(folder().resolve("coverage.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(current));
                        System.out.println("COLOR_COVERAGE_FORM_COMPLETE " + forms.get(index).getFormID());
                        client.setScreen(null);
                        index++;
                        if (index < forms.size()) { switchForm(client); next(1); }
                        else {
                            var report = Map.of("completed", true, "scope", "SSCA my_addon forms only", "registered_forms", forms.size(), "checks", checks,
                                    "require_complete", REQUIRE_COMPLETE, "repaired_forms", REQUIRE_COMPLETE ? 20 : 0,
                                    "run_token", System.getProperty("ssca.colorCoverage.runToken", "manual"),
                                    "world", worldName, "palettes_argb", List.of("FFEE00DD", "FF00EE22"), "forms", RESULTS);
                            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
                            finished = true; System.out.println("COLOR_COVERAGE_COMPLETE checks=" + checks); client.scheduleStop();
                        }
                    }
                    case 12 -> {
                        if (ticks < 4) return;
                        verifyRenderedTextures(client, distinctPalette(), "preview_distinct_layers_verified");
                        shot(client, "01b-distinct-menu"); menu.close(); next(13);
                    }
                    case 13 -> { if (ticks >= 8) { sampleServerColor(client); next(14); } }
                    case 14 -> {
                        if (serverColor == null || ticks < 4) return;
                        check(serverColor.equals(abgr(distinctPalette())), "Five distinct colors reach the actual server player");
                        check(RegPlayerSkinComponent.SKIN_SETTINGS.get(client.player).getFormColor().equals(serverColor), "Saved distinct palette synchronizes to client");
                        verifyRenderedTextures(client, distinctPalette(), "saved_distinct_layers_verified");
                        current.put("distinct_palette_and_eyes_verified", true);
                        client.setScreen(new ViewsScreen("保存后：五色分区与两眼")); next(15);
                    }
                    case 15 -> {
                        if (ticks < 4) return;
                        shot(client, "01c-distinct-saved-four-views"); client.setScreen(null);
                        menu = new FormColorSelectMenuV2(Text.literal("Five-color read back")); client.setScreen(menu);
                        check(menu.getColorSetting(false).equals(distinctPalette()), "Reopened five-color editor retains saved values");
                        check(menu.getColorSetting(true).equals(abgr(distinctPalette())), "Reopened five-color preview matches the saved eye order");
                        if (forms.get(index).getFormID().equals(FormIdentifiers.ALLAY_SP)
                                || forms.get(index).getFormID().equals(FormIdentifiers.FALLEN_ALLAY_SP)) {
                            ButtonWidget enabled = (ButtonWidget) field("enableFormColorSystemButton");
                            click(enabled.getX() + 3, enabled.getY() + 3);
                            check(client.player.getSkinTexture().toString().equals(current.get("original_skin")), "Disabling form colors restores the original allay underlay");
                            click(enabled.getX() + 3, enabled.getY() + 3);
                            ButtonWidget keep = (ButtonWidget) field("keepCustomSkinButton");
                            click(keep.getX() + 3, keep.getY() + 3);
                            Identifier vanillaSkin = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getSkinTexture();
                            check(client.player.getSkinTexture().equals(vanillaSkin), "Keep-original-skin restores the player's own skin rather than SSC's base skin");
                            click(keep.getX() + 3, keep.getY() + 3);
                        }
                        enterAllColors(MAGENTA); next(3);
                    }
                    default -> throw new AssertionError("Unknown stage " + stage);
                }
            } catch (Throwable error) {
                failure = error; finished = true; error.printStackTrace();
                try { Files.writeString(OUT.resolve("failure.txt"), "form=" + index + " stage=" + stage + "\n" + error); }
                catch (Exception ignored) { }
                client.scheduleStop();
            }
        });
    }

    /** Renders the actual saved player through the normal inventory renderer from four angles. */
    private static final class ViewsScreen extends Screen {
        ViewsScreen(String title) { super(Text.literal(title)); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xff45505a);
            context.drawCenteredTextWithShadow(textRenderer, forms.get(index).getContentText(CodexData.ContentType.NAME), width / 2, 10, 0xffffff);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xffffff);
            String[] labels = {"正面", "右侧", "背面", "左侧"};
            var player = client.player;
            var mainHand = player.getMainHandStack();
            var offHand = player.getOffHandStack();
            // Form material inspection omits held items; world screenshots retain the real equipment.
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, net.minecraft.item.ItemStack.EMPTY);
            float yaw = player.getYaw(), pitch = player.getPitch(), body = player.bodyYaw, prevBody = player.prevBodyYaw;
            float head = player.headYaw, prevHead = player.prevHeadYaw;
            try {
                for (int view = 0; view < 4; view++) {
                    int cx = width * (view % 2 * 2 + 1) / 4;
                    int bottom = height * (view / 2 + 1) / 2 - 12;
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal(labels[view]), cx, bottom - height / 2 + 53, 0xffffff);
                    player.setYaw(180); player.setPitch(0); player.bodyYaw = 180; player.prevBodyYaw = 180;
                    player.headYaw = 180; player.prevHeadYaw = 180;
                    Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).rotateY(view * (float)Math.PI / 2);
                    int size = Math.min(65, height / 5);
                    InventoryScreen.drawEntity(context, cx, bottom, size, rotation, null, player);
                }
            } finally {
                player.setYaw(yaw); player.setPitch(pitch); player.bodyYaw = body; player.prevBodyYaw = prevBody;
                player.headYaw = head; player.prevHeadYaw = prevHead;
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, mainHand);
                player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, offHand);
            }
        }
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Color coverage client failed", failure);
        if (!finished) throw new AssertionError("Color coverage client stopped before finishing");
    }
}
