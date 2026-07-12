package net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.ClientConfig;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * 能量条 / 本能条 位置的可视化编辑界面。
 * <p>
 * 特性：
 * <ul>
 *   <li>背景显示实时游戏世界 + 原版 HUD 参照（快捷栏 / 血条 / 饥饿条），让玩家对照真实屏幕布局调整；</li>
 *   <li>屏幕中间是控制面板：每个条一组「锚点(1-9) + X 偏移滑条/数值框 + Y 偏移滑条/数值框」，三者实时联动；</li>
 *   <li>可直接用鼠标拖拽预览色条，滑条 / 数值框同步变动；</li>
 *   <li>退出时若有未保存改动，弹确认框（放弃 / 继续编辑）。</li>
 * </ul>
 * 编辑时用工作副本，仅在「保存」时写回原版 {@link ClientConfig} 并持久化，取消不影响真实配置。
 * 纯客户端本地显示配置，不涉及服务端同步。
 */
public class BarPositionEditorScreen extends Screen {

    private static final Identifier VANILLA_WIDGETS = new Identifier("minecraft", "textures/gui/widgets.png");
    private static final Identifier VANILLA_ICONS = new Identifier("minecraft", "textures/gui/icons.png");

    private static final int BAR_W = 80;
    private static final int BAR_H = 5;
    private static final int OFFSET_MIN = -500;
    private static final int OFFSET_MAX = 500;

    // 原版默认值（重置用）
    private static final int DEF_IN_TYPE = 8, DEF_IN_X = 100, DEF_IN_Y = -9;
    private static final int DEF_MA_TYPE = 8, DEF_MA_X = 100, DEF_MA_Y = -17;
    // SSCA CD 条默认值（与 SkillCooldownBarRenderer 原硬编码位置一致：快捷栏左右两侧、底部对齐）
    private static final int DEF_CD_TYPE = 8, DEF_CD_X = -98, DEF_CD_Y = -21;
    // CD 条尺寸（贴图 4×20，比本能/能量条细长）
    private static final int CD_W = 4;
    private static final int CD_H = 20;

    private static final int DRAG_NONE = 0, DRAG_INSTINCT = 1, DRAG_MANA = 2, DRAG_CD = 3;

    private final Screen parent;

    // 工作副本
    private int inType, inX, inY;   // 本能条
    private int maType, maX, maY;   // 能量条
    private int cdType, cdX, cdY;   // SSCA 技能 CD 条
    private boolean cdSym;          // CD 主/次是否左右对称
    // 进入时的初始快照（取消还原 / 判断是否有改动）
    private int inType0, inX0, inY0, maType0, maX0, maY0;
    private int cdType0, cdX0, cdY0;
    private boolean cdSym0;
    private boolean snapshotTaken = false;

    // 防止联动回填时循环触发回调
    private boolean suppressCallbacks = false;

    // 控件引用
    private ButtonWidget inTypeBtn, maTypeBtn, cdTypeBtn, cdSymBtn;
    private OffsetSlider inXSlider, inYSlider, maXSlider, maYSlider, cdXSlider, cdYSlider;
    private TextFieldWidget inXField, inYField, maXField, maYField, cdXField, cdYField;

    // 拖拽状态
    private int dragging = DRAG_NONE;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartOffX, dragStartOffY;

    // 选中状态（用于方向键微调与高亮）：DRAG_NONE / DRAG_INSTINCT / DRAG_MANA
    private int selected = DRAG_NONE;
    // 网格吸附步长（未按 Shift 且无对齐时）
    private static final int GRID = 5;
    // 对齐吸附阈值（像素）
    private static final int SNAP_DIST = 4;
    // 当前帧激活的对齐参考线（屏幕坐标；null 表示无）
    private Integer guideVX = null;
    private Integer guideHY = null;

    // 控制面板边界（init 时算好，render 背景复用，避免魔数重复不一致）
    private int panelLeft, panelRight, panelTop, panelBottom;

