package net.jackcooper.shapeShifterCurseAddon.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/** Three equal vanilla iron slots. The independent stability bar owns all cost/risk information. */
@Environment(EnvType.CLIENT)
public class SpellbookHudRenderer implements HudRenderCallback {
    /** Includes text that extends past the frame, so every zoom level remains draggable and visible. */
    public static int[] hudAnchor() {
        var mc=MinecraftClient.getInstance();var cfg=SSCAddonConfig.client();
        var p=UIPositionUtils.getCorrectPosition(cfg.spellbookHudPosType,cfg.spellbookHudPosOffsetX,cfg.spellbookHudPosOffsetY);
        return new int[]{clampX(p.getLeft(),mc.getWindow().getScaledWidth()),clampY(p.getRight(),mc.getWindow().getScaledHeight())};
    }
    public static HudScaleGeometry.Box scaleBounds() {
        var mc=MinecraftClient.getInstance();int[] a=hudAnchor();
        int left=a[0]+ORIGIN_X,right=left+WIDTH;
        var book=mc.player==null?ItemStack.EMPTY:SpellcastClient.getEquippedBook();
        if(mc.world!=null && book!=null && !book.isEmpty() && SpellbookData.getSlotCount(book)>0){
            int selected=Math.max(0,Math.min(SpellcastClient.getSelectedSlot(),SpellbookData.getSlotCount(book)-1));
            var snap=snapshotOf(mc,book,selected);int nameW=mc.textRenderer.getWidth(snap.label());
            if(nameW>0){int x=Math.max(3,Math.min(a[0]+CENTER_X-nameW/2,mc.getWindow().getScaledWidth()-nameW-3));left=Math.min(left,x-3);right=Math.max(right,x+nameW+3);}
        }
        return new HudScaleGeometry.Box(left,a[1]+0,right-left,HEIGHT);
    }
    public static final int DEFAULT_X = 26;
    public static final int DEFAULT_Y = -60;
    public static final int SLOT_SIZE = 24;
    public static final int ICON_SIZE = 16;
    public static final int SLOT_STEP = 26;
    private static final int SOURCE_WIDTH = 76;
    private static final int CENTER_X = SOURCE_WIDTH / 2;
    public static final float SLOT_SCALE = 1.2f;
    public static final int ORIGIN_X = (int) Math.floor(CENTER_X * (1 - SLOT_SCALE));
    public static final int WIDTH = (int) Math.ceil(SOURCE_WIDTH * SLOT_SCALE);
    private static final int NAME_Y = Math.round(SLOT_SIZE * SLOT_SCALE) + 4;
    public static final int HEIGHT = NAME_Y + 12;
    // Double the previously requested 2x2 display size; the PNG core remains exactly 3x3.
    private static final float MARK_SCALE = 4f / (3f * SLOT_SCALE);
    private static final int OUTLINE = 0xFF101416;
    private static final Identifier SLOT = texture("slot");
    private static final Identifier SELECTED = texture("slot-selected");
    private static final Identifier MARKS = texture("rarity-marks");
    private static final Identifier NAME_PLATE = texture("name-plate");
    private static final SpellbookHudRenderer BEFORE_CHAT = new SpellbookHudRenderer();

    private static Identifier texture(String name) {
        return new Identifier("ssca_neomagic", "textures/gui/spell_hud/" + name + ".png");
    }

    /** Called once by InGameHud, immediately before vanilla chat. */
    public static void renderBeforeChat(DrawContext context, float tickDelta) {
        BEFORE_CHAT.onHudRender(context, tickDelta);
    }

    private record SlotView(ItemStack scroll, Spell spell, int rarity, boolean modified,
                            float cooldownFraction, String cooldownText) {}
    private record TickSnapshot(SlotView previous, SlotView selected, SlotView next, Text label) {}
    private static final SlotView EMPTY = new SlotView(ItemStack.EMPTY, null, 0, false, 0, "");
    private static Object snapWorld;
    private static ItemStack snapBook;
    private static long snapTick = Long.MIN_VALUE;
    private static int snapSlot = -1;
    private static TickSnapshot snapshot;

    /** Cache scroll decoding, rune/formation queries and formatted labels once per game tick. */
    private static TickSnapshot snapshotOf(MinecraftClient mc, ItemStack book, int selected) {
        long tick = mc.world.getTime();
        if (snapWorld == mc.world && snapBook == book && snapSlot == selected && snapTick == tick
                && snapshot != null) return snapshot;
        int previous = SpellbookData.nextFilledSlot(book, selected, -1);
        int next = SpellbookData.nextFilledSlot(book, selected, 1);
        if (previous < 0 || next < 0) previous = next = selected;
        SlotView current = slotView(mc, book, SpellbookData.getScroll(book, selected));
        var label = current.spell() == null ? Text.empty() : SpellCastFeedback.spellName(current.spell(), current.scroll()).copy();
        if (current.spell() != null) {
            int castLevel = ScrollData.getCastLevel(current.scroll());
            int scrollLevel = ScrollData.getLevel(current.scroll());
            if (castLevel < scrollLevel) label.append(Text.literal(" Lv" + castLevel + "/" + scrollLevel).formatted(Formatting.AQUA));
        }
        snapshot = new TickSnapshot(slotView(mc, book, SpellbookData.getScroll(book, previous)), current,
                slotView(mc, book, SpellbookData.getScroll(book, next)), label);
        snapWorld = mc.world;
        snapBook = book;
        snapSlot = selected;
        snapTick = tick;
        return snapshot;
    }

