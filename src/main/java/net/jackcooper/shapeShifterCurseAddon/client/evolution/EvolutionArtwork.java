package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
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
            "branch_mancianima", "back_arrow", "snow_fox", "snow_fox_base", "running_jumps", "air_control",
            "snowball_throw", "ground_rebound", "falling_strike", "snowball_freeze", "super_dive", "rolling_charge",
            "snow_fox_branch_moon_ring", "snow_fox_branch_stone");
    static final Set<String> SKIN = Set.of("node_frame", "selected_frame", "label_frame", "confirm_frame",
            "scroll_track", "scroll_thumb", "scroll_grip", "line_horizontal", "line_vertical", "point_boundary",
            "experience_bar", "divider_ornament", "leaf_ornament");
    private static final Map<String, Graphic> TEXTURES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private record Graphic(Identifier texture, int width, int height, int left, int top, int w, int h) { }
    private EvolutionArtwork() { }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("ssc_addon", "evolution_artwork"); }
            @Override public void reload(ResourceManager manager) { MinecraftClient.getInstance().execute(EvolutionArtwork::clear); }
        });
    }
    static void clear() {
        var textures = MinecraftClient.getInstance().getTextureManager();
        TEXTURES.values().forEach(graphic -> textures.destroyTexture(graphic.texture));
        TEXTURES.clear(); FAILED.clear(); EvolutionInk.paperFailed = false;
    }
    static boolean hasSprite(String id) { return ICONS.contains(id); }
    static String nodeSprite(String routeId, String nodeId) {
        String formSprite = routeId + "_" + nodeId;
        return hasSprite(formSprite) ? formSprite : nodeId;
    }
    static boolean loaded(String id) { return TEXTURES.containsKey(id) && !FAILED.contains(id); }
    static String decoration(String route) { return "decorations/" + route; }
    /** Keep the selection page's centered content clear, using the existing painted side ornaments. */
    static void selectionDecorations(DrawContext context, String route, int x, int y, int width, int height) {
        String id = decoration(route);
        if (MinecraftClient.getInstance().getResourceManager().getResource(resource(id)).isEmpty()) return;
        Graphic g = texture(id);
        if (g == null) return;
        float margin = 100 * width / (float)g.width;
        region(context, g, x, y, margin, height, 0, 0, 100, g.height);
        float right = x + width - margin;
        if (route.equals("axolotl")) {
            // Move only the middle coral towards the paper edge; keep the lower cluster in place.
            float sy = height / (float)g.height;
            int bottom = CORAL_Y + CORAL_H;
            region(context, g, right, y, margin, CORAL_Y * sy, 430, 0, 100, CORAL_Y);
            region(context, g, right + 8 * width / (float)g.width, y + CORAL_Y * sy,
                    margin, CORAL_H * sy, 430, CORAL_Y, 100, CORAL_H);
            region(context, g, right, y + bottom * sy, margin, (g.height - bottom) * sy,
                    430, bottom, 100, g.height - bottom);
        } else region(context, g, right, y, margin, height, 430, 0, 100, g.height);
    }
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
        return new Identifier("ssc_addon", "textures/gui/evolution/" + folder + id + ".png");
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
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, g.texture);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        var matrix = context.getMatrices().peek().getPositionMatrix();
        var buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        int top = y, v = g.top;
        for (int row = 0; row < 3; row++) {
            int h = row == 1 ? height - 2 * dy : dy;
            int sh = row == 1 ? g.h - 2 * sy : sy;
            int left = x, u = g.left;
            for (int col = 0; col < 3; col++) {
                int w = col == 1 ? width - 2 * dx : dx;
                int sw = col == 1 ? g.w - 2 * sx : sx;
                if (w > 0 && h > 0 && sw > 0 && sh > 0) {
                    float u0 = (float)u / g.width, u1 = (float)(u + sw) / g.width;
                    float v0 = (float)v / g.height, v1 = (float)(v + sh) / g.height;
                    buffer.vertex(matrix, left, top, 0).texture(u0, v0).next();
                    buffer.vertex(matrix, left, top + h, 0).texture(u0, v1).next();
                    buffer.vertex(matrix, left + w, top + h, 0).texture(u1, v1).next();
                    buffer.vertex(matrix, left + w, top, 0).texture(u1, v0).next();
                }
                left += w;
                u += sw;
            }
            top += h;
            v += sh;
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
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
