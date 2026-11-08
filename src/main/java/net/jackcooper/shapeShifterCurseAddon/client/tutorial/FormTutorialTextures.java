package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** The research table's actual PNG skin; preserve pixel borders when resizing the tutorial. */
final class FormTutorialTextures {
    private static final Identifier WINDOW = new Identifier("ssc_addon", "textures/gui/spell_research_background.png");
    private static final Identifier INSET = new Identifier("ssc_addon", "textures/gui/magic_slot_panel.png");
    private static final Identifier THUMB = new Identifier("ssc_addon", "textures/gui/scroll_thumb.png");

    private FormTutorialTextures() {}

    static void window(DrawContext context, int x, int y, int width, int height) {
        frame(context, WINDOW, 312, 212, x, y, width, height);
    }

    static void inset(DrawContext context, int x, int y, int width, int height) {
        frame(context, INSET, 131, 72, x, y, width, height);
    }

    /** Sample only blank interior pixels, so the research screen's own dividers/scrollbar are not copied. */
    private static void frame(DrawContext context, Identifier texture, int tw, int th,
                              int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        int edge = Math.min(3, Math.min(width, height) / 2);
        part(context, texture, tw, th, x + edge, y + edge, width - edge * 2, height - edge * 2, 8, 8, 1, 1);
        part(context, texture, tw, th, x, y, edge, edge, 0, 0, 3, 3);
        part(context, texture, tw, th, x + width - edge, y, edge, edge, tw - 3, 0, 3, 3);
        part(context, texture, tw, th, x, y + height - edge, edge, edge, 0, th - 3, 3, 3);
        part(context, texture, tw, th, x + width - edge, y + height - edge, edge, edge, tw - 3, th - 3, 3, 3);
        part(context, texture, tw, th, x + edge, y, width - edge * 2, edge, 8, 0, 1, 3);
        part(context, texture, tw, th, x + edge, y + height - edge, width - edge * 2, edge, 8, th - 3, 1, 3);
        part(context, texture, tw, th, x, y + edge, edge, height - edge * 2, 0, 8, 3, 1);
        part(context, texture, tw, th, x + width - edge, y + edge, edge, height - edge * 2, tw - 3, 8, 3, 1);
    }

    static void scroll(DrawContext context, int x, int y, int height, int thumbHeight, int offset) {
        inset(context, x, y, 6, height);
        int thumbY = y + offset;
        if (thumbHeight <= 13) {
            part(context, THUMB, 8, 13, x, thumbY, 6, thumbHeight, 0, 0, 8, 13);
            return;
        }
        // Grow the plain body while keeping the PNG's grip lines at their original pixel height.
        part(context, THUMB, 8, 13, x, thumbY, 6, thumbHeight, 0, 1, 8, 1);
        part(context, THUMB, 8, 13, x, thumbY, 6, 1, 0, 0, 8, 1);
        part(context, THUMB, 8, 13, x, thumbY + thumbHeight - 1, 6, 1, 0, 12, 8, 1);
        part(context, THUMB, 8, 13, x, thumbY + (thumbHeight - 9) / 2, 6, 9, 0, 2, 8, 9);
    }

    private static void part(DrawContext context, Identifier texture, int tw, int th,
                             int x, int y, int width, int height, int u, int v, int sw, int sh) {
        if (width > 0 && height > 0) context.drawTexture(texture, x, y, width, height, u, v, sw, sh, tw, th);
    }
}
