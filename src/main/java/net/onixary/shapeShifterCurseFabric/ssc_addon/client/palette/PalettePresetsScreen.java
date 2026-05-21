package net.onixary.shapeShifterCurseFabric.ssc_addon.client.palette;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.palette.PaletteCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 配色预设管理界面（v4：左上角固定预览 + 槽位列表可滚动）。
 *
 * 布局：
 *   左上角：固定 110×140 预览框（始终显示玩家当前形态），下方预览状态标签 + "查看其它形态预览" 入口
 *   右侧顶部：同步开关 + 复制当前形态配色（两个 160w 按钮）
 *   右侧主体：8 行可视的槽位滚动列表（共 20 行，鼠标滚轮上下滑）
 *   底部：状态行 + 关闭
 */
public class PalettePresetsScreen extends Screen {
    private final Screen parent;

    /** 每行的 widget 引用，便于滚动时统一更新 y/visible。 */
    private static final class SlotRow {
        TextFieldWidget nameField;
        TextFieldWidget codeField;
        ButtonWidget applyBtn;
        ButtonWidget exportBtn;
    }

    private final List<SlotRow> slotRows = new ArrayList<>();

    private Text statusLine = Text.empty();
    private int previewSlotIdx = -1;

    // === 布局常量 ===
    private static final int ROW_H        = 22;
    private static final int NAME_W       = 80;
    private static final int CODE_W       = 180;
    private static final int BTN_W        = 44;
    private static final int GAP          = 4;
    private static final int ROW_TOTAL_W  = NAME_W + GAP + CODE_W + GAP + BTN_W + GAP + BTN_W;

    // 左上角固定预览
    private static final int PREVIEW_X       = 12;
    private static final int PREVIEW_Y       = 24;
    private static final int PREVIEW_W       = 110;
    private static final int PREVIEW_H       = 140;
    private static final int PREVIEW_LABEL_Y = PREVIEW_Y + PREVIEW_H + 4;

    // 滚动列表视口
    private static final int VIEW_TOP_OFFSET = 60;
    private static final int VISIBLE_ROWS    = 8;

    private int viewportY;
    private int viewportH;
    private int viewportLeft;
    private int viewportRight;
    private int contentTotalH;
    private int maxScroll;
    private int scrollOffset = 0;

    public PalettePresetsScreen(Screen parent) {
        super(Text.translatable("text.ssc_addon.palette.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PalettePresetStore.get().reloadForCurrentScope();
        slotRows.clear();

        // 顶部右侧：同步开关 + 复制当前
        addDrawableChild(ButtonWidget.builder(getSyncBtnText(), btn -> {
            PalettePresetStore store = PalettePresetStore.get();
            store.setGlobalSync(!store.isGlobalSync());
            MinecraftClient.getInstance().setScreen(new PalettePresetsScreen(parent));
        }).size(160, 20).position(width - 330, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.palette.btn.export_current"),
                btn -> exportCurrent()).size(160, 20).position(width - 165, 22).build());

        // 计算滚动列表视口
        final int startX = PREVIEW_X + PREVIEW_W + 14;
        this.viewportLeft   = startX;
        this.viewportRight  = Math.min(width - 14, startX + ROW_TOTAL_W);
        this.viewportY      = VIEW_TOP_OFFSET;
        this.viewportH      = VISIBLE_ROWS * ROW_H;
        this.contentTotalH  = PalettePresetStore.SLOT_COUNT * ROW_H;
        this.maxScroll      = Math.max(0, contentTotalH - viewportH);
        this.scrollOffset   = Math.min(scrollOffset, maxScroll);

        // 槽位行
        List<PalettePresetStore.Slot> slots = PalettePresetStore.get().getSlots();
        int baseY = viewportY;
        for (int i = 0; i < PalettePresetStore.SLOT_COUNT; i++) {
            final int idx = i;
            int x = startX;
            int y = baseY + i * ROW_H;
            SlotRow row = new SlotRow();

            row.nameField = new TextFieldWidget(this.textRenderer, x, y, NAME_W, 18,
                    Text.translatable("text.ssc_addon.palette.slot_name_placeholder"));
            row.nameField.setMaxLength(24);
            row.nameField.setText(slots.get(idx).name);
            row.nameField.setChangedListener(s -> {
                PalettePresetStore.get().getSlots().get(idx).name = s;
                PalettePresetStore.get().save();
            });
            addDrawableChild(row.nameField);
            x += NAME_W + GAP;

            row.codeField = new TextFieldWidget(this.textRenderer, x, y, CODE_W, 18,
                    Text.translatable("text.ssc_addon.palette.code_placeholder"));
            row.codeField.setMaxLength(64);
            row.codeField.setText(slots.get(idx).code);
            row.codeField.setChangedListener(s -> onCodeFieldChanged(idx, s, row.codeField));
            addDrawableChild(row.codeField);
            x += CODE_W + GAP;

            row.applyBtn = ButtonWidget.builder(Text.translatable("text.ssc_addon.palette.btn.apply"),
                    b -> doApply(idx)).size(BTN_W, 20).position(x, y - 1).build();
            addDrawableChild(row.applyBtn);
            x += BTN_W + GAP;

            row.exportBtn = ButtonWidget.builder(Text.translatable("text.ssc_addon.palette.btn.export"),
                    b -> doExport(idx)).size(BTN_W, 20).position(x, y - 1).build();
            addDrawableChild(row.exportBtn);

            slotRows.add(row);
        }

        // 底部：关闭 + 批量导出全部
        int closeY = viewportY + viewportH + 28;
        int closeX = (width - 120) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.config.close"),
            b -> close()).size(120, 20).position(closeX, closeY).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.palette.btn.export_all"),
            btn -> exportAllToFile()).size(BTN_W * 2 + GAP, 20).position(closeX + 120 + GAP, closeY).build());

