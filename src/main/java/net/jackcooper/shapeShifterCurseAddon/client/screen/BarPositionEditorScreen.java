package net.jackcooper.shapeShifterCurseAddon.client.screen;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
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
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.client.hud.SkillCooldownBarRenderer;
import net.jackcooper.shapeShifterCurseAddon.client.hud.NeoStabilityBar;
import net.jackcooper.shapeShifterCurseAddon.client.hud.SpellbookHudRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * HUD 位置与显隐的可视化编辑界面。
 * <p>
 * 特性：
 * <ul>
 *   <li>背景显示实时游戏世界 + 原版 HUD 参照（快捷栏 / 血条 / 饥饿条），让玩家对照真实屏幕布局调整；</li>
 *   <li>点击某个HUD后显示其独立的锚点、偏移和显隐控制；点击画布空白收起面板；</li>
 *   <li>可直接用鼠标拖拽预览色条，滑条 / 数值框同步变动；</li>
 *   <li>右上角叉号或 Esc 打开退出选择：保存并退出 / 撤销并退出 / 取消。</li>
 * </ul>
 * 编辑时实时预览工作副本；保存并退出时持久化，撤销并退出时还原进入时的快照，取消则继续编辑。
 * 纯客户端本地显示配置，不涉及服务端同步。
 */
public class BarPositionEditorScreen extends Screen {

    private static final Identifier VANILLA_WIDGETS = new Identifier("minecraft", "textures/gui/widgets.png");
    private static final Identifier VANILLA_ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    /** 蓄力条贴图（编辑器预览示意用，与 SpellCastHud 同源；左/右双贴图）。 */
    private static final Identifier CHARGE_TEX_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_right_empty.png");
    private static final Identifier CHARGE_TEX_EMPTY_LEFT = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_left_empty.png");

    private static final int BAR_W = 80;
    private static final int BAR_H = 5;
    private static final int OFFSET_MIN = -500;
    private static final int OFFSET_MAX = 500;

    // 原版默认值（重置用）
    private static final int DEF_IN_TYPE = 8, DEF_IN_X = 100, DEF_IN_Y = -9;
    private static final int DEF_MA_TYPE = 8, DEF_MA_X = 100, DEF_MA_Y = -17;
    private static final int DEF_CD_TYPE = SSCAddonClientConfig.DEFAULT_CD_TYPE;
    private static final int DEF_CD_X = SSCAddonClientConfig.DEFAULT_CD_X;
    private static final int DEF_CD_Y = SSCAddonClientConfig.DEFAULT_CD_Y;
    private static final int CD_W = SkillCooldownBarRenderer.SLOT_WIDTH;
    // 新默认位置居于独立玻璃条下方；已有配置保留，重置才应用新默认值。
    private static final int DEF_SB_TYPE = 7, DEF_SB_X = SpellbookHudRenderer.DEFAULT_X, DEF_SB_Y = SpellbookHudRenderer.DEFAULT_Y;
    // 书栏只包括三槽和魔法名，稳定度条使用自己的独立包围盒。
    private static final int SB_W = SpellbookHudRenderer.WIDTH, SB_H = SpellbookHudRenderer.HEIGHT,
            SB_ORIGIN_DX = SpellbookHudRenderer.ORIGIN_X, SB_ORIGIN_DY = 0;
    // 法术蓄力条：默认锚点 4(左中)+偏移(0,-34)+贴右侧（与 SSCAddonClientConfig 默认一致，CD 条对侧同高）
    private static final int DEF_CH_TYPE = SSCAddonClientConfig.DEFAULT_CHARGE_TYPE;
    private static final int DEF_CH_X = SSCAddonClientConfig.DEFAULT_CHARGE_X;
    private static final int DEF_CH_Y = SSCAddonClientConfig.DEFAULT_CHARGE_Y;
    private static final int CH_W = 14, CH_H = 68; // 贴图尺寸

    private static final int DEF_MG_TYPE = 8, DEF_MG_X = -200, DEF_MG_Y = -35;
    // Actual SSC slot bounds relative to its layout origin.
    private static final int MG_W = 52, MG_H = 24, MG_ORIGIN_DX = 6;

    private static final int DRAG_MAGIC = 7;
    private static final int DRAG_STABILITY = 8;
    private static final int DRAG_NONE = 0, DRAG_INSTINCT = 1, DRAG_MANA = 2, DRAG_CD = 3, DRAG_SPELLBOOK = 5, DRAG_CHARGE = 6;

    private final Screen parent;
    private boolean exitMenuOpen;

    // 工作副本
    private int inType, inX, inY;   // 本能条
    private int maType, maX, maY;   // 能量条
    private int cdType, cdX, cdY;
    private boolean cdRight;
    private int sbType, sbX, sbY;   // 月尘魔法书 HUD 整体
    private int chType, chX, chY;   // 法术蓄力条
    private boolean chRight;
    // 进入时的初始快照（取消还原 / 判断是否有改动）
    private int inType0, inX0, inY0, maType0, maX0, maY0;
    private int cdType0, cdX0, cdY0;
    private boolean cdRight0;
    private int sbType0, sbX0, sbY0;
    private int chType0, chX0, chY0;
    private boolean chRight0;
    private int mgType, mgX, mgY, mgType0, mgX0, mgY0;
    private int stType, stX, stY, stType0, stX0, stY0;
    private ButtonWidget stTypeBtn;
    private OffsetSlider stXSlider, stYSlider;
    private TextFieldWidget stXField, stYField;
    // 本能、能量、CD、书栏、蓄力、SSC法术栏、独立稳定度条。
    private final boolean[] shown = new boolean[7];
    private final boolean[] shown0 = new boolean[7];
    private final ButtonWidget[] visibilityButtons = new ButtonWidget[7];
    private ButtonWidget mgTypeBtn;
    private OffsetSlider mgXSlider, mgYSlider;
    private TextFieldWidget mgXField, mgYField;
    private boolean snapshotTaken = false;

    // 防止联动回填时循环触发回调
    private boolean suppressCallbacks = false;


