package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** GUI-only illustrations; never cast skills, create particles or send world packets. */
final class FormTutorialDiagram {
    private static final int INK = 0xffd2e4de, ACCENT = 0xff80c9d5, EFFECT = 0xffebb271;
    private static final ItemStack PLAYER = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack ENEMY = new ItemStack(Items.ZOMBIE_HEAD);
    private static final ItemStack SHIELD = new ItemStack(Items.SHIELD);
    private static final ItemStack WOLF = new ItemStack(Items.WOLF_SPAWN_EGG);
    private static final ItemStack VEX = new ItemStack(Items.VEX_SPAWN_EGG);
    private static final ItemStack FIRE = new ItemStack(Items.BLAZE_POWDER);
    private static final int[] MARK_COLORS = {0xffe7d45b, 0xffeaa252, 0xffe3767e};

    private FormTutorialDiagram() {}

    static void render(DrawContext c, TextRenderer font, MinecraftClient client, FormTutorialLessons.Lesson lesson,
                       int x, int y, int width, int height, float ticks) {
        FormTutorialTextures.inset(c, x, y, width, height);
        c.enableScissor(x + 3, y + 3, x + width - 3, y + height - 3);
        boolean tall = height > 120;
        int cy = y + height / 2;
        int start = x + Math.max(24, width / 7), end = x + width - Math.max(26, width / 7);
        int iconSize = tall ? 48 : height < 50 ? 22 : 32;
        int iconX = x + (width - iconSize) / 2;
        int iconY = tall ? y + 15 : cy - iconSize / 2 - 4;
        float phase = (ticks % 100) / 100f;
        if (tall) cy = y + height * 2 / 3;
        line(c, start + 12, cy, end - 12, cy, 0xff45636b);
        for (int i = 0; i < 3; i++) {
            int px = start + 18 + (int)((end - start - 35) * ((phase + i / 3f) % 1));
            c.fill(px, cy - 1, px + 3, cy + 2, ACCENT);
        }
        if (client.player != null) {
            // SSC's inventory renderer normalizes several form models independently of the entity hitbox.
            // Fit the drawing to the panel, rather than enlarging tiny hitboxes until their heads are clipped.
            int size = tall ? 30 : Math.max(10, Math.min(24, (height - 21) / 2));
            int lift = lesson.diagram() == FormTutorialLessons.Diagram.FLIGHT ? (int)(Math.sin(phase * Math.PI) * 10) : 0;
            int baseline = tall ? cy + 16 : y + height - 18;
            InventoryScreen.drawEntity(c, start, baseline - Math.min(lift, tall ? 10 : 3), size, -15, -2, client.player);
        }
        ItemStack summoned = lesson.id().equals("vex") || lesson.id().equals("bell_raid") ? VEX : WOLF;
        ItemStack subject = switch (lesson.focus()) {
            case TARGET -> ENEMY;
            case MINIONS -> summoned;
            case ALLY -> PLAYER;
            case SELF -> switch (lesson.diagram()) {
                case SHIELD -> SHIELD;
                case AREA -> ENEMY;
                default -> null;
            };
        };
        if (subject != null) item(c, subject, end - 10, cy - 13, 22);
        c.drawTexture(lesson.icon(), iconX, iconY, iconSize, iconSize, 0, 0, 32, 32, 32, 32);
        int pulse = 13 + (int)(phase * 8);
        int effectX = lesson.focus() == FormTutorialLessons.Focus.SELF ? start : end;
        switch (lesson.diagram()) {
            case PROJECTILE, SPELL -> {
                int px = start + 17 + (int)((end - start - 30) * phase);
                c.fill(px - 3, cy - 3, px + 3, cy + 3, EFFECT);
                if (phase > .8) ring(c, end, cy, pulse, EFFECT);
            }
            case AREA -> { ring(c, effectX, cy, pulse, EFFECT); ring(c, effectX, cy, Math.max(6, pulse - 5), ACCENT); }
            case DASH -> {
                int px = start + 12 + (int)((end - start - 20) * phase);
                for (int i = 0; i < 3; i++) line(c, px - 8 - i * 5, cy + 7, px - i * 5, cy - 7, ACCENT);
            }
            case HEAL -> { cross(c, end, cy - 19 - (int)(phase * 9), 0xff98dca4); cross(c, start, cy - 23, 0xff98dca4); }
            case SHIELD -> { ring(c, start, cy, 21, ACCENT); shield(c, end, cy, ACCENT); }
            case SUMMON -> {
                item(c, summoned, end - 19, cy + 5, 13);
                item(c, summoned, end + 5, cy + 5, 13);
                ring(c, end, cy + 10, 22, ACCENT);
            }
            case RESOURCE -> {
                int bx = start - 13, by = cy + 19, bw = end - start + 26;
                c.fill(bx, by, bx + bw, by + 5, 0xff45636b);
                c.fill(bx, by, bx + (int)(bw * (.25 + .7 * phase)), by + 5, ACCENT);
            }
            case STEALTH -> {
                for (int i = 0; i < 4; i++) {
                    int dx = (int)(Math.sin(phase * Math.PI * 2 + i) * 16);
                    c.fill(effectX + dx - 2, cy - 20 + i * 10, effectX + dx + 2, cy - 18 + i * 10, 0xff97a8d7);
                }
                ring(c, effectX, cy, 21, 0xff97a8d7);
            }
            case MARK -> {
                for (int i = 0; i < 3; i++) {
                    int bx = end - 14 + i * 11;
                    c.fill(bx, cy - 24, bx + 7, cy - 17, phase * 3 >= i ? MARK_COLORS[i] : 0xff45636b);
                }
            }
            case WEB -> {
                for (int i = -2; i <= 2; i++) {
                    line(c, end - 19, cy + i * 7, end + 19, cy - i * 7, INK);
                    line(c, end + i * 7, cy - 19, end - i * 7, cy + 19, INK);
                }
            }
            case FLIGHT -> {
                line(c, start - 17, cy + 6, start - 17, cy - 17, ACCENT);
                line(c, start - 17, cy - 17, start - 21, cy - 11, ACCENT);
                line(c, start - 17, cy - 17, start - 13, cy - 11, ACCENT);
            }
            case MELEE -> {
                for (int i = 0; i < 3; i++) line(c, end - 15 + i * 8, cy + 12, end - 4 + i * 8, cy - 12, EFFECT);
            }
            case EVOLUTION -> {
                for (int i = 0; i < 3; i++) {
                    int nx = start + (end - start) * i / 2;
                    c.fill(nx - 6, cy + 14, nx + 6, cy + 26, phase * 3 >= i ? ACCENT : 0xff45636b);
                }
            }
            case CHAIN -> {
                ring(c, end, cy, 16, EFFECT);
                line(c, end - 7, cy + 12, end - 24, cy + 23, ACCENT);
                c.fill(end - 28, cy + 20, end - 21, cy + 27, EFFECT);
            }
            case WEATHER -> {
                if (lesson.id().contains("moon") || lesson.id().startsWith("night")) {
                    ring(c, end, cy - 22, 8, ACCENT);
                    c.fill(end - 6, cy - 28, end + 5, cy - 16, ACCENT);
                    c.fill(end - 1, cy - 30, end + 7, cy - 20, 0xff414141);
                } else if (lesson.id().contains("cold") || lesson.id().contains("snow")) {
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI / 3;
                        int dx = (int)(Math.cos(a) * 10), dy = (int)(Math.sin(a) * 10);
                        line(c, end - dx, cy - 22 - dy, end + dx, cy - 22 + dy, ACCENT);
                    }
                } else if (lesson.id().equals("fire_fragile")) item(c, FIRE, end - 8, cy - 30, 16);
                else {
                    c.fill(end - 5, cy - 27, end + 5, cy - 17, 0xffe7d45b);
                    ring(c, end, cy - 22, 9, 0xffe7d45b);
                }
                c.fill(end - 18, cy + 18, end + 18, cy + 21, 0xff98dca4);
            }
        }
        if (height >= 42) {
            Text actor = FormTutorialScreen.text("diagram.you");
            Text result = FormTutorialScreen.text("diagram." + switch (lesson.focus()) {
                case SELF -> switch (lesson.diagram()) {
                    case AREA -> "nearby"; case SHIELD -> "defense"; case RESOURCE -> "resource";
                    case HEAL -> "healing"; case WEATHER -> "environment"; case FLIGHT -> "movement";
                    case EVOLUTION -> "unlock"; case STEALTH -> "hidden"; default -> "self";
                };
                case TARGET -> "target"; case ALLY -> "ally"; case MINIONS -> "minions";
            });
            int actorX = Math.max(x + 4 + font.getWidth(actor) / 2, Math.min(start, x + width - 4 - font.getWidth(actor) / 2));
            int resultX = Math.max(x + 4 + font.getWidth(result) / 2, Math.min(end, x + width - 4 - font.getWidth(result) / 2));
            c.drawCenteredTextWithShadow(font, actor, actorX, tall ? cy + 33 : y + height - 11, INK);
            c.drawCenteredTextWithShadow(font, result, resultX, tall ? cy + 33 : y + height - 11, INK);
        }
        if (tall) c.drawCenteredTextWithShadow(font, FormTutorialScreen.text("diagram.caption"), x + width / 2, y + iconSize + 24, INK);
        c.disableScissor();
    }

    private static void item(DrawContext c, ItemStack stack, int x, int y, int size) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(size / 16f, size / 16f, 1);
        c.drawItem(stack, 0, 0);
        c.getMatrices().pop();
    }

    private static void ring(DrawContext c, int x, int y, int radius, int color) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16;
            int px = x + (int)(Math.cos(a) * radius), py = y + (int)(Math.sin(a) * radius);
            c.fill(px, py, px + 2, py + 2, color);
        }
    }

    private static void shield(DrawContext c, int x, int y, int color) {
        line(c, x - 13, y - 16, x + 13, y - 16, color);
        line(c, x - 13, y - 16, x - 10, y + 8, color);
        line(c, x + 13, y - 16, x + 10, y + 8, color);
        line(c, x - 10, y + 8, x, y + 17, color);
        line(c, x + 10, y + 8, x, y + 17, color);
    }

    private static void cross(DrawContext c, int x, int y, int color) {
        c.fill(x - 2, y - 6, x + 2, y + 6, color);
        c.fill(x - 6, y - 2, x + 6, y + 2, color);
    }

    private static void line(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            c.fill(x, y, x + 1, y + 1, color);
        }
    }
}
