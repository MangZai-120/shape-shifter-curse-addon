package net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

/**
 * 高级调色 GUI：
 *  - 顶部：标题 + 启用复选框
 *  - 第二行：5 个色块切换“当前编辑哪一项颜色”
 *  - 三栏并排：左 RGBA 文本框 / 中 R/G/B/A 滑条 / 右 HSV 全色图（共享同一 working color，全联动）
 *  - 中下：3 个 GreyReverse 复选框（独立常驻）
 *  - 下方居中：玩家预览框
 *  - 底部：保存 / 取消（带二次确认）
 *
 * 所有同步都走 AdvancedColorBridge；配色预设导出入功能已迁出附属包、
 * 路由到「幻型者诅咒原版」的玩家颜色自定义界面。
 */
public class AdvancedColorScreen extends Screen {
    private final Screen parent;

    private AdvancedColorBridge.WorkingState working;
    private AdvancedColorBridge.WorkingState initialSnapshot;
    private AdvancedColorBridge.PreviewBackup previewBackup;

    private int editingIdx = AdvancedColorBridge.IDX_PRIMARY;

    // UI 控件引用
    private final ButtonWidget[] colorTabs = new ButtonWidget[AdvancedColorBridge.COLOR_COUNT];
    private TextFieldWidget rField, gField, bField, aField, hexField;
    private RgbSlider sliderR, sliderG, sliderB, sliderA;
    private HsvPickerWidget hsvPicker;
    private CheckboxWidget[] grChecks = new CheckboxWidget[AdvancedColorBridge.GR_COUNT];
    private CheckboxWidget enableCheck;

    // 防止反向回填时触发 changed 回调形成循环
    private boolean suppressCallbacks = false;

    /** 状态提示行（复制成功 / 失败 / 保存成功 等）。 */
    private Text statusLine = Text.empty();
    /** 状态行设置时间（ms），用于渐隐。 */
    private long statusSetTimeMs = 0L;
    /** 渐隐参数：5s 后开始 fade，2s 完成。 */
    private static final long STATUS_HOLD_MS = 5000L;
    private static final long STATUS_FADE_MS = 2000L;

    private void setStatus(Text t) {
        this.statusLine = t;
        this.statusSetTimeMs = System.currentTimeMillis();
    }

