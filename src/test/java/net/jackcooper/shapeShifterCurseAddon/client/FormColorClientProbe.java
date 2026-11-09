package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
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
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;

/** Audits the real V2 menu and native texture baker in a disposable client/world. */
public final class FormColorClientProbe {
    private static final Path OUT = Path.of("form-color-audit");
    private static final long START = System.nanoTime();
    private static final List<Map<String, Object>> FORMS = new ArrayList<>();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static List<IForm> forms;
    private static FormColorSelectMenuV2 menu;
    private static boolean starting, finished;
    private static volatile boolean serverSaveChecked;
    private static volatile FormTextureUtils.ColorSetting savedOnServer;
    private static Throwable failure;
    private static int ticks, stage, index;

    private static FormTextureUtils.ColorSetting savePalette() {
        return new FormTextureUtils.ColorSetting(0xffab2345, 0xff16bc78, 0xff3456dc,
                0xffed1234, 0xff2468ac, false, true, false);
    }

    private static FormTextureUtils.ColorSetting toABGR(FormTextureUtils.ColorSetting c) {
        return new FormTextureUtils.ColorSetting(FormTextureUtils.ARGB2ABGR(c.primaryColor()),
                FormTextureUtils.ARGB2ABGR(c.accentColor1()), FormTextureUtils.ARGB2ABGR(c.accentColor2()),
                FormTextureUtils.ARGB2ABGR(c.eyeColorA()), FormTextureUtils.ARGB2ABGR(c.eyeColorB()),
                c.primaryGreyReverse(), c.accent1GreyReverse(), c.accent2GreyReverse());
    }

    private static Field field(String name) throws Exception {
        Field result = FormColorSelectMenuV2.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static FormTextureUtils.ColorSetting colors(int active) {
        int[] c = {0x00ffffff, 0x00ffffff, 0x00ffffff, 0x00ffffff, 0x00ffffff};
        if (active >= 0) c[active] = 0xffe03090;
        return new FormTextureUtils.ColorSetting(c[0], c[1], c[2], c[3], c[4], false, false, false);
    }

    private static void shot(MinecraftClient client, String name) throws Exception {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(OUT.resolve(name));
        }
    }

    private static void click(MinecraftClient client, ButtonWidget button) {
        menu.mouseClicked(button.getX() + button.getWidth() / 2d,
                button.getY() + button.getHeight() / 2d, 0);
        menu.mouseReleased(button.getX() + button.getWidth() / 2d,
                button.getY() + button.getHeight() / 2d, 0);
    }

    private static void select(int n) throws Exception {
        field("formIDIndex").setInt(menu, n);
        menu.reloadFormIDName();
    }