    public BarPositionEditorScreen(Screen parent) {
        super(Text.translatable("text.ssc_addon.bar_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 首次进入：从原版 ClientConfig 读取工作副本 + 快照
        if (!snapshotTaken) {
            ClientConfig cfg = ShapeShifterCurseFabric.clientConfig;
            inType = inType0 = cfg.instinctBarPosType;
            inX = inX0 = cfg.instinctBarPosOffsetX;
            inY = inY0 = cfg.instinctBarPosOffsetY;
            maType = maType0 = cfg.manaBarPosType;
            maX = maX0 = cfg.manaBarPosOffsetX;
            maY = maY0 = cfg.manaBarPosOffsetY;
            // SSCA CD 条从附属自己的 client config 读取
            SSCAddonClientConfig sscCfg;
            try {
                sscCfg = SSCAddonConfig.client();
            } catch (Exception e) {
                sscCfg = null;
            }
            if (sscCfg != null) {
                cdType = cdType0 = sscCfg.cdBarPosType;
                cdX = cdX0 = sscCfg.cdBarPosOffsetX;
                cdY = cdY0 = sscCfg.cdBarPosOffsetY;
                cdSym = cdSym0 = sscCfg.cdSymmetric;
            } else {
                cdType = cdType0 = DEF_CD_TYPE;
                cdX = cdX0 = DEF_CD_X;
                cdY = cdY0 = DEF_CD_Y;
                cdSym = cdSym0 = true;
            }
            snapshotTaken = true;
        }

        // ====== 控制面板（屏幕中间，紧凑）======
        final int panelW = 208;
        final int panelX = (width - panelW) / 2;
        final int sliderW = 116;
        final int fieldW = 36;
        final int rowH = 14;
        final int ctrlH = 11;
        final int typeBtnW = 100, typeBtnH = 13;
        final int contentRight = panelX + sliderW + 4 + fieldW;

        // 本能条区块
        int inTop = Math.max(20, height / 2 - 85);
        inTypeBtn = ButtonWidget.builder(anchorBtnText("instinct", inType), b -> cycleType(true))
                .dimensions(panelX, inTop, typeBtnW, typeBtnH).build();
        addDrawableChild(inTypeBtn);
        inXSlider = new OffsetSlider(panelX, inTop + rowH, sliderW, ctrlH, "offset_x", inX, v -> { inX = v; onWorkingChanged(); });
        addDrawableChild(inXSlider);
        inXField = makeNumField(panelX + sliderW + 4, inTop + rowH, fieldW, ctrlH, v -> { inX = v; onWorkingChanged(); });
        addDrawableChild(inXField);
        inYSlider = new OffsetSlider(panelX, inTop + rowH * 2, sliderW, ctrlH, "offset_y", inY, v -> { inY = v; onWorkingChanged(); });
        addDrawableChild(inYSlider);
        inYField = makeNumField(panelX + sliderW + 4, inTop + rowH * 2, fieldW, ctrlH, v -> { inY = v; onWorkingChanged(); });
        addDrawableChild(inYField);

        // 能量条区块
        int maTop = inTop + rowH * 3 + 6;
        maTypeBtn = ButtonWidget.builder(anchorBtnText("mana", maType), b -> cycleType(false))
                .dimensions(panelX, maTop, typeBtnW, typeBtnH).build();
        addDrawableChild(maTypeBtn);
        maXSlider = new OffsetSlider(panelX, maTop + rowH, sliderW, ctrlH, "offset_x", maX, v -> { maX = v; onWorkingChanged(); });
        addDrawableChild(maXSlider);
        maXField = makeNumField(panelX + sliderW + 4, maTop + rowH, fieldW, ctrlH, v -> { maX = v; onWorkingChanged(); });
        addDrawableChild(maXField);
        maYSlider = new OffsetSlider(panelX, maTop + rowH * 2, sliderW, ctrlH, "offset_y", maY, v -> { maY = v; onWorkingChanged(); });
        addDrawableChild(maYSlider);
        maYField = makeNumField(panelX + sliderW + 4, maTop + rowH * 2, fieldW, ctrlH, v -> { maY = v; onWorkingChanged(); });
        addDrawableChild(maYField);

        // SSCA 技能 CD 条区块
        int cdTop = maTop + rowH * 3 + 6;
        cdTypeBtn = ButtonWidget.builder(anchorBtnText("cd", cdType), b -> cycleTypeCd())
                .dimensions(panelX, cdTop, typeBtnW, typeBtnH).build();
        addDrawableChild(cdTypeBtn);
        cdSymBtn = ButtonWidget.builder(cdSymText(), b -> { cdSym = !cdSym; onWorkingChanged(); })
                .dimensions(panelX + typeBtnW + 4, cdTop, Math.max(40, contentRight - (panelX + typeBtnW + 4)), typeBtnH).build();
        addDrawableChild(cdSymBtn);
        cdXSlider = new OffsetSlider(panelX, cdTop + rowH, sliderW, ctrlH, "offset_x", cdX, v -> { cdX = v; onWorkingChanged(); });
        addDrawableChild(cdXSlider);
        cdXField = makeNumField(panelX + sliderW + 4, cdTop + rowH, fieldW, ctrlH, v -> { cdX = v; onWorkingChanged(); });
        addDrawableChild(cdXField);
        cdYSlider = new OffsetSlider(panelX, cdTop + rowH * 2, sliderW, ctrlH, "offset_y", cdY, v -> { cdY = v; onWorkingChanged(); });
        addDrawableChild(cdYSlider);
        cdYField = makeNumField(panelX + sliderW + 4, cdTop + rowH * 2, fieldW, ctrlH, v -> { cdY = v; onWorkingChanged(); });
        addDrawableChild(cdYField);

        // ====== 按钮：保存 + 三组独立重置 + 取消 ======
        final int botBtnW = 50;
        final int botBtnH = 14;
        final int botGap = 3;
        int botStartX = panelX;
        int botY = cdTop + rowH * 3 + 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.save"), b -> doSave())
                .dimensions(botStartX, botY, botBtnW, botBtnH).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.reset"), b -> doReset())
                .dimensions(botStartX + botBtnW + botGap, botY, botBtnW, botBtnH).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.cancel"), b -> requestCancel())
                .dimensions(botStartX + (botBtnW + botGap) * 2, botY, botBtnW, botBtnH).build());

        // 三组独立重置按钮（放在底部按钮下方一行，分别只重置对应 UI）
        final int rstBtnW = 66;
        final int rstBtnH = 12;
        int rstY = botY + botBtnH + 4;
        int rstTotalW = rstBtnW * 3 + botGap * 2;
        int rstStartX = panelX + (contentRight - panelX - rstTotalW) / 2;
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("text.ssc_addon.bar_editor.reset_instinct"), b -> doResetInstinct())
                .dimensions(rstStartX, rstY, rstBtnW, rstBtnH).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("text.ssc_addon.bar_editor.reset_mana"), b -> doResetMana())
                .dimensions(rstStartX + rstBtnW + botGap, rstY, rstBtnW, rstBtnH).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("text.ssc_addon.bar_editor.reset_cd"), b -> doResetCd())
                .dimensions(rstStartX + (rstBtnW + botGap) * 2, rstY, rstBtnW, rstBtnH).build());

        // 记录面板边界（背景绘制复用）
        panelLeft = panelX - 8;
        panelRight = contentRight + 8;
        panelTop = inTop - 8;
        panelBottom = rstY + rstBtnH + 8;

        syncAllControls();
    }

    // ====== 锚点循环 ======
    private void cycleType(boolean instinct) {
        if (instinct) {
            inType = inType % 9 + 1;
        } else {
            maType = maType % 9 + 1;
        }
        onWorkingChanged();
    }
    private void cycleTypeCd() {
        cdType = cdType % 9 + 1;
        onWorkingChanged();
    }
    private Text cdSymText() {
        return Text.translatable("text.ssc_addon.bar_editor.cd_symmetric",
                Text.translatable(cdSym ? "text.ssc_addon.bar_editor.sym_on" : "text.ssc_addon.bar_editor.sym_off"));
    }
    private Text anchorBtnText(String which, int type) {
        return Text.translatable("text.ssc_addon.bar_editor.anchor",
                Text.translatable("text.ssc_addon.bar_editor." + which),
                type,
                Text.translatable("text.ssc_addon.bar_editor.anchor.pos." + type));
    }

    // ====== 工作副本变化 → 回填所有控件 + 实时写入配置 ======
    private void onWorkingChanged() {
        if (suppressCallbacks) return;
        syncAllControls();
        applyToConfig();
    }

    /** 把工作值实时写入配置：本能/能量条入原版 ClientConfig，CD 条入附属 SSCAddonClientConfig。 */
    private void applyToConfig() {
        ClientConfig cfg = ShapeShifterCurseFabric.clientConfig;
        cfg.instinctBarPosType = inType;
        cfg.instinctBarPosOffsetX = inX;
        cfg.instinctBarPosOffsetY = inY;
        cfg.manaBarPosType = maType;
        cfg.manaBarPosOffsetX = maX;
        cfg.manaBarPosOffsetY = maY;
        try {
            SSCAddonClientConfig sscCfg = SSCAddonConfig.client();
            sscCfg.cdBarPosType = cdType;
            sscCfg.cdBarPosOffsetX = cdX;
            sscCfg.cdBarPosOffsetY = cdY;
            sscCfg.cdSymmetric = cdSym;
        } catch (Exception ignored) {}
    }

    private void syncAllControls() {
        suppressCallbacks = true;
        try {
            if (inTypeBtn != null) inTypeBtn.setMessage(anchorBtnText("instinct", inType));
            if (maTypeBtn != null) maTypeBtn.setMessage(anchorBtnText("mana", maType));
            if (inXSlider != null) inXSlider.setIntValue(inX);
            if (inYSlider != null) inYSlider.setIntValue(inY);
            if (maXSlider != null) maXSlider.setIntValue(maX);
            if (maYSlider != null) maYSlider.setIntValue(maY);
            if (inXField != null) inXField.setText(String.valueOf(inX));
            if (inYField != null) inYField.setText(String.valueOf(inY));
            if (maXField != null) maXField.setText(String.valueOf(maX));
            if (maYField != null) maYField.setText(String.valueOf(maY));
            if (cdTypeBtn != null) cdTypeBtn.setMessage(anchorBtnText("cd", cdType));
            if (cdXSlider != null) cdXSlider.setIntValue(cdX);
            if (cdYSlider != null) cdYSlider.setIntValue(cdY);
            if (cdXField != null) cdXField.setText(String.valueOf(cdX));
            if (cdYField != null) cdYField.setText(String.valueOf(cdY));
            if (cdSymBtn != null) cdSymBtn.setMessage(cdSymText());
        } finally {
            suppressCallbacks = false;
        }
    }

    private TextFieldWidget makeNumField(int x, int y, int w, int h, IntConsumer onValid) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal(""));
        f.setMaxLength(5);
        f.setChangedListener(s -> {
            if (suppressCallbacks) return;
            String t = s.trim();
            if (t.isEmpty() || t.equals("-")) return;
            try {
                int v = Integer.parseInt(t);
                if (v < OFFSET_MIN) v = OFFSET_MIN;
                if (v > OFFSET_MAX) v = OFFSET_MAX;
                onValid.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return f;
    }

    // ====== 保存 / 取消 / 重置 ======
    private boolean isEdited() {
        return inType != inType0 || inX != inX0 || inY != inY0
                || maType != maType0 || maX != maX0 || maY != maY0
                || cdType != cdType0 || cdX != cdX0 || cdY != cdY0 || cdSym != cdSym0;
    }

    private void doSave() {
        applyToConfig();
        try {
            AutoConfig.getConfigHolder(ClientConfig.class).save();
        } catch (Exception ignored) {}
        // 同步保存附属 client config（CD 条位置）
        try {
            AutoConfig.getConfigHolder(SSCAddonClientConfig.class).save();
        } catch (Exception ignored) {}
        // 更新快照，避免 close 时又弹确认
        inType0 = inType; inX0 = inX; inY0 = inY;
        maType0 = maType; maX0 = maX; maY0 = maY;
        cdType0 = cdType; cdX0 = cdX; cdY0 = cdY; cdSym0 = cdSym;
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void doReset() {
        inType = DEF_IN_TYPE; inX = DEF_IN_X; inY = DEF_IN_Y;
        maType = DEF_MA_TYPE; maX = DEF_MA_X; maY = DEF_MA_Y;
        cdType = DEF_CD_TYPE; cdX = DEF_CD_X; cdY = DEF_CD_Y; cdSym = true;
        syncAllControls();
        applyToConfig();
    }

    /** 仅重置本能条（不影响能量条与 CD 条）。 */
    private void doResetInstinct() {
        inType = DEF_IN_TYPE; inX = DEF_IN_X; inY = DEF_IN_Y;
        syncAllControls();
        applyToConfig();
    }

    /** 仅重置能量条（不影响本能条与 CD 条）。 */
    private void doResetMana() {
        maType = DEF_MA_TYPE; maX = DEF_MA_X; maY = DEF_MA_Y;
        syncAllControls();
        applyToConfig();
    }

    /** 仅重置 SSCA CD 条（不影响本能/能量条）。 */
    private void doResetCd() {
        cdType = DEF_CD_TYPE; cdX = DEF_CD_X; cdY = DEF_CD_Y; cdSym = true;
        syncAllControls();
        applyToConfig();
    }

    private void requestCancel() {
        if (!isEdited()) {
            restoreConfigToSnapshot();
            MinecraftClient.getInstance().setScreen(parent);
            return;
        }
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        restoreConfigToSnapshot();
                        MinecraftClient.getInstance().setScreen(parent);
                    } else {
                        MinecraftClient.getInstance().setScreen(this);
                    }
                },
                Text.translatable("text.ssc_addon.bar_editor.confirm.title"),
                Text.translatable("text.ssc_addon.bar_editor.confirm.body"),
                Text.translatable("text.ssc_addon.bar_editor.confirm.discard"),
                Text.translatable("text.ssc_addon.bar_editor.confirm.keep")));
    }

    /** 取消时把工作值与配置都还原到进入编辑器时的快照。 */
    private void restoreConfigToSnapshot() {
        inType = inType0; inX = inX0; inY = inY0;
        maType = maType0; maX = maX0; maY = maY0;
        cdType = cdType0; cdX = cdX0; cdY = cdY0; cdSym = cdSym0;
        applyToConfig();
    }

    @Override
    public void close() {
        requestCancel();
    }

    // ====== 拖拽 / 选中预览条 ======
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 本能条在上层，三条重叠时优先命中本能条 → 能量条 → CD 条
            if (hitBar(mouseX, mouseY, false)) {
                selected = dragging = DRAG_INSTINCT;
                beginDrag(mouseX, mouseY, inX, inY);
                return true;
            }
            if (hitBar(mouseX, mouseY, true)) {
                selected = dragging = DRAG_MANA;
                beginDrag(mouseX, mouseY, maX, maY);
                return true;
            }
            if (hitCdBar(mouseX, mouseY)) {
                selected = dragging = DRAG_CD;
                beginDrag(mouseX, mouseY, cdX, cdY);
                return true;
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // 点在空白（非控件、非条）→ 取消选中
        if (!handled && button == 0) {
            selected = DRAG_NONE;
        }
        return handled;
    }

    private void beginDrag(double mx, double my, int offX, int offY) {
        dragStartMouseX = mx;
        dragStartMouseY = my;
        dragStartOffX = offX;
        dragStartOffY = offY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != DRAG_NONE) {
            boolean mana = dragging == DRAG_MANA;
            boolean cd = dragging == DRAG_CD;
            int curType = cd ? cdType : (mana ? maType : inType);
            int dx = (int) Math.round(mouseX - dragStartMouseX);
            int dy = (int) Math.round(mouseY - dragStartMouseY);
            // 锚点屏幕坐标（offset=0 时）
            Pair<Integer, Integer> anchor = UIPositionUtils.getCorrectPosition(curType, 0, 0);
            int scrX = anchor.getLeft() + dragStartOffX + dx;
            int scrY = anchor.getRight() + dragStartOffY + dy;
            guideVX = null;
            guideHY = null;
            if (!hasShiftDown()) {
                // 默认：对齐吸附（屏幕中线 / 边缘 / 另一条），失败则轻微网格吸附；按住 Shift 则完全自由
                int[] sx = snapAxis(scrX, mana, cd, true);
                scrX = sx[0];
                if (sx[1] != Integer.MIN_VALUE) guideVX = sx[1];
                int[] sy = snapAxis(scrY, mana, cd, false);
                scrY = sy[0];
                if (sy[1] != Integer.MIN_VALUE) guideHY = sy[1];
            }
            // 安全边界：条至少有一部分留在屏幕内
            int barW = cd ? CD_W : BAR_W;
            int barH = cd ? CD_H : BAR_H;
            scrX = clampScreenX(scrX, barW);
            scrY = clampScreenY(scrY, barH);
            int nx = clampOffset(scrX - anchor.getLeft());
            int ny = clampOffset(scrY - anchor.getRight());
            if (cd) { cdX = nx; cdY = ny; }
            else if (mana) { maX = nx; maY = ny; }
            else { inX = nx; inY = ny; }
            syncAllControls();
            applyToConfig();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != DRAG_NONE && button == 0) {
            dragging = DRAG_NONE;
            guideVX = null;
            guideHY = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // 方向键微调选中的条（未聚焦文本框时）；Shift 大步
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected != DRAG_NONE && !(getFocused() instanceof TextFieldWidget)) {
            int step = hasShiftDown() ? 10 : 1;
            int dx = 0, dy = 0;
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT -> dx = step;
                case GLFW.GLFW_KEY_UP -> dy = -step;
                case GLFW.GLFW_KEY_DOWN -> dy = step;
                default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
            }
            if (selected == DRAG_CD) { cdX = clampOffset(cdX + dx); cdY = clampOffset(cdY + dy); }
            else if (selected == DRAG_MANA) { maX = clampOffset(maX + dx); maY = clampOffset(maY + dy); }
            else { inX = clampOffset(inX + dx); inY = clampOffset(inY + dy); }
            syncAllControls();
            applyToConfig();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int clampOffset(int v) {
        return Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, v));
    }

    /**
     * 单轴对齐吸附：把条的 左/中/右（或上/中/下）三个关键点，向「屏幕中线 / 屏幕边缘 / 其它条的对应关键点」吸附。
     * @param scr        条在该轴的起始屏幕坐标
     * @param mana       当前拖的是不是能量条
     * @param cd         当前拖的是不是 CD 条
     * @param horizontal true=X 轴，false=Y 轴
     * @return [吸附后起始坐标, 参考线坐标(无则 Integer.MIN_VALUE)]
     */
    private int[] snapAxis(int scr, boolean mana, boolean cd, boolean horizontal) {
        int size = horizontal ? (cd ? CD_W : BAR_W) : (cd ? CD_H : BAR_H);
        int screenLen = horizontal ? this.width : this.height;
        // 另外两条已存在 bar 的起始坐标（作为吸附参考）
        java.util.List<Integer> others = new java.util.ArrayList<>();
        if (!mana && !cd) {
            // 拖本能条 → 参考能量条与 CD 条
            others.add(horizontal ? barPos(true).getLeft() : barPos(true).getRight());
            others.add(horizontal ? cdBarPos().getLeft() : cdBarPos().getRight());
        } else if (mana) {
            others.add(horizontal ? barPos(false).getLeft() : barPos(false).getRight());
            others.add(horizontal ? cdBarPos().getLeft() : cdBarPos().getRight());
        } else { // cd
            others.add(horizontal ? barPos(false).getLeft() : barPos(false).getRight());
            others.add(horizontal ? barPos(true).getLeft() : barPos(true).getRight());
        }
        int[] pointOffsets = {0, size / 2, size};
        // 对齐目标：屏幕中线、屏幕两端、其它条的起/中/终
        java.util.List<Integer> targets = new java.util.ArrayList<>();
        targets.add(screenLen / 2); targets.add(0); targets.add(screenLen);
        for (int oStart : others) { targets.add(oStart); targets.add(oStart + size / 2); targets.add(oStart + size); }
        int bestStart = Integer.MIN_VALUE, bestGuide = Integer.MIN_VALUE, bestDist = SNAP_DIST + 1;
        for (int po : pointOffsets) {
            int pointCoord = scr + po;
            for (int tg : targets) {
                int d = Math.abs(pointCoord - tg);
                if (d < bestDist) {
                    bestDist = d;
                    bestStart = tg - po;
                    bestGuide = tg;
                }
            }
        }
        if (bestStart != Integer.MIN_VALUE) return new int[]{bestStart, bestGuide};
        // 无对齐 → 轻微网格吸附（无参考线）
        return new int[]{Math.round(scr / (float) GRID) * GRID, Integer.MIN_VALUE};
    }

    private int clampScreenX(int scrX, int barW) {
        int minVisible = 8;
        return Math.max(-(barW - minVisible), Math.min(this.width - minVisible, scrX));
    }

    private int clampScreenY(int scrY, int barH) {
        return Math.max(0, Math.min(this.height - barH, scrY));
    }

    /** 判断鼠标是否落在本能/能量条预览的热区（比条本体略大，便于点选）。 */
    private boolean hitBar(double mouseX, double mouseY, boolean mana) {
        Pair<Integer, Integer> pos = barPos(mana);
        int x = pos.getLeft();
        int y = pos.getRight();
        return mouseX >= x - 2 && mouseX <= x + BAR_W + 2 && mouseY >= y - 3 && mouseY <= y + BAR_H + 3;
    }

    /** 判断鼠标是否落在 CD 条预览的热区。 */
    private boolean hitCdBar(double mouseX, double mouseY) {
        Pair<Integer, Integer> pos = cdBarPos();
        int x = pos.getLeft();
        int y = pos.getRight();
        return mouseX >= x - 2 && mouseX <= x + CD_W + 2 && mouseY >= y - 3 && mouseY <= y + CD_H + 3;
    }

    private Pair<Integer, Integer> barPos(boolean mana) {
        if (mana) {
            return UIPositionUtils.getCorrectPosition(maType, maX, maY);
        }
        return UIPositionUtils.getCorrectPosition(inType, inX, inY);
    }

    /** CD 条（主技能左侧）屏幕坐标。 */
    private Pair<Integer, Integer> cdBarPos() {
        return UIPositionUtils.getCorrectPosition(cdType, cdX, cdY);
    }

    // ====== 渲染 ======
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            // 世界已由游戏主循环实时渲染到画面；这里叠加真实完整 HUD（含真实的能量条 / 本能条）
            if (!mc.options.hudHidden) {
                try {
                    mc.inGameHud.render(ctx, delta);
                } catch (Exception ignored) {
                    drawHudReference(ctx);
                }
            }
        } else {
            // 主菜单等无世界场景：暗背景 + HUD 参照兑底
            this.renderBackground(ctx);
            drawHudReference(ctx);
        }

        // 控制面板半透明底（够暗以保证控件可读）
        drawControlPanelBackground(ctx);

        // 控件（滑条 / 数值框 / 按钮）
        super.render(ctx, mouseX, mouseY, delta);

        // 拖拽时的对齐参考线（贯穿屏幕）
        if (dragging != DRAG_NONE) {
            if (guideVX != null) ctx.fill(guideVX, 0, guideVX + 1, this.height, 0x88FFEE00);
            if (guideHY != null) ctx.fill(0, guideHY, this.width, guideHY + 1, 0x88FFEE00);
        }

        // 能量 / 本能条 / CD 条可拖拽手柄（半透明示意 + 边框 + 标签），确保任何形态都能看到并拖拽
        drawBarHandle(ctx, mouseX, mouseY, false);
        drawBarHandle(ctx, mouseX, mouseY, true);
        drawCdBarHandle(ctx, mouseX, mouseY);

        // 标题 + 提示
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc_addon.bar_editor.hint"), width / 2, 26, 0xFFAAAAAA);
    }

    private void drawControlPanelBackground(DrawContext ctx) {
        ctx.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC000000);
        ctx.drawBorder(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF555555);
    }

    /**
     * 能量 / 本能条的可拖拽手柄：半透明示意填充 + 边框 + 标签。
     * 真实 HUD 已把当前形态有的条画出；手柄叠在其上作为拖拽锚点，即便当前形态不显示某条也能看到位置。
     */
    private void drawBarHandle(DrawContext ctx, int mouseX, int mouseY, boolean mana) {
        Pair<Integer, Integer> pos = barPos(mana);
        int x = pos.getLeft();
        int y = pos.getRight();

        // 半透明示意填充（即使当前形态不显示真实条，也能看到它的位置与大小）
        int fill = mana ? 0x600000FF : 0x60FF6400;
        ctx.fill(x, y, x + BAR_W, y + BAR_H, fill);

        boolean hovered = hitBar(mouseX, mouseY, mana);
        boolean active = (dragging == (mana ? DRAG_MANA : DRAG_INSTINCT));
        boolean sel = (selected == (mana ? DRAG_MANA : DRAG_INSTINCT));
        int border = active ? 0xFFFFEE00 : (sel ? 0xFF00FF88 : (hovered ? 0xFFFFFFAA : 0xFF000000));
        ctx.drawBorder(x - 1, y - 1, BAR_W + 2, BAR_H + 2, border);

        // 条上方标签
        Text label = Text.translatable("text.ssc_addon.bar_editor." + (mana ? "mana" : "instinct"));
        ctx.drawTextWithShadow(this.textRenderer, label, x, y - 11, mana ? 0xFF66E0FF : 0xFFFFC864);

        // 选中或拖拽时，在条下方显示实时偏移坐标
        if (sel || active) {
            int offX = mana ? maX : inX;
            int offY = mana ? maY : inY;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(" + offX + ", " + offY + ")"),
                    x, y + BAR_H + 2, 0xFFFFFFFF);
        }
    }

    /** SSCA 技能 CD 条的可拖拽手柄：4×20 细长条，主条（左侧）可拖拽，副条（右侧）自动镜像。 */
    private void drawCdBarHandle(DrawContext ctx, int mouseX, int mouseY) {
        Pair<Integer, Integer> pos = cdBarPos();
        int x = pos.getLeft();
        int y = pos.getRight();
        // 主条半透明绿色示意
        ctx.fill(x, y, x + CD_W, y + CD_H, 0x6000FF00);
        boolean hovered = hitCdBar(mouseX, mouseY);
        boolean active = (dragging == DRAG_CD);
        boolean sel = (selected == DRAG_CD);
        int border = active ? 0xFFFFEE00 : (sel ? 0xFF00FF88 : (hovered ? 0xFFFFFFAA : 0xFF000000));
        ctx.drawBorder(x - 1, y - 1, CD_W + 2, CD_H + 2, border);
        Text label = Text.translatable("text.ssc_addon.bar_editor.cd");
        ctx.drawTextWithShadow(this.textRenderer, label, x - 2, y - 11, 0xFF66FF99);
        // 副条镜像示意（不可单独拖拽，跟随主条 X 镜像）
        int secX = this.width - x - CD_W;
        ctx.fill(secX, y, secX + CD_W, y + CD_H, 0x6000AAFF);
        ctx.drawBorder(secX - 1, y - 1, CD_W + 2, CD_H + 2, 0xFF444444);
        // 选中/拖拽时显示主条偏移坐标
        if (sel || active) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(" + cdX + ", " + cdY + ")"),
                    x, y + CD_H + 2, 0xFFFFFFFF);
        }
    }

    /** 画原版 HUD 参照（快捷栏 + 血条 + 饥饿条），仅作位置参照。 */
    private void drawHudReference(DrawContext ctx) {
        int cx = width / 2;
        // 快捷栏
        ctx.drawTexture(VANILLA_WIDGETS, cx - 91, height - 22, 0, 0, 182, 22);
        // 血条（10 颗）：背景 + 满心
        int hbBaseX = cx - 91;
        int hbY = height - 39;
        for (int i = 0; i < 10; i++) {
            int hx = hbBaseX + i * 8;
            ctx.drawTexture(VANILLA_ICONS, hx, hbY, 16, 0, 9, 9);   // 空心背景
            ctx.drawTexture(VANILLA_ICONS, hx, hbY, 52, 0, 9, 9);   // 满心
        }
        // 饥饿条（10 个）：从右往左
        int fBaseX = cx + 91 - 9;
        for (int i = 0; i < 10; i++) {
            int fx = fBaseX - i * 8;
            ctx.drawTexture(VANILLA_ICONS, fx, hbY, 16, 27, 9, 9); // 空饥饿背景
            ctx.drawTexture(VANILLA_ICONS, fx, hbY, 52, 27, 9, 9); // 满饥饿
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * 用 fabric {@link ScreenEvents} 在 SSC「客户端配置」cloth-config 界面右上角注入入口按钮，点击打开本编辑器。
     * 非 Mixin、稳定可打包；放在本类内（而非独立文件），避免新建文件在某些环境被回滚。
     * 用类名字符串 + title 的 translation key 精确判定，只在该界面加按钮，不污染其它配置界面。
     */
    public static void registerEntry() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen == null) return;
            if (!"me.shedaniel.clothconfig2.gui.ClothConfigScreen".equals(screen.getClass().getName())) return;
            Text title = screen.getTitle();
            if (title == null || !(title.getContent() instanceof TranslatableTextContent ttc)) return;
            if (!"text.autoconfig.shape-shifter-curse-client.title".equals(ttc.getKey())) return;
            final int btnW = 130;
            ButtonWidget btn = ButtonWidget.builder(
                            Text.translatable("text.ssc_addon.bar_editor.open"),
                            b -> MinecraftClient.getInstance().setScreen(new BarPositionEditorScreen(screen)))
                    .dimensions(screen.width - btnW - 6, 6, btnW, 20).build();
            Screens.getButtons(screen).add(btn);
        });
    }

    // ====== 偏移滑条控件 ======
    private class OffsetSlider extends SliderWidget {
        private final String labelKey;
        private final IntConsumer onChange;

        OffsetSlider(int x, int y, int w, int h, String labelKey, int initVal, IntConsumer onChange) {
            super(x, y, w, h, Text.empty(), (double) (initVal - OFFSET_MIN) / (OFFSET_MAX - OFFSET_MIN));
            this.labelKey = labelKey;
            this.onChange = onChange;
            updateMessage();
        }

        int getIntValue() {
            return (int) Math.round(OFFSET_MIN + this.value * (OFFSET_MAX - OFFSET_MIN));
        }

        void setIntValue(int v) {
            this.value = (double) (v - OFFSET_MIN) / (OFFSET_MAX - OFFSET_MIN);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("text.ssc_addon.bar_editor." + labelKey)
                    .append(Text.literal(": " + getIntValue())));
        }

        @Override
        protected void applyValue() {
            if (suppressCallbacks) return;
            onChange.accept(getIntValue());
        }
    }
}