    public AdvancedColorScreen(Screen parent) {
        super(Text.translatable("text.ssc_addon.adv_color.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 首次进入时备份 + 取 snapshot
        if (working == null) {
            this.initialSnapshot = AdvancedColorBridge.snapshotFromConfig();
            this.working = initialSnapshot.copy();
            this.previewBackup = AdvancedColorBridge.backupPreview();
            AdvancedColorBridge.applyPreview(working); // 进入时立即把工作态推到本地预览
        }

        // ====== 顶部：启用开关 + 5 个色块切换 ======
        int topY = 26;
        enableCheck = new CheckboxWidget(12, topY, 20, 20,
                Text.translatable("text.ssc_addon.adv_color.enable"), working.enabled) {
            @Override
            public void onPress() {
                super.onPress();
                working.enabled = this.isChecked();
                AdvancedColorBridge.applyPreview(working);
            }
        };
        addDrawableChild(enableCheck);

        // 5 个色块：在顶部右侧
        int swatchSize = 22;
        int swatchGap  = 6;
        int swatchTotalW = AdvancedColorBridge.COLOR_COUNT * swatchSize + (AdvancedColorBridge.COLOR_COUNT - 1) * swatchGap;
        int swatchStartX = (width - swatchTotalW) / 2;
        for (int i = 0; i < AdvancedColorBridge.COLOR_COUNT; i++) {
            final int idx = i;
            int sx = swatchStartX + i * (swatchSize + swatchGap);
            ButtonWidget btn = ButtonWidget.builder(Text.literal(""), b -> {
                editingIdx = idx;
                refreshAllFromWorking();
            }).size(swatchSize, swatchSize).position(sx, topY).build();
            colorTabs[i] = btn;
            addDrawableChild(btn);
        }

        // ====== 三栏并排 ======
        // 中栏顶部嵌入预览框（在 R/G/B/A 滑条上方），所以 colH 需要更大
        int colTop = 70;
        int colH   = 170;

        // 列宽估算：保留左右各 12px 边距 + 中间两个 14px gap
        int totalAvail = width - 24;
        int gapBetween = 14;
        int colW = (totalAvail - 2 * gapBetween) / 3;

        int leftX  = 12;
        int midX   = leftX + colW + gapBetween;
        int rightX = midX + colW + gapBetween;

        // ---- 左栏：RGBA 文本框 + Hex ----
        int lineY = colTop + 14;
        int labelW = 18;
        int fieldW = Math.min(60, colW - labelW - 4);
        rField = makeNumField(leftX + labelW, lineY, fieldW, 0, 255, c -> updateColorChannel(0, c));
        gField = makeNumField(leftX + labelW, lineY + 22, fieldW, 0, 255, c -> updateColorChannel(1, c));
        bField = makeNumField(leftX + labelW, lineY + 44, fieldW, 0, 255, c -> updateColorChannel(2, c));
        aField = makeNumField(leftX + labelW, lineY + 66, fieldW, 0, 255, c -> updateColorChannel(3, c));
        addDrawableChild(rField); addDrawableChild(gField); addDrawableChild(bField); addDrawableChild(aField);

        // Hex 输入 (RRGGBB 或 AARRGGBB)
        hexField = new TextFieldWidget(this.textRenderer, leftX, lineY + 92,
                Math.min(110, colW), 18, Text.literal("#"));
        hexField.setMaxLength(8);
        hexField.setChangedListener(s -> { if (!suppressCallbacks) applyHexInput(s); });
        addDrawableChild(hexField);

        // ---- 中栏：顶部预留预览框 76x90，下方四条滑条（缩小高度+行距） ----
        int sliderW = colW;
        int sliderH = 14;
        int sliderGap = 18;
        // 预览顶部从 colTop 开始高 90，需要为“滑条”标题预留 12 px
        int sliderTopY = colTop + 90 + 12;
        sliderR = new RgbSlider(midX, sliderTopY,                  sliderW, sliderH, "R", 0,   c -> updateColorChannel(0, c));
        sliderG = new RgbSlider(midX, sliderTopY + sliderGap,      sliderW, sliderH, "G", 0,   c -> updateColorChannel(1, c));
        sliderB = new RgbSlider(midX, sliderTopY + sliderGap * 2,  sliderW, sliderH, "B", 0,   c -> updateColorChannel(2, c));
        sliderA = new RgbSlider(midX, sliderTopY + sliderGap * 3,  sliderW, sliderH, "A", 255, c -> updateColorChannel(3, c));
        addDrawableChild(sliderR); addDrawableChild(sliderG); addDrawableChild(sliderB); addDrawableChild(sliderA);

        // ---- 右栏：HSV 拾色 ----
        int svSize = Math.min(96, colH - 10);
        int hueW   = 12;
        // 右栏整体居中
        int hsvX = rightX + Math.max(0, (colW - (svSize + 4 + hueW)) / 2);
        hsvPicker = new HsvPickerWidget(hsvX, lineY, svSize, hueW, rgb -> {
            if (suppressCallbacks) return;
            // HSV 只改 RGB，不改 Alpha
            int curAlpha = (working.colorsARGB[editingIdx] >>> 24) & 0xFF;
            int newARGB = (curAlpha << 24) | (rgb & 0x00FFFFFF);
            working.colorsARGB[editingIdx] = newARGB;
            refreshLeftMidFromWorking();
            AdvancedColorBridge.applyPreview(working);
        });

        // ====== 右栏 HSV 下方：GreyReverse 三 checkbox 垂直堆叠 ======
        int grStartY = lineY + svSize + 10; // HSV 拾色器下方 10 px
        int grRowH = 22;
        for (int i = 0; i < AdvancedColorBridge.GR_COUNT; i++) {
            final int idx = i;
            String key = switch (i) {
                case 0 -> "text.ssc_addon.adv_color.grey_reverse.primary";
                case 1 -> "text.ssc_addon.adv_color.grey_reverse.accent1";
                default -> "text.ssc_addon.adv_color.grey_reverse.accent2";
            };
            grChecks[i] = new CheckboxWidget(rightX, grStartY + i * grRowH, 20, 20,
                    Text.translatable(key), working.greyReverse[idx]) {
                @Override
                public void onPress() {
                    super.onPress();
                    working.greyReverse[idx] = this.isChecked();
                    AdvancedColorBridge.applyPreview(working);
                }
            };
            addDrawableChild(grChecks[i]);
        }

        // ====== 底部：取消 / 保存 ======
        int btnY = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.adv_color.btn.cancel"),
                b -> requestCancel()).size(100, 20).position(width / 2 - 60, btnY).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.adv_color.btn.save"),
                b -> doSave()).size(100, 20).position(width / 2 + 50, btnY).build());