    // 控件引用
    private ButtonWidget inTypeBtn, maTypeBtn, cdTypeBtn, cdSideBtn, sbTypeBtn, chTypeBtn, chSideBtn;
    private OffsetSlider inXSlider, inYSlider, maXSlider, maYSlider, cdXSlider, cdYSlider, sbXSlider, sbYSlider, chXSlider, chYSlider;
    private TextFieldWidget inXField, inYField, maXField, maYField, cdXField, cdYField, sbXField, sbYField, chXField, chYField;

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
            mgType = mgType0 = cfg.magicGUIPosType;
            mgX = mgX0 = cfg.magicGUIPosOffsetX;
            mgY = mgY0 = cfg.magicGUIPosOffsetY;
            // SSCA CD 条从附属自己的 client config 读取
            SSCAddonClientConfig sscCfg;
            try {
                sscCfg = SSCAddonConfig.client();
            } catch (Exception e) {
                sscCfg = null;
            }
            if (sscCfg != null) {
                sscCfg.migrateSkillHudLayout();
                cdType = cdType0 = sscCfg.cdBarPosType;
                cdX = cdX0 = sscCfg.cdBarPosOffsetX;
                cdY = cdY0 = sscCfg.cdBarPosOffsetY;
                cdRight = cdRight0 = sscCfg.cdMirrorRight;
                sbType = sbType0 = sscCfg.spellbookHudPosType;
                sbX = sbX0 = sscCfg.spellbookHudPosOffsetX;
                sbY = sbY0 = sscCfg.spellbookHudPosOffsetY;
                chType = chType0 = sscCfg.chargeBarPosType;
                chX = chX0 = sscCfg.chargeBarPosOffsetX;
                chY = chY0 = sscCfg.chargeBarPosOffsetY;
                chRight = chRight0 = sscCfg.chargeMirrorRight;
                stType = stType0 = sscCfg.neoStabilityBarPosType;
                stX = stX0 = sscCfg.neoStabilityBarPosOffsetX;
                stY = stY0 = sscCfg.neoStabilityBarPosOffsetY;
            } else {
                cdType = cdType0 = DEF_CD_TYPE;
                cdX = cdX0 = DEF_CD_X;
                cdY = cdY0 = DEF_CD_Y;
                cdRight = cdRight0 = false;
                sbType = sbType0 = DEF_SB_TYPE;
                sbX = sbX0 = DEF_SB_X;
                sbY = sbY0 = DEF_SB_Y;
                chType = chType0 = DEF_CH_TYPE;
                chX = chX0 = DEF_CH_X;
                chY = chY0 = DEF_CH_Y;
                chRight = chRight0 = true;
                stType = stType0 = 7;
                stX = stX0 = 16;
                stY = stY0 = -88;
            }
            if (sscCfg != null) {
                shown[0] = sscCfg.showInstinctBar;
                shown[1] = sscCfg.showManaBar;
                shown[2] = sscCfg.showCdBar;
                shown[3] = sscCfg.showSpellbookHud;
                shown[4] = sscCfg.showChargeBar;
                shown[5] = sscCfg.showSscMagicHud;
                shown[6] = sscCfg.showNeoStabilityBar;
            } else {
                Arrays.fill(shown, true);
            }
            System.arraycopy(shown, 0, shown0, 0, shown.length);
            snapshotTaken = true;
        }

        // 每次重建只保留当前目标的控件，避免旧输入框继续接收焦点或回调。
        Arrays.fill(visibilityButtons, null);
        inTypeBtn = maTypeBtn = cdTypeBtn = cdSideBtn = sbTypeBtn = chTypeBtn = chSideBtn = mgTypeBtn = stTypeBtn = null;
        inXSlider = inYSlider = maXSlider = maYSlider = cdXSlider = cdYSlider = sbXSlider = sbYSlider = chXSlider = chYSlider = mgXSlider = mgYSlider = stXSlider = stYSlider = null;
        inXField = inYField = maXField = maYField = cdXField = cdYField = sbXField = sbYField = chXField = chYField = mgXField = mgYField = stXField = stYField = null;
        if (exitMenuOpen) {
            buildExitMenu();
            return;
        }
        final int panelW = 200;
        int panelX = (width - panelW) / 2;
        final int sliderW = 116;
        final int fieldW = 36;
        final int rowH = 13;
        final int ctrlH = 11;
        final int typeBtnW = selectedSection() == 2 || selectedSection() == 4 ? 100 : 156, typeBtnH = 13;
        int section = selectedSection();
        int sectionTop = Math.max(48, height / 2 - 25);
        int[] target = selectedBounds();
        if (target != null && panelX - 8 < target[0] + target[2] && panelX + panelW + 8 > target[0]
                && sectionTop - 8 < target[1] + target[3] && sectionTop + 63 > target[1]) {
            int above = target[1] - 80, below = target[1] + target[3] + 22;
            if (above >= 8) sectionTop = above;
            else if (below + 63 < height - 25) sectionTop = below;
            else panelX = target[0] > width / 2 ? 8 : width - panelW - 8;
        }
        final int contentRight = panelX + panelW;
        panelLeft = panelX - 8;
        panelRight = contentRight + 8;
        panelTop = sectionTop - 8;
        panelBottom = sectionTop + 63;
        if (section >= 0) {
            buildSection(section, sectionTop, panelX, sliderW, fieldW, rowH, ctrlH, typeBtnW, typeBtnH, contentRight);
            addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.reset_current", sectionName(section)), b -> doResetSection(section))
                    .dimensions(panelX, sectionTop + 43, panelW, 14).build());
        }

        // 公共操作只占右上角，避免覆盖快捷栏及底部 HUD。
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.close"), b -> openExitMenu())
                .dimensions(width - 24, 4, 20, 20)
                .tooltip(Tooltip.of(Text.translatable("text.ssc_addon.bar_editor.exit.title"))).build());

        syncAllControls();
    }

    private int selectedSection() {
        return switch (selected) {
            case DRAG_INSTINCT -> 0;
            case DRAG_MANA -> 1;
            case DRAG_CD -> 2;
            case DRAG_SPELLBOOK -> 3;
            case DRAG_CHARGE -> 4;
            case DRAG_MAGIC -> 5;
            case DRAG_STABILITY -> 6;
            default -> -1;
        };
    }

    /** 区块索引 → 名称 lang 键参（0=本能 1=能量 2=CD 3=魔法书 4=蓄力条 5=SSC法术栏）。 */
    private String sectionName(int section) {
        return switch (section) {
            case 0 -> Text.translatable("text.ssc_addon.bar_editor.instinct").getString();
            case 1 -> Text.translatable("text.ssc_addon.bar_editor.mana").getString();
            case 2 -> Text.translatable("text.ssc_addon.bar_editor.cd").getString();
            case 3 -> Text.translatable("text.ssc_addon.bar_editor.spellbook").getString();
            case 4 -> Text.translatable("text.ssc_addon.bar_editor.charge").getString();
            case 6 -> Text.translatable("text.ssc_addon.bar_editor.stability").getString();
            default -> Text.translatable("text.ssc_addon.bar_editor.ssc_magic").getString();
        };
    }

    /** 重置指定区块（独立重置按钮回调）。 */
    private void doResetSection(int section) {
        shown[section] = true;
        switch (section) {
            case 0 -> doResetInstinct();
            case 1 -> doResetMana();
            case 2 -> doResetCd();
            case 3 -> doResetSpellbook();
            case 4 -> doResetCharge();
            case 6 -> { stType = 7; stX = 16; stY = -88; onWorkingChanged(); }
            default -> { mgType = DEF_MG_TYPE; mgX = DEF_MG_X; mgY = DEF_MG_Y; onWorkingChanged(); }
        }
    }

    /** 按索引构建一个区块的控件（滚动窗口内调用；不在窗口的区块控件不创建）。 */
    private void buildSection(int section, int top, int panelX, int sliderW, int fieldW,
            int rowH, int ctrlH, int typeBtnW, int typeBtnH, int contentRight) {
        visibilityButtons[section] = addDrawableChild(ButtonWidget.builder(visibilityText(section), b -> {
            shown[section] = !shown[section];
            onWorkingChanged();
        }).dimensions(panelX + 160, top, 40, typeBtnH).build());
        switch (section) {
            case 6 -> {
                stTypeBtn = addDrawableChild(ButtonWidget.builder(anchorBtnText("stability", stType), b -> { stType = stType % 9 + 1; onWorkingChanged(); }).dimensions(panelX, top, typeBtnW, typeBtnH).build());
                stXSlider = addDrawableChild(new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", stX, v -> { stX = v; onWorkingChanged(); }));
                stXField = addDrawableChild(makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { stX = v; onWorkingChanged(); }));
                stYSlider = addDrawableChild(new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", stY, v -> { stY = v; onWorkingChanged(); }));
                stYField = addDrawableChild(makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { stY = v; onWorkingChanged(); }));
            }
            case 5 -> {
                mgTypeBtn = addDrawableChild(ButtonWidget.builder(anchorBtnText("ssc_magic", mgType), b -> {
                    mgType = mgType % 9 + 1;
                    onWorkingChanged();
                }).dimensions(panelX, top, typeBtnW, typeBtnH).build());
                mgXSlider = addDrawableChild(new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", mgX, v -> { mgX = v; onWorkingChanged(); }));
                mgXField = addDrawableChild(makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { mgX = v; onWorkingChanged(); }));
                mgYSlider = addDrawableChild(new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", mgY, v -> { mgY = v; onWorkingChanged(); }));
                mgYField = addDrawableChild(makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { mgY = v; onWorkingChanged(); }));
            }
            case 0 -> {
                inTypeBtn = ButtonWidget.builder(anchorBtnText("instinct", inType), b -> cycleType(true))
                        .dimensions(panelX, top, typeBtnW, typeBtnH).build();
                addDrawableChild(inTypeBtn);
                inXSlider = new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", inX, v -> { inX = v; onWorkingChanged(); });
                addDrawableChild(inXSlider);
                inXField = makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { inX = v; onWorkingChanged(); });
                addDrawableChild(inXField);
                inYSlider = new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", inY, v -> { inY = v; onWorkingChanged(); });
                addDrawableChild(inYSlider);
                inYField = makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { inY = v; onWorkingChanged(); });
                addDrawableChild(inYField);
            }
            case 1 -> {
                maTypeBtn = ButtonWidget.builder(anchorBtnText("mana", maType), b -> cycleType(false))
                        .dimensions(panelX, top, typeBtnW, typeBtnH).build();
                addDrawableChild(maTypeBtn);
                maXSlider = new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", maX, v -> { maX = v; onWorkingChanged(); });
                addDrawableChild(maXSlider);
                maXField = makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { maX = v; onWorkingChanged(); });
                addDrawableChild(maXField);
                maYSlider = new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", maY, v -> { maY = v; onWorkingChanged(); });
                addDrawableChild(maYSlider);
                maYField = makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { maY = v; onWorkingChanged(); });
                addDrawableChild(maYField);
            }
            case 2 -> {
                cdTypeBtn = ButtonWidget.builder(anchorBtnText("cd", cdType), b -> cycleTypeCd())
                        .dimensions(panelX, top, typeBtnW, typeBtnH).build();
                addDrawableChild(cdTypeBtn);
                cdSideBtn = ButtonWidget.builder(cdSideText(), b -> toggleCdSide())
                        .dimensions(panelX + typeBtnW + 4, top, 52, typeBtnH).build();
                addDrawableChild(cdSideBtn);
                cdXSlider = new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", cdX, v -> { cdX = v; onWorkingChanged(); });
                addDrawableChild(cdXSlider);
                cdXField = makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { cdX = v; onWorkingChanged(); });
                addDrawableChild(cdXField);
                cdYSlider = new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", cdY, v -> { cdY = v; onWorkingChanged(); });
                addDrawableChild(cdYSlider);
                cdYField = makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { cdY = v; onWorkingChanged(); });
                addDrawableChild(cdYField);
            }
            case 3 -> {
                sbTypeBtn = ButtonWidget.builder(anchorBtnText("spellbook", sbType), b -> cycleTypeSpellbook())
                        .dimensions(panelX, top, typeBtnW, typeBtnH).build();
                addDrawableChild(sbTypeBtn);
                sbXSlider = new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", sbX, v -> { sbX = v; onWorkingChanged(); });
                addDrawableChild(sbXSlider);
                sbXField = makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { sbX = v; onWorkingChanged(); });
                addDrawableChild(sbXField);
                sbYSlider = new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", sbY, v -> { sbY = v; onWorkingChanged(); });
                addDrawableChild(sbYSlider);
                sbYField = makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { sbY = v; onWorkingChanged(); });
                addDrawableChild(sbYField);
            }
            default -> {
                chTypeBtn = ButtonWidget.builder(anchorBtnText("charge", chType), b -> cycleTypeCharge())
                        .dimensions(panelX, top, typeBtnW, typeBtnH).build();
                addDrawableChild(chTypeBtn);
                chSideBtn = ButtonWidget.builder(chSideText(), b -> toggleChSide())
                        .dimensions(panelX + typeBtnW + 4, top, 52, typeBtnH).build();
                addDrawableChild(chSideBtn);
                chXSlider = new OffsetSlider(panelX, top + rowH, sliderW, ctrlH, "offset_x", chX, v -> { chX = v; onWorkingChanged(); });
                addDrawableChild(chXSlider);
                chXField = makeNumField(panelX + sliderW + 4, top + rowH, fieldW, ctrlH, v -> { chX = v; onWorkingChanged(); });
                addDrawableChild(chXField);
                chYSlider = new OffsetSlider(panelX, top + rowH * 2, sliderW, ctrlH, "offset_y", chY, v -> { chY = v; onWorkingChanged(); });
                addDrawableChild(chYSlider);
                chYField = makeNumField(panelX + sliderW + 4, top + rowH * 2, fieldW, ctrlH, v -> { chY = v; onWorkingChanged(); });
                addDrawableChild(chYField);
            }
        }
    }

    private Text visibilityText(int section) {
        return Text.translatable("text.ssc_addon.bar_editor." + (shown[section] ? "shown" : "hidden"));
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
    private void cycleTypeSpellbook() {
        sbType = sbType % 9 + 1;
        onWorkingChanged();
    }
    private void cycleTypeCharge() {
        chType = chType % 9 + 1;
        onWorkingChanged();
    }
    private void toggleChSide() {
        chRight = !chRight;
        dragging = DRAG_NONE;
        onWorkingChanged();
    }
    private Text chSideText() {
        return Text.translatable(chRight ? "text.ssc_addon.bar_editor.cd_right" : "text.ssc_addon.bar_editor.cd_left");
    }
    private void toggleCdSide() {
        cdRight = !cdRight;
        dragging = DRAG_NONE;
        onWorkingChanged();
    }
    private Text cdSideText() {
        return Text.translatable(cdRight ? "text.ssc_addon.bar_editor.cd_right" : "text.ssc_addon.bar_editor.cd_left");
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
        cfg.magicGUIPosType = mgType;
        cfg.magicGUIPosOffsetX = mgX;
        cfg.magicGUIPosOffsetY = mgY;
        try {
            SSCAddonClientConfig sscCfg = SSCAddonConfig.client();
            sscCfg.showInstinctBar = shown[0];
            sscCfg.showManaBar = shown[1];
            sscCfg.showCdBar = shown[2];
            sscCfg.showSpellbookHud = shown[3];
            sscCfg.showChargeBar = shown[4];
            sscCfg.showSscMagicHud = shown[5];
            sscCfg.showNeoStabilityBar = shown[6];
            sscCfg.neoStabilityBarPosType = stType;
            sscCfg.neoStabilityBarPosOffsetX = stX;
            sscCfg.neoStabilityBarPosOffsetY = stY;
            sscCfg.cdBarPosType = cdType;
            sscCfg.cdBarPosOffsetX = cdX;
            sscCfg.cdBarPosOffsetY = cdY;
            sscCfg.cdMirrorRight = cdRight;
            sscCfg.spellbookHudPosType = sbType;
            sscCfg.spellbookHudPosOffsetX = sbX;
            sscCfg.spellbookHudPosOffsetY = sbY;
            sscCfg.chargeBarPosType = chType;
            sscCfg.chargeBarPosOffsetX = chX;
            sscCfg.chargeBarPosOffsetY = chY;
            sscCfg.chargeMirrorRight = chRight;
        } catch (Exception ignored) {}
    }

    private void syncAllControls() {
        suppressCallbacks = true;
        try {
            for (int i = 0; i < shown.length; i++) {
                if (visibilityButtons[i] != null) visibilityButtons[i].setMessage(visibilityText(i));
            }
            if (mgTypeBtn != null) mgTypeBtn.setMessage(anchorBtnText("ssc_magic", mgType));
            if (mgXSlider != null) mgXSlider.setIntValue(mgX);
            if (mgYSlider != null) mgYSlider.setIntValue(mgY);
            if (mgXField != null) mgXField.setText(String.valueOf(mgX));
            if (mgYField != null) mgYField.setText(String.valueOf(mgY));
            if (stTypeBtn != null) stTypeBtn.setMessage(anchorBtnText("stability", stType));
            if (stXSlider != null) stXSlider.setIntValue(stX);
            if (stYSlider != null) stYSlider.setIntValue(stY);
            if (stXField != null) stXField.setText(String.valueOf(stX));
            if (stYField != null) stYField.setText(String.valueOf(stY));
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
            if (cdSideBtn != null) cdSideBtn.setMessage(cdSideText());
            if (sbTypeBtn != null) sbTypeBtn.setMessage(anchorBtnText("spellbook", sbType));
            if (sbXSlider != null) sbXSlider.setIntValue(sbX);
            if (sbYSlider != null) sbYSlider.setIntValue(sbY);
            if (sbXField != null) sbXField.setText(String.valueOf(sbX));
            if (sbYField != null) sbYField.setText(String.valueOf(sbY));
            if (chTypeBtn != null) chTypeBtn.setMessage(anchorBtnText("charge", chType));
            if (chSideBtn != null) chSideBtn.setMessage(chSideText());
            if (chXSlider != null) chXSlider.setIntValue(chX);
            if (chYSlider != null) chYSlider.setIntValue(chY);
            if (chXField != null) chXField.setText(String.valueOf(chX));
            if (chYField != null) chYField.setText(String.valueOf(chY));
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
    private void doSave() {
        applyToConfig();
        try {
            AutoConfig.getConfigHolder(ClientConfig.class).save();
        } catch (Exception ignored) {}
        // 同步保存附属 client config（CD 条位置）
        try {
            AutoConfig.getConfigHolder(SSCAddonClientConfig.class).save();
        } catch (Exception ignored) {}
        System.arraycopy(shown, 0, shown0, 0, shown.length);
        mgType0 = mgType; mgX0 = mgX; mgY0 = mgY;
        stType0 = stType; stX0 = stX; stY0 = stY;
        // 更新快照，避免 close 时又弹确认
        inType0 = inType; inX0 = inX; inY0 = inY;
        maType0 = maType; maX0 = maX; maY0 = maY;
        cdType0 = cdType; cdX0 = cdX; cdY0 = cdY; cdRight0 = cdRight;
        sbType0 = sbType; sbX0 = sbX; sbY0 = sbY;
        chType0 = chType; chX0 = chX; chY0 = chY; chRight0 = chRight;
        MinecraftClient.getInstance().setScreen(parent);
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
        cdType = DEF_CD_TYPE; cdX = DEF_CD_X; cdY = DEF_CD_Y; cdRight = false;
        syncAllControls();
        applyToConfig();
    }

    /** 仅重置月尘魔法书 HUD（不影响其它条）。 */
    private void doResetSpellbook() {
        sbType = DEF_SB_TYPE; sbX = DEF_SB_X; sbY = DEF_SB_Y;
        syncAllControls();
        applyToConfig();
    }

    /** 仅重置法术蓄力条（不影响其它条）。 */
    private void doResetCharge() {
        chType = DEF_CH_TYPE; chX = DEF_CH_X; chY = DEF_CH_Y; chRight = true;
        syncAllControls();
        applyToConfig();
    }

    private void openExitMenu() {
        exitMenuOpen = true;
        dragging = DRAG_NONE;
        guideVX = guideHY = null;
        setFocused(null);
        clearAndInit();
    }

    private void continueEditing() {
        exitMenuOpen = false;
        setFocused(null);
        clearAndInit();
    }

    /** 弹窗只创建三个选项，底层编辑控件不接收点击或键盘输入。 */
    private void buildExitMenu() {
        int buttonW = Math.min(200, width - 48);
        int x = (width - buttonW) / 2, y = height / 2 - 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.exit.save"), b -> doSave())
                .dimensions(x, y, buttonW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("text.ssc_addon.bar_editor.exit.discard"), b -> {
            restoreConfigToSnapshot();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(x, y + 24, buttonW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> continueEditing())
                .dimensions(x, y + 48, buttonW, 20).build());
    }

    /** 取消时把工作值与配置都还原到进入编辑器时的快照。 */
    private void restoreConfigToSnapshot() {
        System.arraycopy(shown0, 0, shown, 0, shown.length);
        mgType = mgType0; mgX = mgX0; mgY = mgY0;
        stType = stType0; stX = stX0; stY = stY0;
        inType = inType0; inX = inX0; inY = inY0;
        maType = maType0; maX = maX0; maY = maY0;
        cdType = cdType0; cdX = cdX0; cdY = cdY0; cdRight = cdRight0;
        sbType = sbType0; sbX = sbX0; sbY = sbY0;
        chType = chType0; chX = chX0; chY = chY0; chRight = chRight0;
        applyToConfig();
    }

    @Override
    public void close() {
        if (exitMenuOpen) continueEditing();
        else openExitMenu();
    }

    // ====== 拖拽 / 选中预览条 ======
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 弹窗开启时，任何点击都不能穿透到背后的 HUD。
        if (exitMenuOpen) {
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        // 面板控件和关闭按钮优先，面板内部留白不穿透到其后的 HUD。
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (selected != DRAG_NONE && mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) return true;
        if (button == 0) {
            int hit = hitStability(mouseX, mouseY) ? DRAG_STABILITY
                    : hitMagic(mouseX, mouseY) ? DRAG_MAGIC
                    : hitChargeBar(mouseX, mouseY) ? DRAG_CHARGE
                    : hitSpellbook(mouseX, mouseY) ? DRAG_SPELLBOOK
                    : hitCdBar(mouseX, mouseY) ? DRAG_CD
                    : hitBar(mouseX, mouseY, true) ? DRAG_MANA
                    : hitBar(mouseX, mouseY, false) ? DRAG_INSTINCT : DRAG_NONE;
            if (hit != DRAG_NONE) {
                selected = dragging = hit;
                int x = switch (hit) { case DRAG_STABILITY -> stX; case DRAG_MAGIC -> mgX; case DRAG_CHARGE -> chX; case DRAG_SPELLBOOK -> sbX; case DRAG_CD -> cdX; case DRAG_MANA -> maX; default -> inX; };
                int y = switch (hit) { case DRAG_STABILITY -> stY; case DRAG_MAGIC -> mgY; case DRAG_CHARGE -> chY; case DRAG_SPELLBOOK -> sbY; case DRAG_CD -> cdY; case DRAG_MANA -> maY; default -> inY; };
                beginDrag(mouseX, mouseY, x, y);
                clearAndInit();
                return true;
            }
        }
        // 点击画布空白时同时撤下控件，隐藏调整框但保留已编辑的工作值。
        if (button == 0 && selected != DRAG_NONE) {
            selected = DRAG_NONE;
            dragging = DRAG_NONE;
            guideVX = guideHY = null;
            clearAndInit();
        }
        return button == 0;
    }

    private void beginDrag(double mx, double my, int offX, int offY) {
        if (dragging == DRAG_STABILITY) {
            var position = stabilityPos();
            var anchor = UIPositionUtils.getCorrectPosition(stType, 0, 0);
            offX = position.getLeft() - anchor.getLeft();
            offY = position.getRight() - anchor.getRight();
        } else if (dragging == DRAG_CD) {
            var position = cdBarPos();
            var anchor = UIPositionUtils.getCorrectPosition(cdType, 0, 0);
            offX = position.getLeft() - anchor.getLeft();
            offY = position.getRight() - anchor.getRight();
        } else if (dragging == DRAG_CHARGE) {
            var position = chargeBarPos();
            var anchor = UIPositionUtils.getCorrectPosition(chType, 0, 0);
            offX = position.getLeft() - anchor.getLeft();
            offY = position.getRight() - anchor.getRight();
        }
        dragStartMouseX = mx;
        dragStartMouseY = my;
        dragStartOffX = offX;
        dragStartOffY = offY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != DRAG_NONE) {
            if (dragging == DRAG_STABILITY) {
                var a = UIPositionUtils.getCorrectPosition(stType, 0, 0);
                int x = a.getLeft() + dragStartOffX + (int) Math.round(mouseX - dragStartMouseX);
                int y = a.getRight() + dragStartOffY + (int) Math.round(mouseY - dragStartMouseY);
                if (!hasShiftDown()) { x = Math.round(x / (float) GRID) * GRID; y = Math.round(y / (float) GRID) * GRID; }
                x = Math.max(0, Math.min(Math.max(0, width - NeoStabilityBar.WIDTH), x));
                y = Math.max(0, Math.min(Math.max(0, height - NeoStabilityBar.HEIGHT), y));
                stX = clampOffset(x - a.getLeft()); stY = clampOffset(y - a.getRight());
                onWorkingChanged(); return true;
            }
            if (dragging == DRAG_MAGIC) {
                var a = UIPositionUtils.getCorrectPosition(mgType, 0, 0);
                int x = a.getLeft() + dragStartOffX + (int) Math.round(mouseX - dragStartMouseX);
                int y = a.getRight() + dragStartOffY + (int) Math.round(mouseY - dragStartMouseY);
                if (!hasShiftDown()) {
                    x = Math.round(x / (float) GRID) * GRID;
                    y = Math.round(y / (float) GRID) * GRID;
                }
                x = Math.max(0, Math.min(Math.max(0, width - MG_W), x + MG_ORIGIN_DX)) - MG_ORIGIN_DX;
                y = Math.max(0, Math.min(Math.max(0, height - MG_H), y));
                mgX = clampOffset(x - a.getLeft());
                mgY = clampOffset(y - a.getRight());
                onWorkingChanged();
                return true;
            }
            if (dragging == DRAG_SPELLBOOK || dragging == DRAG_CHARGE) {
                boolean charge = dragging == DRAG_CHARGE;
                int curType = charge ? chType : sbType;
                int dx = (int) Math.round(mouseX - dragStartMouseX);
                int dy = (int) Math.round(mouseY - dragStartMouseY);
                Pair<Integer, Integer> a = UIPositionUtils.getCorrectPosition(curType, 0, 0);
                int scrX = a.getLeft() + dragStartOffX + dx;
                int scrY = a.getRight() + dragStartOffY + dy;
                guideVX = null;
                guideHY = null;
                if (!hasShiftDown()) {
                    scrX = Math.round(scrX / (float) GRID) * GRID;
                    scrY = Math.round(scrY / (float) GRID) * GRID;
                }
                int barW = charge ? CH_W : SB_W;
                int barH = charge ? CH_H : SB_H;
                if (charge) {
                    // 蓄力条 pos 即贴图左上，直接 clamp 即可
                    scrX = clampScreenX(scrX, barW);
                    scrY = clampScreenY(scrY, barH);
                } else {
                    // 三个等大槽与下方名称使用与正式 HUD 相同的完整包围盒。
                    scrX = clampScreenX(scrX + SB_ORIGIN_DX, barW) - SB_ORIGIN_DX;
                    scrY = clampScreenY(scrY + SB_ORIGIN_DY, barH) - SB_ORIGIN_DY;
                }
                if (charge) {
                    // 偏移语义与渲染器一致（CD 条同式）：左缘贴锚点；贴右侧时镜像换算（拖拽 x 即面板左上角 x）
                    chX = chRight ? clampOffset(width - scrX - CH_W - a.getLeft())
                            : clampOffset(scrX - a.getLeft());
                    chY = clampOffset(scrY - a.getRight());
                } else {
                    sbX = clampOffset(scrX - a.getLeft());
                    sbY = clampOffset(scrY - a.getRight());
                }
                syncAllControls();
                applyToConfig();
                return true;
            }
            boolean mana = dragging == DRAG_MANA;
            boolean cd = dragging == DRAG_CD;
            boolean cdLike = cd;
            int curType = cdLike ? cdType : (mana ? maType : inType);
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
                int[] sx = snapAxis(scrX, mana, cdLike, true);
                scrX = sx[0];
                if (sx[1] != Integer.MIN_VALUE) guideVX = sx[1];
                int[] sy = snapAxis(scrY, mana, cdLike, false);
                scrY = sy[0];
                if (sy[1] != Integer.MIN_VALUE) guideHY = sy[1];
            }
            // 安全边界：条至少有一部分留在屏幕内
            int barW = cdLike ? CD_W : BAR_W;
            int barH = cdLike ? SkillCooldownBarRenderer.PANEL_HEIGHT : BAR_H;
            scrX = cdLike ? Math.max(0, Math.min(Math.max(0, width - barW), scrX)) : clampScreenX(scrX, barW);
            scrY = clampScreenY(scrY, barH);
            int nx = clampOffset(scrX - anchor.getLeft());
            int ny = clampOffset(scrY - anchor.getRight());
            if (cd) { cdX = cdRight ? clampOffset(width - scrX - CD_W - anchor.getLeft()) : nx; cdY = ny; }
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
            clearAndInit();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // 方向键微调选中的条（未聚焦文本框时）；Shift 大步
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (exitMenuOpen) return super.keyPressed(keyCode, scanCode, modifiers);
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
            if (selected == DRAG_STABILITY) { stX = clampOffset(stX + dx); stY = clampOffset(stY + dy); }
            else if (selected == DRAG_MAGIC) { mgX = clampOffset(mgX + dx); mgY = clampOffset(mgY + dy); }
            else if (selected == DRAG_SPELLBOOK) { sbX = clampOffset(sbX + dx); sbY = clampOffset(sbY + dy); }
            else if (selected == DRAG_CHARGE) { chX = clampOffset(chX + (chRight ? -dx : dx)); chY = clampOffset(chY + dy); }
            else if (selected == DRAG_CD) { cdX = clampOffset(cdX + (cdRight ? -dx : dx)); cdY = clampOffset(cdY + dy); }
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
        int size = horizontal ? (cd ? CD_W : BAR_W) : (cd ? SkillCooldownBarRenderer.PANEL_HEIGHT : BAR_H);
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
        return mouseX >= x - 2 && mouseX <= x + CD_W + 2
            && mouseY >= y - 2 && mouseY <= y + SkillCooldownBarRenderer.PANEL_HEIGHT + 2;
    }

    /** 蓄力条屏幕坐标（与 SpellCastHud 同式：左缘贴锚点 + clamp 防出屏；贴右侧时镜像换算）。 */
    private Pair<Integer, Integer> chargeBarPos() {
        var anchor = UIPositionUtils.getCorrectPosition(chType, 0, 0);
        int x = Math.max(0, Math.min(Math.max(0, width - CH_W), anchor.getLeft() + chX));
        if (chRight) x = Math.max(0, width - x - CH_W);
        int y = Math.max(0, Math.min(Math.max(0, height - CH_H), anchor.getRight() + chY));
        return new Pair<>(x, y);
    }

    /** 判断鼠标是否落在蓄力条预览的热区。 */
    private boolean hitChargeBar(double mouseX, double mouseY) {
        Pair<Integer, Integer> pos = chargeBarPos();
        int x = pos.getLeft();
        int y = pos.getRight();
        return mouseX >= x - 2 && mouseX <= x + CH_W + 2
            && mouseY >= y - 2 && mouseY <= y + CH_H + 2;
    }

    private Pair<Integer, Integer> barPos(boolean mana) {
        if (mana) {
            return UIPositionUtils.getCorrectPosition(maType, maX, maY);
        }
        return UIPositionUtils.getCorrectPosition(inType, inX, inY);
    }

    /** CD 条（主技能左侧）屏幕坐标。 */
    private Pair<Integer, Integer> cdBarPos() {
        var layout = cdLayout();
        return new Pair<>(layout.primaryX(), layout.primaryY());
    }

    private SkillCooldownBarRenderer.Layout cdLayout() {
        var anchor = UIPositionUtils.getCorrectPosition(cdType, 0, 0);
        return SkillCooldownBarRenderer.panelLayout(anchor.getLeft() + cdX, anchor.getRight() + cdY,
                cdRight, width, height);
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

        // 能量 / 本能条 / CD 条可拖拽手柄（半透明示意 + 边框 + 标签），确保任何形态都能看到并拖拽
        drawBarHandle(ctx, mouseX, mouseY, false);
        drawBarHandle(ctx, mouseX, mouseY, true);
        drawCdBarHandle(ctx, mouseX, mouseY);
        drawSpellbookHandle(ctx, mouseX, mouseY);
        drawChargeBarHandle(ctx, mouseX, mouseY);

        drawMagicHandle(ctx, mouseX, mouseY);
        drawStabilityHandle(ctx, mouseX, mouseY);

        if (exitMenuOpen) {
            ctx.fill(0, 0, width, height, 0x99000000);
            int buttonW = Math.min(200, width - 48), x = (width - buttonW) / 2;
            int y = height / 2 - 62;
            ctx.fill(x - 10, y, x + buttonW + 10, y + 110, 0xEE202020);
            ctx.drawBorder(x - 10, y, buttonW + 20, 110, 0xFF777777);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("text.ssc_addon.bar_editor.exit.title"), width / 2, y + 12, 0xFFFFFF);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        // 未选中时不绘制调整框，也不存在中间输入控件。
        if (selected != DRAG_NONE) drawControlPanelBackground(ctx);

        // 控件（滑条 / 数值框 / 按钮）
        super.render(ctx, mouseX, mouseY, delta);

        // 拖拽时的对齐参考线（贯穿屏幕）
        if (dragging != DRAG_NONE) {
            if (guideVX != null) ctx.fill(guideVX, 0, guideVX + 1, this.height, 0x88FFEE00);
            if (guideHY != null) ctx.fill(0, guideHY, this.width, guideHY + 1, 0x88FFEE00);
        }

        // 标题 + 提示
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc_addon.bar_editor.select_hint"), width / 2, 26, 0xFFAAAAAA);
    }

    private Pair<Integer, Integer> stabilityPos() {
        int[] pos = NeoStabilityBar.position(stType, stX, stY, width, height);
        return new Pair<>(pos[0], pos[1]);
    }

    private boolean hitStability(double x, double y) {
        var p = stabilityPos();
        return x >= p.getLeft() - 2 && x <= p.getLeft() + NeoStabilityBar.WIDTH + 2
                && y >= p.getRight() - 2 && y <= p.getRight() + NeoStabilityBar.HEIGHT + 2;
    }

    private int[] selectedBounds() {
        Pair<Integer, Integer> p;
        return switch (selected) {
            case DRAG_INSTINCT, DRAG_MANA -> { p = barPos(selected == DRAG_MANA); yield new int[]{p.getLeft(), p.getRight(), BAR_W, BAR_H}; }
            case DRAG_CD -> { p = cdBarPos(); yield new int[]{p.getLeft(), p.getRight(), CD_W, SkillCooldownBarRenderer.PANEL_HEIGHT}; }
            case DRAG_SPELLBOOK -> { p = spellbookPos(); yield new int[]{p.getLeft() + SB_ORIGIN_DX, p.getRight() + SB_ORIGIN_DY, SB_W, SB_H}; }
            case DRAG_CHARGE -> { p = chargeBarPos(); yield new int[]{p.getLeft(), p.getRight(), CH_W, CH_H}; }
            case DRAG_MAGIC -> { p = magicPos(); yield new int[]{p.getLeft() + MG_ORIGIN_DX, p.getRight(), MG_W, MG_H}; }
            case DRAG_STABILITY -> { p = stabilityPos(); yield new int[]{p.getLeft(), p.getRight(), NeoStabilityBar.WIDTH, NeoStabilityBar.HEIGHT}; }
            default -> null;
        };
    }

    private void drawStabilityHandle(DrawContext ctx, int mouseX, int mouseY) {
        var p = stabilityPos(); int x = p.getLeft(), y = p.getRight();
        var n = net.ssca.neomagic.client.NeoClient.hud;
        if (!n.contains("S")) {
            n = new net.minecraft.nbt.NbtCompound(); n.putDouble("C",70); n.putDouble("S",80); n.putDouble("Q",25); n.putInt("Age",-1); n.putInt("Recovery",40); n.putBoolean("HasSpell",true);
        }
        NeoStabilityBar.draw(ctx, n, x, y);
        int border = dragging == DRAG_STABILITY ? 0xFFFFEE00 : selected == DRAG_STABILITY ? 0xFF00FF88 : hitStability(mouseX,mouseY) ? 0xFFFFFFAA : 0xFF536169;
        ctx.drawBorder(x - 1, y - 1, NeoStabilityBar.WIDTH + 2, NeoStabilityBar.HEIGHT + 2, border);
        Text label = Text.translatable("text.ssc_addon.bar_editor.stability");
        if (!shown[6]) label = label.copy().append(" (").append(visibilityText(6)).append(")");
        ctx.drawTextWithShadow(textRenderer, label, x, y - 11, 0xFF7CD4E5);
    }

    private Pair<Integer, Integer> magicPos() {
        return UIPositionUtils.getCorrectPosition(mgType, mgX, mgY);
    }

    private boolean hitMagic(double mx, double my) {
        var p = magicPos();
        return mx >= p.getLeft() + MG_ORIGIN_DX - 2 && mx <= p.getLeft() + MG_ORIGIN_DX + MG_W + 2
                && my >= p.getRight() - 2 && my <= p.getRight() + MG_H + 2;
    }

    private void drawMagicHandle(DrawContext ctx, int mouseX, int mouseY) {
        var p = magicPos();
        int ax = p.getLeft(), y = p.getRight(), x = ax + MG_ORIGIN_DX;
        ctx.fill(ax + 6, y + 4, ax + 22, y + 20, 0x8050C8EF);
        ctx.fill(ax + 42, y + 4, ax + 58, y + 20, 0x8050C8EF);
        ctx.fill(ax + 20, y, ax + 44, y + 24, 0xA050C8EF);
        int color = dragging == DRAG_MAGIC ? 0xFFFFEE00 : selected == DRAG_MAGIC ? 0xFF00FF88
                : hitMagic(mouseX, mouseY) ? 0xFFFFFFAA : 0xFF000000;
        ctx.drawBorder(x - 1, y - 1, MG_W + 2, MG_H + 2, color);
        Text label = Text.translatable("text.ssc_addon.bar_editor.ssc_magic");
        if (!shown[5]) label = label.copy().append(" (").append(visibilityText(5)).append(")");
        ctx.drawTextWithShadow(textRenderer, label, x, y - 11, 0xFF50C8EF);
        if (selected == DRAG_MAGIC) ctx.drawTextWithShadow(textRenderer, Text.literal("(" + mgX + ", " + mgY + ")"), x, y + MG_H + 2, 0xFFFFFFFF);
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

    private void drawCdBarHandle(DrawContext ctx, int mouseX, int mouseY) {
        Pair<Integer, Integer> pos = cdBarPos();
        int x = pos.getLeft();
        int y = pos.getRight();
        int columnHeight = SkillCooldownBarRenderer.PANEL_HEIGHT;
        if (client == null || client.world == null || client.player == null) {
            SkillCooldownBarRenderer.drawPanel(ctx, x, y, cdRight);
        }
        // 主条半透明绿色示意
        ctx.fill(x, y, x + CD_W, y + columnHeight, 0x1800FF00);
        boolean hovered = hitCdBar(mouseX, mouseY);
        boolean active = (dragging == DRAG_CD);
        boolean sel = (selected == DRAG_CD);
        int border = active ? 0xFFFFEE00 : (sel ? 0xFF00FF88 : (hovered ? 0xFFFFFFAA : 0xFF000000));
        ctx.drawBorder(x - 1, y - 1, CD_W + 2, columnHeight + 2, border);
        Text label = Text.translatable("text.ssc_addon.bar_editor.cd_panel");
        if (hovered && !active) ctx.drawTooltip(this.textRenderer, label, mouseX, mouseY);
        // 选中/拖拽时显示主条偏移坐标
        if (sel || active) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(" + cdX + ", " + cdY + ")"),
                    Math.max(0, x - 75), y + 12, 0xFFFFFFFF);
        }
    }

    /** 月尘魔法书 HUD 整体锚点屏幕坐标（= 单元逻辑原点 baseX/baseY）。 */
    private Pair<Integer, Integer> spellbookPos() {
        Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(sbType, sbX, sbY);
        return new Pair<>(SpellbookHudRenderer.clampX(pos.getLeft(), width),
                SpellbookHudRenderer.clampY(pos.getRight(), height));
    }

    /** 判断鼠标是否落在月尘魔法书 HUD 预览包围盒热区。 */
    private boolean hitSpellbook(double mouseX, double mouseY) {
        Pair<Integer, Integer> pos = spellbookPos();
        int x = pos.getLeft() + SB_ORIGIN_DX;
        int y = pos.getRight() + SB_ORIGIN_DY;
        return mouseX >= x - 2 && mouseX <= x + SB_W + 2 && mouseY >= y - 3 && mouseY <= y + SB_H + 3;
    }

    /** 三槽实图与名称共用拖动区域，稳定度条单独调整。 */
    private void drawSpellbookHandle(DrawContext ctx, int mouseX, int mouseY) {
        Pair<Integer, Integer> pos = spellbookPos();
        int ax = pos.getLeft(), ay = pos.getRight();       // 锚点(baseX,baseY)
        int x = ax + SB_ORIGIN_DX, y = ay + SB_ORIGIN_DY;  // 包围盒左上
        SpellbookHudRenderer.drawPreview(ctx, ax, ay);
        boolean hovered = hitSpellbook(mouseX, mouseY);
        boolean active = (dragging == DRAG_SPELLBOOK);
        boolean sel = (selected == DRAG_SPELLBOOK);
        int border = active ? 0xFFFFEE00 : (sel ? 0xFF00FF88 : (hovered ? 0xFFFFFFAA : 0xFF000000));
        ctx.drawBorder(x - 1, y - 1, SB_W + 2, SB_H + 2, border);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc_addon.bar_editor.spellbook"), x, y - 11, 0xFFCDAAFF);
        if (sel || active) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(" + sbX + ", " + sbY + ")"),
                    x, y + SB_H + 2, 0xFFFFFFFF);
        }
    }

    /** 法术蓄力条可拖拽手柄：真实贴图示意 + 边框 + 标签（施法时才会弹出，编辑器常驻显示位置）。 */
    private void drawChargeBarHandle(DrawContext ctx, int mouseX, int mouseY) {
        Pair<Integer, Integer> pos = chargeBarPos();
        int x = pos.getLeft();
        int y = pos.getRight();
        // 空框贴图示意（与实际 HUD 同贴图；左/右双贴图选图，直观）
        ctx.drawTexture(chRight ? CHARGE_TEX_EMPTY : CHARGE_TEX_EMPTY_LEFT, x, y, 0, 0, CH_W, CH_H, CH_W, CH_H);
        boolean hovered = hitChargeBar(mouseX, mouseY);
        boolean active = (dragging == DRAG_CHARGE);
        boolean sel = (selected == DRAG_CHARGE);
        int border = active ? 0xFFFFEE00 : (sel ? 0xFF00FF88 : (hovered ? 0xFFFFFFAA : 0xFF000000));
        ctx.drawBorder(x - 1, y - 1, CH_W + 2, CH_H + 2, border);
        Text label = Text.translatable("text.ssc_addon.bar_editor.charge");
        if (hovered && !active) ctx.drawTooltip(this.textRenderer, label, mouseX, mouseY);
        if (sel || active) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(" + chX + ", " + chY + ")"),
                    x, y + CH_H + 2, 0xFFFFFFFF);
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
            if (!"text.autoconfig.shape-shifter-curse-client.title".equals(ttc.getKey())
                    && !"text.autoconfig.ssc_addon_client.title".equals(ttc.getKey())) return;
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
