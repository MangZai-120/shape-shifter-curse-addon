package net.jackcooper.shapeShifterCurseAddon.client.colorpicker;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** One editor and its child screens share the current working and preset textures. */
public final class ColorPreviewTextures implements FormTextureUtils.TempFormTextureProcessor {
    private static final Map<Screen, ColorPreviewTextures> screens = new WeakHashMap<>();
    private static ColorPreviewTextures active;
    private final Layers working = new Layers("working");
    private final Layers slot = new Layers("preset");
    private boolean drawingSlot;
    private AdvancedColorBridge.PreviewBackup backup;

    private ColorPreviewTextures() {}

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("ssc_addon", "color_preview"); }
            @Override public void reload(ResourceManager manager) {
                if (active != null) active.clearTextures();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (client.getNetworkHandler() == null || client.getNetworkHandler() == handler) changeScreen(null);
        }));
    }

    public static ColorPreviewTextures attach(Screen screen, Screen parent) {
        var session = screens.get(parent);
        if (session == null) session = new ColorPreviewTextures();
        screens.put(screen, session);
        return session;
    }

    public static void changeScreen(Screen next) {
        var session = screens.get(next);
        if (active == session) {
            if (active != null && next instanceof AdvancedColorScreen) active.slot.clear();
            return;
        }
        if (active != null) {
            AdvancedColorBridge.restoreFromBackup(active.backup);
            active.backup = null;
            active.clearTextures();
        }
        active = session;
    }

    public static ColorPreviewTextures active() { return active; }

    void beginEdit() {
        if (backup == null) backup = AdvancedColorBridge.backupPreview();
    }

    void endEdit(boolean save) {
        if (!save) AdvancedColorBridge.restoreFromBackup(backup);
        backup = null;
    }

    public static void drawPreset(boolean selected, Runnable draw) {
        var session = active;
        if (session == null) { draw.run(); return; }
        boolean previous = session.drawingSlot;
        session.drawingSlot = selected;
        if (!selected) session.slot.clear();
        try { draw.run(); } finally { session.drawingSlot = previous; }
    }

    private void clearTextures() {
        working.clear();
        slot.clear();
    }

    @Override
    public Identifier getTexture(int modelId, String category, Identifier texture, Identifier mask, boolean multiply) {
        var player = MinecraftClient.getInstance().player;
        var colors = player == null ? null : FormTextureUtils.getPlayerColorSetting(player);
        if (colors == null) {
            (drawingSlot ? slot : working).clear();
            return texture;
        }
        if (mask == null || texture == null) return texture;
        return texture(colors, texture, mask, multiply,
                () -> FormTextureUtils.BakeTextureNoMemLeak(texture, mask, colors, multiply));
    }

    public Identifier texture(FormTextureUtils.ColorSetting colors, Identifier source, Identifier mask, boolean multiply,
                              Supplier<NativeImageBackedTexture> bake) {
        var layers = drawingSlot ? slot : working;
        if (!colors.equals(layers.colors)) {
            layers.clear();
            layers.colors = colors;
        }
        var key = new Material(source, mask, multiply);
        var id = layers.textures.get(key);
        if (id != null) return id;
        var image = bake.get();
        if (image == null) return source;
        // Vanilla also memoizes render layers by identifier. Reuse the same slots across palette changes and visits.
        id = new Identifier("ssc_addon", "color_preview/" + layers.name + "/" + layers.textures.size());
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, image);
        layers.textures.put(key, id);
        return id;
    }

    private record Material(Identifier texture, Identifier mask, boolean multiply) {}

    private static final class Layers {
        final String name;
        FormTextureUtils.ColorSetting colors;
        final Map<Material, Identifier> textures = new HashMap<>();

        Layers(String name) { this.name = name; }

        void clear() {
            var manager = MinecraftClient.getInstance().getTextureManager();
            textures.values().forEach(manager::destroyTexture);
            textures.clear();
            colors = null;
        }
    }
}