    private static int[] pixels(NativeImage image) {
        int[] result = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                result[y * image.getWidth() + x] = image.getColor(x, y);
            }
        }
        return result;
    }

    private static Map<String, Object> layer(String name, Identifier texture, Identifier mask,
                                             FormModel model) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layer", name);
        result.put("texture", texture.toString());
        result.put("mask", mask == null ? null : mask.toString());
        int[] changed = new int[5];
        int visible;
        try (NativeImage original = FormTextureUtils.toNativeImage(texture)) {
            if (original == null) throw new AssertionError("Missing texture " + texture);
            int[] base = pixels(original);
            visible = 0;
            for (int color : base) if ((color >>> 24) > 0) visible++;
            if (mask != null) {
                for (int channel = 0; channel < 5; channel++) {
                    menu.loadData(colors(channel));
                    try (var baked = FormTextureUtils.BakeTextureNoMemLeak(
                            texture, mask, menu.getColorSetting(true), model.UseMultiplyMask)) {
                        int[] actual = pixels(baked.getImage());
                        for (int p = 0; p < base.length; p++) {
                            if ((base[p] >>> 24) > 0 && base[p] != actual[p]) changed[channel]++;
                        }
                    }
                }
                menu.loadData(colors(-1));
                try (var baked = FormTextureUtils.BakeTextureNoMemLeak(
                        texture, mask, menu.getColorSetting(true), model.UseMultiplyMask)) {
                    if (!java.util.Arrays.equals(base, pixels(baked.getImage()))) {
                        throw new AssertionError("Disabled channels alter original texture: " + texture);
                    }
                }
            }
        }
        result.put("visible_pixels", visible);
        result.put("changed_pixels_by_menu_channel", changed);
        return result;
    }

    private static Map<String, Object> auditForm(MinecraftClient client, IForm form) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("form", form.getFormID().toString());
        result.put("render_form", menu.getLayerID().toString());
        var renderer = FormRenderUtils.getFormRenderer(new Identifier("origins", "origin"), menu.getLayerID());
        if (renderer == null) {
            result.put("no_ssc_model", true);
            return result;
        }
        FormModel model = renderer.realModel;
        List<Map<String, Object>> layers = new ArrayList<>();
        for (boolean slim : new boolean[]{false, true}) {
            if (slim && model.WideOnly || !slim && model.SlimOnly) continue;
            String suffix = slim ? "_slim" : "";
            Identifier[] textures = slim
                    ? new Identifier[]{model.TextureResource_Slim, model.OverlayTextureResource_Slim,
                        model.FullBrightTextureResource_Slim, model.EmissiveTextureResource_Slim}
                    : new Identifier[]{model.TextureResource, model.OverlayTextureResource,
                        model.FullBrightTextureResource, model.EmissiveTextureResource};
            Identifier[] masks = slim
                    ? new Identifier[]{model.TextureMaskResource_Slim, model.OverlayTextureMaskResource_Slim,
                        model.FullBrightTextureMaskResource_Slim, model.EmissiveTextureMaskResource_Slim}
                    : new Identifier[]{model.TextureMaskResource, model.OverlayTextureMaskResource,
                        model.FullBrightTextureMaskResource, model.EmissiveTextureMaskResource};
            String[] names = {"texture", "overlay", "fullbright_texture", "emissive_overlay"};
            for (int l = 0; l < textures.length; l++) {
                if (textures[l] == null || textures[l].getPath().equals("textures/missing.png")) continue;
                layers.add(layer(names[l] + suffix, textures[l], masks[l], model));
            }
        }
        result.put("layers", layers);
        menu.loadData(new FormTextureUtils.ColorSetting(0xffd44b46, 0xff45cf69, 0xff5174df,
                0xffffbc23, 0xffea34e8, false, false, false));
        return result;
    }

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server);
            state.tutorialPolicyInitialized = true;
            state.tutorialAutoEnabled = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 300_000_000_000L) throw new AssertionError("Color audit timeout at " + stage);
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true;
                    Files.createDirectories(OUT);
                    client.options.pauseOnLostFocus = false;
                    client.options.getMaxFps().setValue(60);
                    client.options.getViewDistance().setValue(3);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                            DataConfiguration.SAFE_MODE.enabledFeatures());
                    client.createIntegratedServerLoader().createAndStart("Form-Color-" + System.currentTimeMillis(),
                            new LevelInfo("Form color audit", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), data), new GeneratorOptions(20261107L, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (client.player == null || client.world == null) return;
                ticks++;
                if (stage == 0 && ticks > 60) {
                    ShapeShifterCurseFabric.clientConfig.disableUnlockCheckInFormColorSelectMenu = true;
                    forms = new ArrayList<>(RegPlayerForms.playerForms.values());
                    REPORT.put("registered_forms", forms.size());
                    REPORT.put("channel_order", List.of("primary", "accent1", "accent2", "eyeA", "eyeB"));
                    menu = new FormColorSelectMenuV2(Text.literal("Form color audit"));
                    field("enableFormColorSystem").setBoolean(menu, true);
                    field("keepCustomSkin").setBoolean(menu, false);
                    client.setScreen(menu);
                    menu.loadData(new FormTextureUtils.ColorSetting(0xffab2345, 0xff16bc78, 0xff3456dc,
                            0xffed1234, 0xff2468ac, false, false, false));
                    var actual = menu.getColorSetting(true);
                    REPORT.put("eye_a_matches_argb", actual.eyeColorA() == FormTextureUtils.ARGB2ABGR(0xffed1234));
                    REPORT.put("eye_b_matches_argb", actual.eyeColorB() == FormTextureUtils.ARGB2ABGR(0xff2468ac));
                    index = 0;
                    while (index < forms.size() && !forms.get(index).getFormID().equals(
                            net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ANUBIS_WOLF_SP)) index++;
                    if (index == forms.size()) throw new AssertionError("Reported Anubis form is missing");
                    select(index);
                    menu.loadData(colors(-1));
                    menu.openPanel(0);
                    menu.realOpenClosePanel();
                    ((TextFieldWidget) field("SliderRTextBox").get(menu)).setText("30");
                    REPORT.put("rgb_edit_leaves_disabled_layer_disabled", (menu.getColorSetting(false).primaryColor() >>> 24) == 0);
                    stage = 1; ticks = 0;
                } else if (stage == 1 && ticks >= 5) {
                    shot(client, "anubis-primary-disabled.png");
                    click(client, (ButtonWidget) field("SliderAButton").get(menu));
                    REPORT.put("layer_button_enables_color", (menu.getColorSetting(false).primaryColor() >>> 24) == 255);
                    stage = 2; ticks = 0;
                } else if (stage == 2 && ticks >= 5) {
                    shot(client, "anubis-primary-enabled.png");
                    menu.closePanel(); menu.realOpenClosePanel();
                    index = 0; stage = 3; ticks = 0;
                } else if (stage == 3) {
                    select(index);
                    FORMS.add(auditForm(client, forms.get(index)));
                    System.out.println("FORM_COLOR_AUDIT form=" + forms.get(index).getFormID());
                    stage = 4; ticks = 0;
                } else if (stage == 4 && ticks >= 4) {
                    shot(client, String.format("%02d-%s.png", index, forms.get(index).getFormID().toString().replace(':', '-')));
                    index++;
                    if (index < forms.size()) { stage = 3; ticks = 0; }
                    else {
                        menu.loadData(savePalette());
                        menu.close();
                        stage = 5; ticks = 0;
                    }
                } else if (stage == 5 && ticks >= 20) {
                    client.getServer().execute(() -> {
                        var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                        savedOnServer = net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent
                                .SKIN_SETTINGS.get(player).getFormColor();
                        serverSaveChecked = true;
                    });
                    stage = 6; ticks = 0;
                } else if (stage == 6 && serverSaveChecked && ticks >= 10) {
                    var expected = toABGR(savePalette());
                    REPORT.put("v2_save_reaches_server_in_correct_abgr_order", expected.equals(savedOnServer));
                    REPORT.put("v2_save_reaches_client_in_correct_abgr_order", expected.equals(
                            net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent
                                    .SKIN_SETTINGS.get(client.player).getFormColor()));
                    menu = new FormColorSelectMenuV2(Text.literal("Color save round trip"));
                    client.setScreen(menu);
                    REPORT.put("reopened_v2_argb_matches_saved_palette", savePalette().equals(menu.getColorSetting(false)));
                    REPORT.put("reopened_v2_preview_matches_saved_palette", expected.equals(menu.getColorSetting(true)));
                    REPORT.put("completed", true); REPORT.put("forms", FORMS);
                    Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(REPORT));
                    finished = true;
                    System.out.println("FORM_COLOR_AUDIT_COMPLETE forms=" + FORMS.size());
                    client.scheduleStop();
                }
            } catch (Throwable error) {
                failure = error; finished = true; error.printStackTrace();
                client.scheduleStop();
            }
        });
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Color audit execution failed", failure);
        if (!finished) throw new AssertionError("Color audit stopped before completion");
    }
}
