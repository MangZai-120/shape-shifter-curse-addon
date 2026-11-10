package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.*;

/** Painted PNGs: one shared parchment, transparent route ornaments, and 64x64 icons. */
public final class EvolutionArtwork {
    static final int TILE = 64, INK_ABGR = 0xFF213649;
    private static final int CORAL_X = 470, CORAL_Y = 236, CORAL_W = 45, CORAL_H = 121;
    static final Set<String> ICONS = Set.of("axolotl", "axolotl_base", "aquatic_adapt", "water_propel",
            "water_spurt", "water_spear", "vortex_guide", "become_water", "branch_moon_ring", "branch_stone",
            "experience_orb", "unlocked_check", "familiar_fox", "familiar_fox_base", "mana_system", "spark",
            "rocket", "buff_immunity", "alchemy", "spirit_vision", "fire_ring", "branch_spirit_lord",
            "branch_mancianima", "back_arrow");
    static final Set<String> SKIN = Set.of("node_frame", "selected_frame", "label_frame", "confirm_frame",
            "scroll_track", "scroll_thumb", "scroll_grip", "line_horizontal", "line_vertical", "point_boundary",
            "experience_bar", "divider_ornament", "leaf_ornament");
    private static final Map<String, Graphic> TEXTURES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private record Graphic(Identifier texture, int width, int height, int left, int top, int w, int h) { }
    private EvolutionArtwork() { }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("my_addon", "evolution_artwork"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(EvolutionArtwork::clear); }
        });
    }
    static void clear() {
        var textures = MinecraftClient.getInstance().getTextureManager();
        TEXTURES.values().forEach(graphic -> textures.destroyTexture(graphic.texture));
        TEXTURES.clear(); FAILED.clear(); EvolutionInk.paperFailed = false;
    }
    static boolean hasSprite(String id) { return ICONS.contains(id); }
    static boolean loaded(String id) { return TEXTURES.containsKey(id) && !FAILED.contains(id); }
    static String decoration(String route) { return "decorations/" + route; }
    /** New routes supply only a transparent decorations/<route>.png on the shared 768x512 canvas. */
    static void decorations(DrawContext context, String route, int x, int y, int width, int height) {
        String id = decoration(route);
        if (MinecraftClient.getInstance().getResourceManager().getResource(resource(id)).isEmpty()) return;
        Graphic g = texture(id);
        if (g == null) return;
        if (!route.equals("axolotl")) {
            region(context, g, x, y, width, height, 0, 0, g.width, g.height);
            return;
        }
        // Keep all artwork in one route PNG. Leave the coral's source rectangle out
        // of this pass; attachedCoral draws that same region once against the rail.
        canvasRegion(context, g, x, y, width, height, 0, 0, g.width, CORAL_Y);
        canvasRegion(context, g, x, y, width, height, 0, CORAL_Y, CORAL_X, CORAL_H);
        canvasRegion(context, g, x, y, width, height, CORAL_X + CORAL_W, CORAL_Y,
                g.width - CORAL_X - CORAL_W, CORAL_H);
        canvasRegion(context, g, x, y, width, height, 0, CORAL_Y + CORAL_H,
                g.width, g.height - CORAL_Y - CORAL_H);
    }
    private static void canvasRegion(DrawContext context, Graphic g, int x, int y, int width, int height,
                                     int u, int v, int sw, int sh) {
        float sx = width / (float) g.width, sy = height / (float) g.height;
        region(context, g, x + u * sx, y + v * sy, sw * sx, sh * sy, u, v, sw, sh);
    }
    static Identifier resource(String id) {
        String folder = ICONS.contains(id) ? "icons/" : SKIN.contains(id) ? "skin/" : "";
        return new Identifier("my_addon", "textures/gui/evolution/" + folder + id + ".png");
    }
    static void draw(DrawContext context, String id, int x, int y, int size) {
        image(context, hasSprite(id) || SKIN.contains(id) ? id : "mana_system", x, y, size, size);
    }
    static boolean image(DrawContext context, String id, int x, int y, int width, int height) {
        Graphic graphic = texture(id);
        if (graphic == null) return false;
        region(context, graphic, x, y, width, height, 0, 0, graphic.width, graphic.height);
        return true;
    }
    /** Position the transparent coral against the rail, or at its authored position without a rail. */
    static void attachedCoral(DrawContext context, int x, int y, int width, int height, int railLeft) {
        Graphic g = texture(decoration("axolotl"));
        if (g == null) return;
        float sx = width / (float) g.width, sy = height / (float) g.height;
        float left = railLeft >= 0 ? railLeft - 43 * sx : x + CORAL_X * sx;
        region(context, g, left, y + CORAL_Y * sy, CORAL_W * sx, CORAL_H * sy,
                CORAL_X, CORAL_Y, CORAL_W, CORAL_H);
    }
    /** Keep thin painted ornaments at their intended stroke thickness. */
    static void stretch(DrawContext context, String id, int x, int y, int width, int height) {
        Graphic graphic = texture(id);
        if (graphic != null) region(context, graphic, x, y, width, height, graphic.left, graphic.top, graphic.w, graphic.h);
    }
    /** Thin rails keep their full source width; slicing horizontally would erase their single-pixel sides. */
    static void vertical(DrawContext context, String id, int x, int y, int width, int height) {
        Graphic g = texture(id);
        if (g == null || height < 1) return;
        int sourceCap = Math.min(6, g.h / 3), cap = Math.min(4, height / 3);
        region(context, g, x, y, width, cap, g.left, g.top, g.w, sourceCap);
        region(context, g, x, y + cap, width, height - cap * 2, g.left, g.top + sourceCap, g.w, g.h - sourceCap * 2);
        region(context, g, x, y + height - cap, width, cap, g.left, g.top + g.h - sourceCap, g.w, sourceCap);
    }
    /** Preserve painted octagonal corners while cards and scrollbars change size. */
    static void nine(DrawContext context, String id, int x, int y, int width, int height) {
        Graphic g = texture(id);
        if (g == null || width < 1 || height < 1) return;
        int sx = Math.min(12, g.w / 3), sy = Math.min(12, g.h / 3);
        int dx = Math.min(6, width / 3), dy = Math.min(6, height / 3);
        int[] sourceX = {g.left, g.left + sx, g.left + g.w - sx};
        int[] sourceY = {g.top, g.top + sy, g.top + g.h - sy};
        int[] sourceW = {sx, g.w - sx * 2, sx}, sourceH = {sy, g.h - sy * 2, sy};
        int[] destX = {x, x + dx, x + width - dx}, destY = {y, y + dy, y + height - dy};
        int[] destW = {dx, width - dx * 2, dx}, destH = {dy, height - dy * 2, dy};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            region(context, g, destX[col], destY[row], destW[col], destH[row], sourceX[col], sourceY[row], sourceW[col], sourceH[row]);
    }
    private static void region(DrawContext ctx, Graphic g, float x, float y, float width, float height, int u, int v, int sw, int sh) {
        if (width < 1 || height < 1 || sw < 1 || sh < 1) return;
        ctx.draw();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(width / (float)sw, height / (float)sh, 1);
        ctx.drawTexture(g.texture, 0, 0, u, v, sw, sh, g.width, g.height);
        ctx.getMatrices().pop();
        RenderSystem.disableBlend();
    }
    private static Graphic texture(String id) {
        if (TEXTURES.containsKey(id)) return TEXTURES.get(id);
        if (FAILED.contains(id)) return null;
        var client = MinecraftClient.getInstance();
        try (var stream = client.getResourceManager().getResource(resource(id)).orElseThrow().getInputStream();
             var source = NativeImage.read(stream)) {
            if ((ICONS.contains(id) || SKIN.contains(id)) && (source.getWidth() != TILE || source.getHeight() != TILE))
                throw new IOException("Evolution icon must be an authored 64x64 PNG: " + id);
            int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
            for (int py = 0; py < source.getHeight(); py++) for (int px = 0; px < source.getWidth(); px++) {
                if ((source.getColor(px, py) >>> 24) == 0) continue;
                left = Math.min(left, px); top = Math.min(top, py);
                right = Math.max(right, px); bottom = Math.max(bottom, py);
            }
            if (right < left) throw new IOException("Empty evolution PNG: " + id);
            NativeImage pixels = new NativeImage(source.getWidth(), source.getHeight(), false);
            pixels.copyFrom(source);
            var texture = new NativeImageBackedTexture(pixels);
            texture.setFilter(false, false);
            Identifier handle = client.getTextureManager().registerDynamicTexture("ssca_evolution_" + id, texture);
            Graphic graphic = new Graphic(handle, source.getWidth(), source.getHeight(), left, top, right - left + 1, bottom - top + 1);
            TEXTURES.put(id, graphic);
            return graphic;
        } catch (Exception exception) {
            FAILED.add(id);
            org.slf4j.LoggerFactory.getLogger(EvolutionArtwork.class).warn("Unable to load evolution PNG {}", id, exception);
            return null;
        }
    }
}
