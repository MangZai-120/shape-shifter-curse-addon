package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shared painted presentation; starting a route and changing jobs keep their own actions. */
public abstract class EvolutionFormSelectionScreen extends Screen {
    protected static final int PANEL_W = 280, PANEL_H = 210;
    private static final int ICON_SIZE = 48;

    protected record FormPage(String routeId, String portrait, Text name, Text description) {
        public FormPage { }
    }

    protected EvolutionFormSelectionScreen(Text title) { super(title); }
    protected final int panelX() { return (width - PANEL_W) / 2; }
    protected final int panelY() { return (height - PANEL_H) / 2; }

    protected static String portraitFor(String routeId, String baseId) {
        return EvolutionArtwork.hasSprite(routeId) ? routeId : baseId == null ? "mana_system" : baseId;
    }

    protected final ButtonWidget paperButton(int x, int y, int w, int h, Text label,
                                            ButtonWidget.PressAction action, int arrow) {
        return new PaperButton(x, y, w, h, label, action, arrow);
    }

    protected final void renderFormPage(DrawContext ctx, int mouseX, int mouseY, float delta,
                                        int page, int count, FormPage form, Text status) {
        int px = panelX(), py = panelY();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        try {
            ctx.fill(0, 0, width, height, 0x32000000);
            EvolutionInk.paper(ctx, px, py, PANEL_W, PANEL_H);
            ctx.getMatrices().translate(0, 0, 1);
            if (form != null) EvolutionArtwork.selectionDecorations(ctx, form.routeId(), px, py, PANEL_W, PANEL_H);
            ctx.getMatrices().translate(0, 0, 10);
            centered(ctx, title, width / 2, py + 12, PANEL_W - 64);
            EvolutionInk.divider(ctx, px + 48, py + 36, PANEL_W - 96);
            if (form != null) {
                centered(ctx, Text.translatable("evolution.ssc_addon.start.select.page", page + 1, count),
                        width / 2, py + 25, 64);
                int iconTop = py + 44;
                EvolutionInk.frame(ctx, width / 2 - ICON_SIZE / 2 - 4, iconTop - 3, ICON_SIZE + 8, ICON_SIZE + 6);
                EvolutionInk.icon(ctx, form.portrait(), width / 2 - ICON_SIZE / 2, iconTop, ICON_SIZE);
                int nameWidth = Math.min(PANEL_W - 104, textRenderer.getWidth(plain(form.name())) + 20);
                EvolutionInk.plaque(ctx, width / 2 - nameWidth / 2, iconTop + ICON_SIZE + 5, nameWidth, 17);
                centered(ctx, form.name(), width / 2, iconTop + ICON_SIZE + 10, nameWidth - 16);

                var lines = textRenderer.wrapLines(Text.literal(plain(form.description())), PANEL_W - 100);
                int descTop = iconTop + ICON_SIZE + 27;
                int descBottom = py + PANEL_H - (status == null ? 60 : 70);
                float scale = Math.min(1, (descBottom - descTop) / (float)Math.max(1, lines.size() * 10));
                ctx.enableScissor(px + 48, descTop, px + PANEL_W - 48, descBottom);
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(width / 2f - textRenderer.getWidth(line) * scale / 2, descTop + i * 10 * scale, 0);
                    ctx.getMatrices().scale(scale, scale, 1);
                    ctx.drawText(textRenderer, line, 0, 0, EvolutionInk.INK, false);
                    ctx.getMatrices().pop();
                }
                ctx.disableScissor();
                if (status != null) centered(ctx, status, width / 2, py + PANEL_H - 68, PANEL_W - 100);
            }
            super.render(ctx, mouseX, mouseY, delta);
        } finally { ctx.draw(); ctx.getMatrices().pop(); }
    }

    private String plain(Text value) { return java.util.Objects.requireNonNullElse(Formatting.strip(value.getString()), ""); }
    private void centered(DrawContext ctx, Text value, int cx, int y, int maxWidth) {
        String text = plain(value);
        float scale = Math.min(1, maxWidth / (float)Math.max(1, textRenderer.getWidth(text)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - textRenderer.getWidth(text) * scale / 2, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, text, 0, 0, EvolutionInk.INK, false);
        ctx.getMatrices().pop();
    }

    private final class PaperButton extends ButtonWidget {
        private final int arrow;
        PaperButton(int x, int y, int w, int h, Text label, PressAction action, int arrow) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION_SUPPLIER);
            this.arrow = arrow;
        }
        @Override public void renderButton(DrawContext ctx, int mx, int my, float delta) {
            EvolutionInk.button(ctx, getX(), getY(), getWidth(), getHeight());
            if (active && (isHovered() || (arrow == 0 && isFocused()))) EvolutionInk.frame(ctx, getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4);
            if (arrow == 0) centered(ctx, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, getWidth() - 14);
            else {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(getX() + (arrow > 0 ? getWidth() : 0), getY() + (arrow > 0 ? getHeight() : 0), 0);
                // A half turn preserves the quad winding, so GUI face culling cannot hide the arrow.
                if (arrow > 0) ctx.getMatrices().scale(-1, -1, 1);
                EvolutionArtwork.draw(ctx, "back_arrow", (getWidth() - 14) / 2, (getHeight() - 14) / 2, 14);
                ctx.getMatrices().pop();
            }
            if (!active) for (int x = getX() + 5; x < getX() + getWidth() - 4; x += 4)
                ctx.fill(x, getY() + getHeight() - 4, x + 1, getY() + getHeight() - 3, EvolutionInk.INK);
        }
    }
}