        refreshAllFromWorking();
    }

    private TextFieldWidget makeNumField(int x, int y, int w, int min, int max, java.util.function.IntConsumer onValid) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(3);
        f.setChangedListener(s -> {
            if (suppressCallbacks) return;
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= min && v <= max) onValid.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return f;
    }

    /** 文本/滑条改了某一通道 → 写回 working → 联动其余控件 + 推预览。 */
    private void updateColorChannel(int channel, int v) {
        int argb = working.colorsARGB[editingIdx];
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        switch (channel) {
            case 0: r = v; break;
            case 1: g = v; break;
            case 2: b = v; break;
            case 3: a = v; break;
        }
        working.colorsARGB[editingIdx] = (a << 24) | (r << 16) | (g << 8) | b;
        refreshOthersExceptChannel(channel);
        AdvancedColorBridge.applyPreview(working);
    }

    private void refreshOthersExceptChannel(int changedChannel) {
        suppressCallbacks = true;
        try {
            int argb = working.colorsARGB[editingIdx];
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            if (changedChannel != 0) { rField.setText(String.valueOf(r)); sliderR.setIntValue(r); }
            if (changedChannel != 1) { gField.setText(String.valueOf(g)); sliderG.setIntValue(g); }
            if (changedChannel != 2) { bField.setText(String.valueOf(b)); sliderB.setIntValue(b); }
            if (changedChannel != 3) { aField.setText(String.valueOf(a)); sliderA.setIntValue(a); }
            // changed channel 的另一控件也同步（滑条/文本各自）
            // 这里简化：保证两端一致——changed 通道也回填另一端
            switch (changedChannel) {
                case 0: rField.setText(String.valueOf(r)); sliderR.setIntValue(r); break;
                case 1: gField.setText(String.valueOf(g)); sliderG.setIntValue(g); break;
                case 2: bField.setText(String.valueOf(b)); sliderB.setIntValue(b); break;
                case 3: aField.setText(String.valueOf(a)); sliderA.setIntValue(a); break;
            }
            hexField.setText(toHexString(argb));
            hsvPicker.setFromRgb(argb & 0x00FFFFFF);
        } finally {
            suppressCallbacks = false;
        }
    }

    private void refreshLeftMidFromWorking() {
        suppressCallbacks = true;
        try {
            int argb = working.colorsARGB[editingIdx];
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            rField.setText(String.valueOf(r)); sliderR.setIntValue(r);
            gField.setText(String.valueOf(g)); sliderG.setIntValue(g);
            bField.setText(String.valueOf(b)); sliderB.setIntValue(b);
            aField.setText(String.valueOf(a)); sliderA.setIntValue(a);
            hexField.setText(toHexString(argb));
        } finally {
            suppressCallbacks = false;
        }
    }

    /** 切换 editingIdx 或外部全量变化时，三栏全部同步。 */
    private void refreshAllFromWorking() {
        refreshLeftMidFromWorking();
        suppressCallbacks = true;
        try {
            hsvPicker.setFromRgb(working.colorsARGB[editingIdx] & 0x00FFFFFF);
        } finally {
            suppressCallbacks = false;
        }
    }

    private void applyHexInput(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}")) return;
        long parsed;
        try { parsed = Long.parseLong(t, 16); } catch (NumberFormatException e) { return; }
        int argb;
        if (t.length() == 6) {
            int curA = (working.colorsARGB[editingIdx] >>> 24) & 0xFF;
            argb = (curA << 24) | ((int) parsed & 0x00FFFFFF);
        } else {
            argb = (int) parsed;
        }
        working.colorsARGB[editingIdx] = argb;
        suppressCallbacks = true;
        try {
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            rField.setText(String.valueOf(r)); sliderR.setIntValue(r);
            gField.setText(String.valueOf(g)); sliderG.setIntValue(g);
            bField.setText(String.valueOf(b)); sliderB.setIntValue(b);
            aField.setText(String.valueOf(a)); sliderA.setIntValue(a);
            hsvPicker.setFromRgb(argb & 0x00FFFFFF);
        } finally {
            suppressCallbacks = false;
        }
        AdvancedColorBridge.applyPreview(working);
    }

    private static String toHexString(int argb) {
        return String.format("%08X", argb);
    }

    // ====== 渲染 ======

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        // 标题
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, width / 2, 8, 0xFFFFFF);

        // 5 色块边框 + 选中高亮
        for (int i = 0; i < AdvancedColorBridge.COLOR_COUNT; i++) {
            ButtonWidget btn = colorTabs[i];
            int bx = btn.getX(), by = btn.getY(), bw = btn.getWidth(), bh = btn.getHeight();
            int col = working.colorsARGB[i] | 0xFF000000; // 强制不透明显示
            ctx.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, col);
            int border = (i == editingIdx) ? 0xFFFFFF55 : 0xFF000000;
            // 上下左右四条
            ctx.fill(bx, by, bx + bw, by + 1, border);
            ctx.fill(bx, by + bh - 1, bx + bw, by + bh, border);
            ctx.fill(bx, by, bx + 1, by + bh, border);
            ctx.fill(bx + bw - 1, by, bx + bw, by + bh, border);

            // 鼠标 hover 时显示色块名 tooltip
            if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh) {
                ctx.drawTooltip(this.textRenderer, slotNameText(i), mouseX, mouseY);
            }
        }

        // 色块下方居中：当前编辑色槽名（黄色 0xFFFFAA 与列标题统一）
        if (colorTabs[0] != null) {
            int labelY = colorTabs[0].getY() + colorTabs[0].getHeight() + 4;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("text.ssc_addon.adv_color.current_editing", slotNameText(editingIdx)),
                    width / 2, labelY, 0xFFFFAA);
        }

        // 三栏标题 + 通道 label（中栏“滑条”标题改为贴近第一条滑条左上方）
        int colTop = 70;
        int totalAvail = width - 24;
        int gapBetween = 14;
        int colW = (totalAvail - 2 * gapBetween) / 3;
        int leftX  = 12;
        int midX   = leftX + colW + gapBetween;
        int rightX = midX + colW + gapBetween;
        int lineY = colTop + 14;
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("text.ssc_addon.adv_color.col.rgba"), leftX, colTop, 0xFFFFAA);
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("text.ssc_addon.adv_color.col.wheel"), rightX, colTop, 0xFFFFAA);
        // “滑条”标题：贴近第一条滑条左上方（与 init() 里 sliderTopY 取同一公式）
        int sliderTopY = colTop + 90 + 12;
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("text.ssc_addon.adv_color.col.slider"), midX, sliderTopY - 11, 0xFFFFAA);

        // 左栏 R/G/B/A label
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("R"), leftX + 4, lineY + 5,  0xFF6666);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("G"), leftX + 4, lineY + 27, 0x66FF66);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("B"), leftX + 4, lineY + 49, 0x6666FF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("A"), leftX + 4, lineY + 71, 0xCCCCCC);

        // 右栏拾色器渲染
        hsvPicker.render(ctx, mouseX, mouseY);

        // 预览：放在中栏顶部（“滑条”标题上方区域），放大为 76x90 并上移到 colTop
        int previewW = 76, previewH = 90;
        int previewX = midX + (colW - previewW) / 2;
        int previewY = colTop; // 顶到列顶，最大化可用空间
        ctx.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0x88000000);
        ctx.drawBorder(previewX, previewY, previewW, previewH, 0xFFAAAAAA);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            // 预览已由 applyPreview 把 working 推到 PlayerSkinComponent，这里直接渲染玩家即可
            int cx = previewX + previewW / 2;
            int cy = previewY + previewH - 4;
            int mx = mouseX - cx;
            int my = mouseY - (previewY + previewH / 2);
            float f = (float) Math.atan(mx / 40.0F);
            float g = (float) Math.atan(my / 40.0F);
            Quaternionf body = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf head = new Quaternionf().rotateX(g * 20.0F * 0.017453292F);
            body.mul(head);
            float bh = player.bodyYaw, yaw = player.getYaw(), pitch = player.getPitch();
            float phy = player.prevHeadYaw, hy = player.headYaw, pby = player.prevBodyYaw;
            player.bodyYaw = 180.0F + f * 20.0F;
            player.prevBodyYaw = player.bodyYaw;
            player.setYaw(180.0F + f * 40.0F);
            player.setPitch(-g * 20.0F);
            player.headYaw = player.getYaw();
            player.prevHeadYaw = player.getYaw();
            try {
                InventoryScreen.drawEntity(ctx, cx, cy, 40, body, head, player);
            } finally {
                player.bodyYaw = bh; player.prevBodyYaw = pby;
                player.setYaw(yaw); player.setPitch(pitch);
                player.prevHeadYaw = phy; player.headYaw = hy;
            }
        }

        // 状态行：底部按钮上方居中；设置后 5s 内全亮，然后 2s 渐隐至不可见
        if (statusLine != null && !statusLine.getString().isEmpty() && statusSetTimeMs > 0L) {
            long elapsed = System.currentTimeMillis() - statusSetTimeMs;
            int alpha;
            if (elapsed < STATUS_HOLD_MS) {
                alpha = 0xFF;
            } else if (elapsed < STATUS_HOLD_MS + STATUS_FADE_MS) {
                long fadeIn = elapsed - STATUS_HOLD_MS;
                alpha = (int) (0xFF * (1.0 - (double) fadeIn / STATUS_FADE_MS));
                if (alpha < 0) alpha = 0;
                if (alpha > 0xFF) alpha = 0xFF;
            } else {
                alpha = 0;
            }
            if (alpha > 4) {
                int color = (alpha << 24) | 0xAAFFAA;
                ctx.drawCenteredTextWithShadow(this.textRenderer, statusLine,
                        width / 2, height - 28 - 14, color);
            }
        }
    }

    // ====== 鼠标事件转发给 HSV 拾色器 ======

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hsvPicker != null && hsvPicker.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (hsvPicker != null && hsvPicker.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (hsvPicker != null && hsvPicker.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    // ====== 关闭 / 保存 / 取消 ======

    @Override
    public void close() {
        // ESC 触发——视为请求取消
        requestCancel();
    }

    private void requestCancel() {
        if (isDirty()) {
            MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(
                    yes -> {
                        if (yes) {
                            performCancel();
                        } else {
                            // 继续编辑
                            MinecraftClient.getInstance().setScreen(this);
                        }
                    },
                    Text.translatable("text.ssc_addon.adv_color.confirm.cancel_title"),
                    Text.translatable("text.ssc_addon.adv_color.confirm.cancel_msg")
            ));
        } else {
            performCancel();
        }
    }

    private boolean isDirty() {
        if (initialSnapshot == null) return false;
        if (working.enabled != initialSnapshot.enabled) return true;
        for (int i = 0; i < AdvancedColorBridge.COLOR_COUNT; i++) {
            if (working.colorsARGB[i] != initialSnapshot.colorsARGB[i]) return true;
        }
        for (int i = 0; i < AdvancedColorBridge.GR_COUNT; i++) {
            if (working.greyReverse[i] != initialSnapshot.greyReverse[i]) return true;
        }
        return false;
    }

    private void performCancel() {
        AdvancedColorBridge.restoreFromBackup(previewBackup);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void doSave() {
        AdvancedColorBridge.commitSave(working);
        // 提交后 PlayerSkinComponent 会被服务端同步覆写，无需手动 restoreFromBackup
        MinecraftClient.getInstance().setScreen(parent);
    }

    /** 5 个色槽对应的本地化名称。 */
    private static Text slotNameText(int idx) {
        return switch (idx) {
            case AdvancedColorBridge.IDX_PRIMARY -> Text.translatable("text.ssc_addon.adv_color.slot.primary");
            case AdvancedColorBridge.IDX_ACCENT1 -> Text.translatable("text.ssc_addon.adv_color.slot.accent1");
            case AdvancedColorBridge.IDX_ACCENT2 -> Text.translatable("text.ssc_addon.adv_color.slot.accent2");
            case AdvancedColorBridge.IDX_EYE_A   -> Text.translatable("text.ssc_addon.adv_color.slot.eye_a");
            case AdvancedColorBridge.IDX_EYE_B   -> Text.translatable("text.ssc_addon.adv_color.slot.eye_b");
            default -> Text.literal("?");
        };
    }

    // ====== 自定义滑条 ======

    /**
     * R/G/B/A 通道滑条。
     * 显示文本 "X: 123"，0~255 离散。
     */
    private static class RgbSlider extends SliderWidget {
        private final String channelLabel;
        private final java.util.function.IntConsumer onValueChanged;

        RgbSlider(int x, int y, int w, int h, String channelLabel, int initial, java.util.function.IntConsumer onValueChanged) {
            super(x, y, w, h, Text.literal(channelLabel + ": " + initial), initial / 255.0);
            this.channelLabel = channelLabel;
            this.onValueChanged = onValueChanged;
            updateMessage();
        }

        public void setIntValue(int v) {
            this.value = Math.max(0, Math.min(255, v)) / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 255.0);
            this.setMessage(Text.literal(channelLabel + ": " + v));
        }

        @Override
        protected void applyValue() {
            if (onValueChanged != null) {
                int v = (int) Math.round(this.value * 255.0);
                onValueChanged.accept(v);
            }
        }
    }
}