    private static SlotView slotView(MinecraftClient mc, ItemStack book, ItemStack scroll) {
        if (scroll.isEmpty()) return EMPTY;
        Spell spell = ScrollData.getSpell(scroll);
        if (spell == null) return new SlotView(scroll, null, 0, false, 0, "");
        int rarity = spell.getRarity(ScrollData.getLevel(scroll)).ordinal();
        long remaining = Math.max(0, SharedSpellCooldowns.getEffectiveCooldownEnd(mc.player, scroll) - mc.world.getTime());
        float fraction = 0;
        if (remaining > 0) {
            int level = ScrollData.getCastLevel(scroll);
            float levelCd = spell.getBaseCooldownTicks() * spell.getCooldownMultiplier(level);
            int total = Math.max(Math.round(levelCd * (2 - ScrollData.getDurabilityRatio(scroll))
                            * FormationData.sumCooldownMultiplier(book, spell.getElement())),
                    Math.max(spell.getCooldownFloorTicks(), Math.round(levelCd * SpellNumbers.RELATIVE_CD_FLOOR)));
            total = RuneScheme.modifiers(mc.player, scroll, level).cooldown(total);
            int authoritative = SharedSpellCooldowns.getClientCooldownTotal(spell, ScrollData.getCooldownEnd(scroll),
                    scroll.getNbt() == null ? 0 : scroll.getNbt().getInt("RuneCdTotal"));
            if (authoritative > 0) total = authoritative;
            fraction = total > 0 ? Math.min(1, remaining / (float) total) : 1;
        }
        // Long cooldowns use whole seconds so the value fits inside a 24px slot.
        String time = remaining == 0 ? "" : remaining < 2000 ? SpellCastFeedback.seconds(remaining)
                : Long.toString((remaining + 19) / 20);
        return new SlotView(scroll, spell, rarity, RuneScheme.isModified(scroll), fraction, time);
    }

    /** The editor and renderer share dimensions and edge clamping. */
    public static int clampX(int x, int screenWidth) {
        return Math.max(-ORIGIN_X, Math.min(x, screenWidth - WIDTH - ORIGIN_X));
    }

    public static int clampY(int y, int screenHeight) {
        return Math.max(0, Math.min(y, screenHeight - HEIGHT));
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaling.render(context, 3, () -> renderUnscaled(context, tickDelta));
    }

