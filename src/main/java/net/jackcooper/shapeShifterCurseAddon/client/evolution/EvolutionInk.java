package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.minecraft.client.gui.DrawContext;

/** Single brown ink, painted skin elements, and Minecraft-rendered labels. */
final class EvolutionInk {
    static final int INK = 0xFF493621, PAPER = 0xFFE9D4A9;
    static boolean paperFailed;
    private EvolutionInk() { }
    static void paper(DrawContext ctx, int x, int y, int width, int height) {
        if (!EvolutionArtwork.image(ctx, "background_base", x, y, width, height)) {
            paperFailed = true;
            ctx.fill(x, y, x + width, y + height, PAPER);
        }
    }
    static void frame(DrawContext ctx, int x, int y, int w, int h) { EvolutionArtwork.nine(ctx, "node_frame", x, y, w, h); }
    static void selection(DrawContext ctx, int x, int y, int w, int h) { EvolutionArtwork.nine(ctx, "selected_frame", x, y, w, h); }
    static void plaque(DrawContext ctx, int x, int y, int w, int h) { EvolutionArtwork.nine(ctx, "label_frame", x, y, w, h); }
    static void button(DrawContext ctx, int x, int y, int w, int h) { EvolutionArtwork.nine(ctx, "confirm_frame", x, y, w, h); }
    static void line(DrawContext ctx, int x0, int y0, int x1, int y1) {
        boolean vertical = x0 == x1;
        int length = vertical ? Math.abs(y1 - y0) : Math.abs(x1 - x0);
        int startX = Math.min(x0, x1), startY = Math.min(y0, y1), previous = 0;
        // A continuous pixel nib under the painted echo prevents long scaled strokes from breaking up.
        for (int i = 0; i <= length; i++) {
            int offset = i < 3 || i > length - 3 ? 0 : switch ((i / 11) % 9) {
                case 2, 3 -> 1;
                case 6 -> -1;
                default -> 0;
            };
            if (vertical) ctx.fill(startX + Math.min(previous, offset), startY + i, startX + Math.max(previous, offset) + 1, startY + i + 1, INK);
            else ctx.fill(startX + i, startY + Math.min(previous, offset), startX + i + 1, startY + Math.max(previous, offset) + 1, INK);
            previous = offset;
        }
        if (vertical) EvolutionArtwork.stretch(ctx, "line_vertical", x0 - 1, startY, 3, Math.max(1, length + 1));
        else EvolutionArtwork.stretch(ctx, "line_horizontal", startX, y0 - 1, Math.max(1, length + 1), 3);
    }
    static void boundary(DrawContext ctx, int left, int y, int width) { EvolutionArtwork.stretch(ctx, "point_boundary", left, y - 1, width, 4); }
    static void divider(DrawContext ctx, int left, int y, int width) {
        int gap = Math.min(20, width / 4), middle = left + width / 2;
        line(ctx, left, y, middle - gap / 2 - 2, y);
        line(ctx, middle + gap / 2 + 2, y, left + width, y);
        EvolutionArtwork.stretch(ctx, "divider_ornament", middle - gap / 2, y - 3, gap, 7);
    }
    static void check(DrawContext ctx, int x, int y, int size) {
        int side = Math.max(10, size / 3), left = x + size - side - 1, top = y + size - side - 1;
        ctx.fill(left, top, left + side, top + side, PAPER);
        EvolutionArtwork.draw(ctx, "unlocked_check", left, top, side);
    }
    static void icon(DrawContext ctx, String id, int x, int y, int size) { EvolutionArtwork.draw(ctx, id, x, y, size); }
    static void orb(DrawContext ctx, int x, int y, int size) { EvolutionArtwork.draw(ctx, "experience_orb", x, y, size); }
}