        updateRowPositions();
    }

    /** 根据 scrollOffset 同步槽位 widget 的 y 与 visible。 */
    private void updateRowPositions() {
        int baseY = viewportY;
        int top = viewportY;
        int bottom = viewportY + viewportH;
        for (int i = 0; i < slotRows.size(); i++) {
            int y = baseY + i * ROW_H - scrollOffset;
            SlotRow row = slotRows.get(i);
            row.nameField.setY(y);
            row.codeField.setY(y);
            row.applyBtn.setY(y - 1);
            row.exportBtn.setY(y - 1);
            boolean visible = y + 18 > top && y < bottom;
            setRowVisible(row, visible);
        }
    }

    private static void setRowVisible(SlotRow row, boolean visible) {
        row.nameField.visible = visible;
        row.nameField.active = visible;
        row.codeField.visible = visible;
        row.codeField.active = visible;
        row.applyBtn.visible = visible;
        row.applyBtn.active = visible;
        row.exportBtn.visible = visible;
        row.exportBtn.active = visible;
    }

    // === 业务逻辑 ===

    private void onCodeFieldChanged(int idx, String text, TextFieldWidget field) {
        String trimmed = text == null ? "" : text.trim();
        PalettePresetStore.Slot slot = PalettePresetStore.get().getSlots().get(idx);
        if (trimmed.isEmpty()) {
            slot.code = "";
            PalettePresetStore.get().save();
            field.setEditableColor(0xFFFFFF);
            return;
        }
        try {
            PaletteCodec.decode(trimmed);
            slot.code = trimmed.toUpperCase();
            if (slot.name == null || slot.name.isEmpty()) {
                slot.name = "Preset " + (idx + 1);
                if (idx < slotRows.size()) slotRows.get(idx).nameField.setText(slot.name);
            }
            PalettePresetStore.get().save();
            field.setEditableColor(0xFFFFFF);
            setStatus("text.ssc_addon.palette.status.auto_saved", idx + 1);
        } catch (PaletteCodec.DecodeException e) {
            field.setEditableColor(0xFF5555);
        }
    }

    private void doApply(int idx) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || p.networkHandler == null) { setStatus("text.ssc_addon.palette.status.no_player"); return; }
        PalettePresetStore.Slot slot = PalettePresetStore.get().getSlots().get(idx);
        if (slot.isEmpty()) { setStatus("text.ssc_addon.palette.status.empty_slot"); return; }
        PaletteCodec.PaletteData data;
        try { data = PaletteCodec.decode(slot.code); }
        catch (PaletteCodec.DecodeException e) { setStatus("text.ssc_addon.palette.status.invalid_code"); return; }
        PaletteConfigSync.apply(data);
        p.networkHandler.sendChatCommand("ssc_addon palette apply " + slot.code);
        setStatus("text.ssc_addon.palette.status.apply_sent", idx + 1);
    }

    private void doExport(int idx) {
        PalettePresetStore.Slot slot = PalettePresetStore.get().getSlots().get(idx);
        if (slot.isEmpty()) { setStatus("text.ssc_addon.palette.status.empty_slot"); return; }
        MinecraftClient.getInstance().keyboard.setClipboard(slot.code);
        setStatus("text.ssc_addon.palette.status.exported", idx + 1);
    }

    /** 一键导出 UI 中所有槽位到 config/ssca/palette_presets_export.json，并复制路径到剪贴板。 */
    private void exportAllToFile() {
        java.nio.file.Path p = PalettePresetStore.get().exportAllToSharedFile();
        if (p == null) { setStatus("text.ssc_addon.palette.status.export_all_failed"); return; }
        MinecraftClient.getInstance().keyboard.setClipboard(p.toString());
        setStatus("text.ssc_addon.palette.status.export_all_ok", p.toString());
    }

    private void exportCurrent() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) { setStatus("text.ssc_addon.palette.status.no_player"); return; }
        try {
            PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
            FormTextureUtils.ColorSetting cs = skin.getFormColor();
            String code = PaletteCodec.encode(
                    FormTextureUtils.ABGR2RGBA(cs.getPrimaryColor()),
                    FormTextureUtils.ABGR2RGBA(cs.getAccentColor1()),
                    FormTextureUtils.ABGR2RGBA(cs.getAccentColor2()),
                    FormTextureUtils.ABGR2RGBA(cs.getEyeColorA()),
                    FormTextureUtils.ABGR2RGBA(cs.getEyeColorB()),
                    cs.getPrimaryGreyReverse(), cs.getAccent1GreyReverse(), cs.getAccent2GreyReverse());
            MinecraftClient.getInstance().keyboard.setClipboard(code);
            setStatus("text.ssc_addon.palette.status.current_exported");
        } catch (RuntimeException e) {
            setStatus("text.ssc_addon.palette.status.record_failed");
        }
    }

    private void setStatus(String key, Object... args) { this.statusLine = Text.translatable(key, args); }

    private Text getSyncBtnText() {
        PalettePresetStore store = PalettePresetStore.get();
        Text state = Text.translatable(store.isGlobalSync()
                ? "text.ssc_addon.palette.sync.global"
                : "text.ssc_addon.palette.sync.per_scope");
        return Text.translatable("text.ssc_addon.palette.sync.label", state);
    }

    /** 焦点行的 code（无焦点或为空 → null = 玩家当前真实配色）。 */
    private String getPreviewCode() {
        if (previewSlotIdx < 0) return null;
        return PalettePresetStore.get().getSlots().get(previewSlotIdx).code;
    }

    private void updatePreviewSelection() {
        int newIdx = -1;
        for (int i = 0; i < slotRows.size(); i++) {
            SlotRow r = slotRows.get(i);
            if (!r.nameField.visible) continue;
            if (r.nameField.isFocused() || r.codeField.isFocused()) { newIdx = i; break; }
        }
        if (newIdx >= 0) {
            String code = PalettePresetStore.get().getSlots().get(newIdx).code;
            if (code == null || code.isEmpty()) newIdx = -1;
        }
        this.previewSlotIdx = newIdx;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= viewportLeft && mouseX <= viewportRight
                && mouseY >= viewportY && mouseY <= viewportY + viewportH
                && maxScroll > 0) {
            int delta = (int) (-amount * ROW_H);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
            updateRowPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        updateRowPositions();
        updatePreviewSelection();
        super.render(context, mouseX, mouseY, delta);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // 左上角预览框（始终显示当前形态，焦点行决定预览的是哪个 code）
        context.fill(PREVIEW_X, PREVIEW_Y, PREVIEW_X + PREVIEW_W, PREVIEW_Y + PREVIEW_H, 0x88000000);
        context.drawBorder(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, 0xFFAAAAAA);

        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) {
            int cx = PREVIEW_X + PREVIEW_W / 2;
            int cy = PREVIEW_Y + PREVIEW_H - 12;
            PalettePreviewRenderer.render(context, cx, cy, 38,
                    cx - mouseX, cy - 50 - mouseY, p, getPreviewCode());
        }

        // 预览标签
        Text previewLabel = previewSlotIdx >= 0
                ? Text.translatable("text.ssc_addon.palette.preview.slot", previewSlotIdx + 1)
                : Text.translatable("text.ssc_addon.palette.preview.current");
        context.drawCenteredTextWithShadow(this.textRenderer, previewLabel,
                PREVIEW_X + PREVIEW_W / 2, PREVIEW_LABEL_Y, 0xCCCCCC);

        // 列标题
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc_addon.palette.header.name"),
                viewportLeft + 4, viewportY - 12, 0xFFFFAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc_addon.palette.header.code"),
                viewportLeft + NAME_W + GAP + 4, viewportY - 12, 0xFFFFAA);

        // 滚动条
        if (maxScroll > 0) {
            int trackX = viewportRight + 4;
            int trackW = 4;
            context.fill(trackX, viewportY, trackX + trackW, viewportY + viewportH, 0x44FFFFFF);
            int thumbH = Math.max(20, viewportH * viewportH / contentTotalH);
            int thumbY = viewportY + (viewportH - thumbH) * scrollOffset / maxScroll;
            context.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, 0xFFAAAAAA);
        }

        // 状态行
        if (statusLine != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusLine,
                    this.width / 2, viewportY + viewportH + 8, 0xAAFFAA);
        }
    }
}