    private void renderUnscaled(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden
                || !SSCAddonConfig.client().showSpellbookHud) return;
        ItemStack book = SpellcastClient.getEquippedBook();
        if (book == null || book.isEmpty() || SpellbookData.getSlotCount(book) <= 0) return;
        var cfg = SSCAddonConfig.client();
        var anchor = UIPositionUtils.getCorrectPosition(cfg.spellbookHudPosType,
                cfg.spellbookHudPosOffsetX, cfg.spellbookHudPosOffsetY);
        int selected = Math.max(0, Math.min(SpellcastClient.getSelectedSlot(), SpellbookData.getSlotCount(book) - 1));
        draw(context, clampX(anchor.getLeft(), mc.getWindow().getScaledWidth()),
                clampY(anchor.getRight(), mc.getWindow().getScaledHeight()), snapshotOf(mc, book, selected));
    }

    /** Actual textures in the position editor, also usable without a book or player. */
    public static void drawPreview(DrawContext context, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack book = mc.player == null ? ItemStack.EMPTY : SpellcastClient.getEquippedBook();
        if (mc.world != null && book != null && !book.isEmpty() && SpellbookData.getSlotCount(book) > 0) {
            int selected = Math.max(0, Math.min(SpellcastClient.getSelectedSlot(), SpellbookData.getSlotCount(book) - 1));
            draw(context, x, y, snapshotOf(mc, book, selected));
        } else draw(context, x, y, new TickSnapshot(EMPTY, EMPTY, EMPTY, Text.empty()));
    }

    private static void draw(DrawContext context, int x, int y, TickSnapshot data) {
        context.draw();
        float[] color = RenderSystem.getShaderColor().clone();
        SpellbookHudLayer.push(context.getMatrices());
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.getMatrices().push();
            try {
                // Grow around the existing center; all three frames share the same baseline.
                context.getMatrices().translate(x + CENTER_X, y, 0);
                context.getMatrices().scale(SLOT_SCALE, SLOT_SCALE, 1);
                context.getMatrices().translate(-CENTER_X, 0, 0);
                drawSlot(context, data.previous(), 0, 0, false);
                drawSlot(context, data.next(), 2 * SLOT_STEP, 0, false);
                drawSlot(context, data.selected(), SLOT_STEP, 0, true);
            } finally {
                context.getMatrices().pop();
            }
            // The name follows the taller row, retaining its original font and PNG backplate.
            if (!data.label().getString().isEmpty()) drawLabel(context, data.label(), x + CENTER_X, y + NAME_Y);
        } finally {
            try {
                context.draw();
            } finally {
                context.getMatrices().pop();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
        }
    }

    private static void drawSlot(DrawContext context, SlotView data, int x, int y, boolean selected) {
        context.drawTexture(selected ? SELECTED : SLOT, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
        if (data.scroll().isEmpty()) return;
        int iconX = x + 4, iconY = y + 4;
        Identifier icon = data.spell() == null ? null : data.spell().getIconTexture();
        if (icon == null) context.drawItem(data.scroll(), iconX, iconY);
        else context.drawTexture(icon, iconX, iconY, ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 260);
        try {
            if (data.cooldownFraction() > 0) {
                int maskHeight = (int) Math.ceil(ICON_SIZE * data.cooldownFraction());
                context.fill(iconX, iconY + ICON_SIZE - maskHeight, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xAA000000);
                var mc = MinecraftClient.getInstance();
                int textWidth = mc.textRenderer.getWidth(data.cooldownText());
                // 冷却数字缩为原来的 75%，长数字继续按图标内宽收敛，保留描边余量。
                float textScale = Math.min(0.75f, (ICON_SIZE - 2f) / Math.max(1, textWidth));
                context.getMatrices().push();
                try {
                    context.getMatrices().translate(x + (SLOT_SIZE - textWidth * textScale) / 2f,
                            y + (SLOT_SIZE - mc.textRenderer.fontHeight * textScale) / 2f, 0);
                    context.getMatrices().scale(textScale, textScale, 1);
                    drawOutlined(context, Text.literal(data.cooldownText()), 0, 0, 0xFFF3F7F1);
                } finally {
                    context.getMatrices().pop();
                }
            }
            if (data.spell() != null) {
                // Keep the requested +1 right / +2 down GUI-pixel offsets independent of row scaling.
                // Compensate for SLOT_SCALE so the core finishes at 4x4, independent of row size.
                context.getMatrices().push();
                try {
                    context.getMatrices().translate(x + 21 - 5 * MARK_SCALE + 1 / SLOT_SCALE,
                            y + 20 - 5 * MARK_SCALE + 2 / SLOT_SCALE, 0);
                    context.getMatrices().scale(MARK_SCALE, MARK_SCALE, 1);
                    context.drawTexture(MARKS, 0, 0, 5 * Math.max(0, Math.min(5, data.rarity())),
                            data.modified() ? 5 : 0, 5, 5, 30, 10);
                } finally {
                    context.getMatrices().pop();
                }
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void drawLabel(DrawContext context, Text label, int centerX, int y) {
        var mc = MinecraftClient.getInstance();
        int width = mc.textRenderer.getWidth(label);
        int x = Math.max(3, Math.min(centerX - width / 2, mc.getWindow().getScaledWidth() - width - 3));
        int plateX = x - 3, plateY = y - 2, plateWidth = width + 6, plateHeight = mc.textRenderer.fontHeight + 4;
        // Nine-slice the PNG. Only solid pixels stretch, keeping the stepped corners sharp.
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int dx = col == 0 ? 0 : col == 1 ? 1 : plateWidth - 1;
            int dy = row == 0 ? 0 : row == 1 ? 1 : plateHeight - 1;
            int w = col == 1 ? plateWidth - 2 : 1, h = row == 1 ? plateHeight - 2 : 1;
            context.drawTexture(NAME_PLATE, plateX + dx, plateY + dy, w, h, col, row, 1, 1, 3, 3);
        }
        drawOutlined(context, label, x, y, 0xFFF0F6F2);
    }

    private static void drawOutlined(DrawContext context, Text text, int x, int y, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        // The outline is plain so formatted text retains a dark contour, including the blue level tag.
        String plain = text.getString();
        context.drawText(font, plain, x - 1, y, OUTLINE, false);
        context.drawText(font, plain, x + 1, y, OUTLINE, false);
        context.drawText(font, plain, x, y - 1, OUTLINE, false);
        context.drawText(font, plain, x, y + 1, OUTLINE, false);
        context.drawText(font, text, x, y, color, false);
    }
}
