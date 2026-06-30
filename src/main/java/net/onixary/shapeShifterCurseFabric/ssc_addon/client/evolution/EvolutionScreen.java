package net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * SSCA 进化加点界面：使魔（familiar_fox）进化树。
 *
 * <p>入口：幻形者之书界面内的「进化加点」按钮（见 EvolutionBookHook）。</p>
 *
 * <p><b>坐标体系</b>：直接使用 Screen 原生的 {@code this.width / this.height}（逻辑坐标，
 * 永远等于填满整个游戏窗口的尺寸）。所有布局基于这两个值动态计算，保证：
 * <ul>
 *   <li>始终全屏覆盖；</li>
 *   <li>在任意 GUI scale 下都不会裁切 / 错位 / 消失。</li>
 * </ul>
 * 鼠标坐标直接使用 {@code mouseX / mouseY}，无需任何转换。</p>
 *
 * <p><b>交互</b>：中间进化树区域支持鼠标拖动平移、滚轮缩放（带边界限制）。
 * 连接线在节点边缘外停止，绝不伸入节点框内。点击节点可解锁（消耗点数）。</p>
 */
public class EvolutionScreen extends Screen {
    /** 进化树布局基础参数（逻辑像素，再乘 zoom）。 */
    private static final int COL_SPACING = 120, ROW_SPACING = 84, NODE_SIZE = 42;
    /** 顶部 / 底部信息条高度。 */
    private static final int TOP_BAR = 44, BOT_BAR = 38;
    /** 树区域相对屏幕的边距。 */
    private static final int TREE_MARGIN_X = 10, TREE_MARGIN_TOP = 4, TREE_MARGIN_BOT = 4;
    /** 配色：面板与边框。 */
    private static final int PANEL_BG = 0xE0100C08, GOLD_BOR = 0xFFB8893A;
    /** 配色：文字。 */
    private static final int TITLE_GOLD = 0xFFE8C66A, TXT_GOLD = 0xFFD8A86A, TXT_GRAY = 0xFFAAAAAA;
    /** 配色：节点状态。 */
    private static final int N_UNLK_FILL = 0x40FFD700, N_UNLK_BOR = 0xFFFFD700, N_UNLK_IN = 0xFFF0E68C;
    private static final int N_CAN_BOR = 0xFF66FF66, N_LK_FILL = 0x99000000, N_LK_BOR = 0xFF555555;
    private static final int N_AUTO_BOR = 0xFF00CED1, N_AUTO_FILL = 0x2000FFFF;
    /** 配色：暂存待确认节点（橙）。 */
    private static final int N_PEND_FILL = 0x40FF9A2E, N_PEND_BOR = 0xFFFF9A2E, N_PEND_IN = 0xFFFFD08A;
    /** 配色：连接线。 */
    private static final int LINE_ACT = 0xFFD4AF37, LINE_GLOW = 0xFFF0E68C, LINE_INACT = 0x668B4513;
    /** 返回按钮配色。 */
    private static final int BW = 100, BH = 22, BBG = 0xC0100C08, BBGH = 0xD0181408;
    private static final int BBOR = 0xFFB8893A, BTXT = 0xFFE8C66A;
    /** 两按钮（返回 / 确认加点）水平间距。 */
    private static final int BTN_GAP = 14;
    /** 确认加点按钮（待确认时）高亮配色。 */
    private static final int CONF_BG = 0xC0102A0C, CONF_BOR = 0xFF55C964, CONF_TXT = 0xFFBFFFC0;
    private static final int CONF_DIS_TXT = 0xFF6A6A6A;
    /** 动画周期（毫秒）。 */
    private static final double PULSE_MS = 2000.0, RIPPLE_MS = 3000.0;
    /** 虚线参数。 */
    private static final int DASH = 4, DGAP = 3;
    /** 缩放 / 平移限制。 */
    private static final double ZOOM_MIN = 0.5, ZOOM_MAX = 2.5, ZOOM_STEP = 0.12, PAN_MARGIN = 0.15;
    /** 进化树尺寸（节点 col 0~5、row 0~2）。 */
    private static final int TREE_COLS = 5, TREE_ROWS = 2;
    private static final int TREE_W = TREE_COLS * COL_SPACING, TREE_H = TREE_ROWS * ROW_SPACING;
    /** 经验等级里程碑（与 EvolutionManager 一致，仅用于界面提示）。 */
    private static final int[] LEVEL_MILESTONES = {5, 10, 15, 20, 30, 40, 45};

    private final Screen parent;
    /** 返回按钮矩形（逻辑坐标）。 */
    private int[] btnRect = new int[4];
    private boolean btnHovered;
    /** 确认加点按钮矩形（逻辑坐标）。 */
    private int[] confBtnRect = new int[4];
    private boolean confHovered;
    /** 客户端暂存：本次待确认解锁的节点（按点击顺序，确认后逐个提交服务端）。 */
    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    /** 拖动状态。 */
    private boolean dragging = false;
    private double dragStartMX, dragStartMY, dragStartPX, dragStartPY;
    /** 进化树平移 / 缩放。 */
    private double panX = 0, panY = 0, zoom = 1.0;
    /** 树区域几何缓存（render 时计算）。 */
    private int treeLeft, treeTop, treeW, treeH;

    public EvolutionScreen(Screen parent) {
        super(Text.translatable("evolution.my_addon.screen.title"));
        this.parent = parent;
    }

    private EvolutionComponent getComp() {
        PlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? null : RegEvolutionComponent.EVOLUTION.get(p);
    }

    @Override
    protected void init() {
        // 不创建默认 ButtonWidget：返回按钮自绘，以便统一深色+金色风格。
        updateBtn();
        pending.clear();
        panX = 0;
        panY = 0;
        zoom = 1.0;
    }

    private void updateBtn() {
        int total = BW * 2 + BTN_GAP;
        int left = this.width / 2 - total / 2;
        int y = this.height - BOT_BAR - BH / 2 - 2;
        btnRect[0] = left;
        btnRect[1] = y;
        btnRect[2] = BW;
        btnRect[3] = BH;
        confBtnRect[0] = left + BW + BTN_GAP;
        confBtnRect[1] = y;
        confBtnRect[2] = BW;
        confBtnRect[3] = BH;
    }

    // ---------------- 树区域几何（逻辑坐标，每帧刷新） ----------------

    private void updateTreeRect() {
        treeLeft = TREE_MARGIN_X;
        treeTop = TOP_BAR + TREE_MARGIN_TOP;
        treeW = this.width - TREE_MARGIN_X * 2;
        treeH = this.height - TOP_BAR - BOT_BAR - TREE_MARGIN_TOP - TREE_MARGIN_BOT;
    }

    private double tcX() { return (double) TREE_W / 2.0; }
    private double tcY() { return (double) TREE_H / 2.0; }

    /** 树坐标 → 屏幕逻辑坐标（含 zoom + pan）。 */
    private double toSX(double tx) { return treeLeft + treeW / 2.0 + (tx - tcX()) * zoom + panX; }
    private double toSY(double ty) { return treeTop + treeH / 2.0 + (ty - tcY()) * zoom + panY; }
    private double ncx(EvolutionNode n) { return toSX(n.col * COL_SPACING); }
    private double ncy(EvolutionNode n) { return toSY(n.row * ROW_SPACING); }
    private double sns() { return NODE_SIZE * zoom; }
    private double hn() { return sns() / 2.0; }

    // ---------------- 缩放 / 平移限制 ----------------

    private double clampZ(double z) { return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, z)); }

    private void clampPan() {
        double sw = treeW, sh = treeH, tw = TREE_W * zoom, th = TREE_H * zoom;
        double mx = Math.max(0, (tw - sw) / 2.0 + sw * PAN_MARGIN);
        double my = Math.max(0, (th - sh) / 2.0 + sh * PAN_MARGIN);
        double nmx = -mx, nmy = -my;
        if (tw <= sw) { double r = (sw - tw) / 2.0; nmx = -r; mx = r; }
        if (th <= sh) { double r = (sh - th) / 2.0; nmy = -r; my = r; }
        panX = Math.max(nmx, Math.min(mx, panX));
        panY = Math.max(nmy, Math.min(my, panY));
    }

    /** 以鼠标位置为锚点缩放（保持鼠标下的树坐标不变）。 */
    private void zoomAt(double mx, double my, double newZoom) {
        newZoom = clampZ(newZoom);
        double tx = (mx - treeLeft - treeW / 2.0 - panX) / zoom + tcX();
        double ty = (my - treeTop - treeH / 2.0 - panY) / zoom + tcY();
        zoom = newZoom;
        panX = mx - treeLeft - treeW / 2.0 - (tx - tcX()) * zoom;
        panY = my - treeTop - treeH / 2.0 - (ty - tcY()) * zoom;
        clampPan();
    }

    // ---------------- 状态判定 ----------------

    private boolean preMet(EvolutionComponent c, EvolutionNode n) {
        if (n.prereqs.isEmpty()) return true;
        for (String p : n.prereqs) if (effUnlocked(c, p)) return true;
        return false;
    }

    /** 前置全部满足（用于 tooltip 与连线判断）。 */
    private boolean preMetAll(EvolutionComponent c, EvolutionNode n) {
        if (n.prereqs.isEmpty()) return true;
        for (String p : n.prereqs) if (!effUnlocked(c, p)) return false;
        return true;
    }

    /** 节点在“已解锁或已加入本次暂存”下视为生效。 */
    private boolean effUnlocked(EvolutionComponent c, String id) {
        return c.isUnlocked(id) || pending.contains(id);
    }

    /** 本次暂存合计消耗点数。 */
    private int pendingCost() {
        int sum = 0;
        for (String id : pending) {
            EvolutionNode n = FamiliarFoxTree.get(id);
            if (n != null) sum += n.cost;
        }
        return sum;
    }

    /** 扣除暂存后的模拟剩余点数。 */
    private int simPoints(EvolutionComponent c) {
        return c.getPoints() - pendingCost();
    }

    /** 是否可加入暂存（走路线、非自动、未解锁且未暂存、前置满足、模拟点数足）。 */
    private boolean canUn(EvolutionComponent c, EvolutionNode n) {
        return c.isOnSscaRoute() && !n.autoUnlock && !c.isUnlocked(n.id) && !pending.contains(n.id)
                && preMet(c, n) && simPoints(c) >= n.cost;
    }

    private boolean inNode(double mx, double my, EvolutionNode n) {
        double cx = ncx(n), cy = ncy(n), h = hn();
        return mx >= cx - h && mx <= cx + h && my >= cy - h && my <= cy + h;
    }

    private boolean inBtn(double mx, double my) {
        return mx >= btnRect[0] && mx <= btnRect[0] + btnRect[2]
                && my >= btnRect[1] && my <= btnRect[1] + btnRect[3];
    }

    private boolean inConfBtn(double mx, double my) {
        return mx >= confBtnRect[0] && mx <= confBtnRect[0] + confBtnRect[2]
                && my >= confBtnRect[1] && my <= confBtnRect[1] + confBtnRect[3];
    }

    private boolean inTreeArea(double mx, double my) {
        return mx >= treeLeft && mx <= treeLeft + treeW && my >= treeTop && my <= treeTop + treeH;
    }

    // ---------------- 渲染主流程 ----------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updateTreeRect();
        updateBtn();

        renderBackground(ctx);

        EvolutionComponent comp = getComp();

        // 进化树区域背景（比全局更深）
        ctx.fill(treeLeft, treeTop, treeLeft + treeW, treeTop + treeH, 0xB0060402);
        drawAmbientGlow(ctx);
        drawPanelBorder(ctx, treeLeft, treeTop, treeW, treeH, GOLD_BOR);

        // 裁剪进化树内容：缩放后连线/节点可能溢出，用 scissors 限制在区域内
        enableScissor(treeLeft + 1, treeTop + 1, treeLeft + treeW - 1, treeTop + treeH - 1);

        // 连线（前置 → 节点），在节点边缘外停止
        drawConnections(ctx, comp);

        // 节点
        EvolutionNode hovered = null;
        if (comp != null) {
            for (EvolutionNode node : FamiliarFoxTree.nodes()) {
                boolean unlocked = comp.isUnlocked(node.id);
                boolean staged = pending.contains(node.id);
                boolean canUn = canUn(comp, node);
                drawNode(ctx, node, unlocked, staged, canUn);
                if (inNode(mouseX, mouseY, node)) {
                    hovered = node;
                }
            }
        }

        disableScissor();

        // 顶部 / 底部信息面板
        drawTopBar(ctx, comp);
        drawBottomBar(ctx, comp);

        // 返回按钮 + 确认加点按钮（水平并排、整体居中）
        btnHovered = inBtn(mouseX, mouseY);
        drawBackButton(ctx, btnHovered);
        confHovered = inConfBtn(mouseX, mouseY);
        drawConfirmButton(ctx, confHovered, !pending.isEmpty());

        // 操作提示（树区域左下角）
        drawHints(ctx);

        // tooltip
        if (hovered != null && comp != null) {
            drawNodeTooltip(ctx, hovered, mouseX, mouseY, comp);
        }
    }

    /** scissors：逻辑坐标 → framebuffer 物理坐标（左下原点翻转）。 */
    private void enableScissor(int x1, int y1, int x2, int y2) {
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int ph = MinecraftClient.getInstance().getWindow().getHeight();
        int px1 = (int) Math.floor(x1 * gs);
        int px2 = (int) Math.ceil(x2 * gs);
        int pbottom = (int) Math.floor(y1 * gs);
        int ptop = (int) Math.ceil(y2 * gs);
        RenderSystem.enableScissor(px1, ph - ptop, px2, ph - pbottom);
    }

    private void disableScissor() {
        RenderSystem.disableScissor();
    }

    /** 绘制矩形边框（4 条 fill）。 */
    private void drawPanelBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ---------------- 连接线（不伸入节点框内） ----------------

    private void drawConnections(DrawContext ctx, EvolutionComponent comp) {
        if (comp == null) return;
        for (EvolutionNode node : FamiliarFoxTree.nodes()) {
            for (String pid : node.prereqs) {
                EvolutionNode pre = FamiliarFoxTree.get(pid);
                if (pre == null) continue;
                boolean active = comp.isUnlocked(pid);
                boolean staged = !active && pending.contains(pid);
                drawConnection(ctx, pre, node, active, staged);
            }
        }
    }

    /**
     * 绘制前置 pre → node 的折线连接。折线在两端节点边缘外停止（不进入节点框）。
     * 形状：从 pre 右边出发 → 水平到中点 → 垂直到 node 行 → 水平到 node 左边。
     */
    private void drawConnection(DrawContext ctx, EvolutionNode pre, EvolutionNode node, boolean active, boolean staged) {
        double half = hn();
        double px = ncx(pre), py = ncy(pre);
        double nx = ncx(node), ny = ncy(node);
        // 起点贴 pre 右边缘外 1px；终点贴 node 左边缘外 1px
        double sx = px + half + 1;
        double sy = py;
        double ex = nx - half - 1;
        double ey = ny;

        int color = active ? LINE_ACT : (staged ? N_PEND_BOR : LINE_INACT);
        int glow = active ? LINE_GLOW : (staged ? N_PEND_IN : 0);

        if (sy == ey) {
            drawLineH(ctx, sx, ex, sy, color, glow);
            return;
        }
        if (sx > ex) {
            // 同列（极少见）：退化为垂直
            drawLineV(ctx, px, sy, ey, color, glow);
            return;
        }
        double midX = (sx + ex) / 2.0;
        drawLineH(ctx, sx, midX, sy, color, glow);
        drawLineV(ctx, midX, sy, ey, color, glow);
        drawLineH(ctx, midX, ex, ey, color, glow);
    }

    private void drawLineH(DrawContext ctx, double xa, double xb, double y, int color, int glow) {
        int x1 = (int) Math.round(Math.min(xa, xb));
        int x2 = (int) Math.round(Math.max(xa, xb));
        int yi = (int) Math.round(y);
        if (glow != 0) {
            ctx.fill(x1, yi - 2, x2, yi + 3, (glow & 0x00FFFFFF) | 0x30000000);
        }
        ctx.fill(x1, yi - 1, x2, yi + 2, color);
    }

    private void drawLineV(DrawContext ctx, double x, double ya, double yb, int color, int glow) {
        int xi = (int) Math.round(x);
        int y1 = (int) Math.round(Math.min(ya, yb));
        int y2 = (int) Math.round(Math.max(ya, yb));
        if (glow != 0) {
            ctx.fill(xi - 2, y1, xi + 3, y2, (glow & 0x00FFFFFF) | 0x30000000);
        }
        ctx.fill(xi - 1, y1, xi + 2, y2, color);
    }

    // ---------------- 节点 ----------------

    private void drawNode(DrawContext ctx, EvolutionNode node, boolean unlocked, boolean staged, boolean canUn) {
        double cx = ncx(node), cy = ncy(node), half = hn();
        int left = (int) Math.round(cx - half);
        int top = (int) Math.round(cy - half);
        int size = (int) Math.round(sns());
        if (size < 4) return;

        long now = System.currentTimeMillis();
        int pulse = (!unlocked && (canUn || staged)) ? pulseAlpha(now, PULSE_MS, 0x55, 0xCC) : 0;

        if (unlocked) {
            ctx.fill(left, top, left + size, top + size, N_UNLK_FILL);
            ctx.fill(left + 2, top + 2, left + size - 2, top + size - 2, 0x30FFE066);
            drawPanelBorder(ctx, left, top, size, size, N_UNLK_BOR);
            drawPanelBorder(ctx, left + 3, top + 3, size - 6, size - 6, N_UNLK_IN & 0x88FFFFFF);
        } else if (staged) {
            ctx.fill(left, top, left + size, top + size, N_PEND_FILL);
            ctx.fill(left + 2, top + 2, left + size - 2, top + size - 2, 0x40FFB060);
            ctx.fill(left - 2, top - 2, left + size + 2, top + size + 2,
                    (N_PEND_BOR & 0x00FFFFFF) | ((pulse / 2) << 24));
            drawPanelBorder(ctx, left, top, size, size, N_PEND_BOR);
            drawPanelBorder(ctx, left + 3, top + 3, size - 6, size - 6, N_PEND_IN & 0x88FFFFFF);
        } else if (node.autoUnlock) {
            ctx.fill(left, top, left + size, top + size, N_AUTO_FILL);
            int ripple = (int) ((now % (long) RIPPLE_MS) / RIPPLE_MS * size);
            ctx.fill(left, top + ripple, left + size, top + ripple + 1, 0x6000FFFF);
            drawDashedBorder(ctx, left, top, size, size, N_AUTO_BOR);
        } else if (canUn) {
            ctx.fill(left, top, left + size, top + size, 0x30106610);
            ctx.fill(left - 2, top - 2, left + size + 2, top + size + 2,
                    (N_CAN_BOR & 0x00FFFFFF) | ((pulse / 2) << 24));
            drawPanelBorder(ctx, left, top, size, size,
                    (N_CAN_BOR & 0x00FFFFFF) | (pulse << 24));
        } else {
            ctx.fill(left, top, left + size, top + size, N_LK_FILL);
            drawPanelBorder(ctx, left, top, size, size, N_LK_BOR);
            ctx.fill(left + 1, top + 1, left + size - 1, top + size - 1, 0x60000000);
        }

        // 物品图标（按 zoom 缩放绘制）
        double iconScale = Math.max(0.5, Math.min(zoom, 1.5));
        int iconDraw = 16;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - iconDraw * iconScale / 2.0, cy - iconDraw * iconScale / 2.0, 0);
        if (Math.abs(iconScale - 1.0) > 0.01) {
            ctx.getMatrices().scale((float) iconScale, (float) iconScale, 1.0f);
        }
        ctx.drawItem(new ItemStack(node.icon), 0, 0);
        ctx.getMatrices().pop();

        // 节点名称（下方，居中）
        Text name = Text.translatable(node.nameKey);
        int nameColor = unlocked ? 0xFFFFD700 : (staged ? 0xFFFFB060
                : (canUn ? 0xFF99FF99 : (node.autoUnlock ? 0xFF66E0E0 : 0xFF888888)));
        int nameW = this.textRenderer.getWidth(name);
        ctx.drawTextWithShadow(this.textRenderer, name,
                (int) Math.round(cx - nameW / 2.0), top + size + 3, nameColor);
    }

    /** 虚线边框：用间断 fill 模拟。 */
    private void drawDashedBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        dashedH(ctx, x, y, w, color);
        dashedH(ctx, x, y + h - 1, w, color);
        dashedV(ctx, x, y, h, color);
        dashedV(ctx, x + w - 1, y, h, color);
    }

    private void dashedH(DrawContext ctx, int x, int y, int w, int color) {
        int p = x;
        while (p < x + w) {
            int seg = Math.min(DASH, x + w - p);
            ctx.fill(p, y, p + seg, y + 1, color);
            p += DASH + DGAP;
        }
    }

    private void dashedV(DrawContext ctx, int x, int y, int h, int color) {
        int p = y;
        while (p < y + h) {
            int seg = Math.min(DASH, y + h - p);
            ctx.fill(x, p, x + 1, p + seg, color);
            p += DASH + DGAP;
        }
    }

    /** sin 脉冲映射到 [min, max] 的 alpha（0~255）。 */
    private int pulseAlpha(long now, double periodMs, int min, int max) {
        double t = (now % (long) periodMs) / periodMs * Math.PI * 2;
        double v = (Math.sin(t) + 1.0) / 2.0;
        return min + (int) (v * (max - min));
    }

    // ---------------- 顶部 / 底部信息面板 ----------------

    private void drawTopBar(DrawContext ctx, EvolutionComponent comp) {
        ctx.fill(0, 0, this.width, TOP_BAR, PANEL_BG);
        drawPanelBorder(ctx, 0, 0, this.width, TOP_BAR, GOLD_BOR);
        // 左：标题
        ctx.drawTextWithShadow(this.textRenderer, this.title, 12, 8, TITLE_GOLD);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("evolution.my_addon.screen.route_active").formatted(Formatting.DARK_GRAY),
                12, 26, TXT_GRAY);

        if (comp != null) {
            int level = MinecraftClient.getInstance().player != null
                    ? MinecraftClient.getInstance().player.experienceLevel : 0;
            Text levelText = Text.translatable("evolution.my_addon.screen.level", level);
            int lw = this.textRenderer.getWidth(levelText);
            ctx.drawTextWithShadow(this.textRenderer, levelText, this.width - 12 - lw, 8, TXT_GOLD);

            if (comp.isOnSscaRoute()) {
                Text pointsText = Text.translatable("evolution.my_addon.screen.points", simPoints(comp));
                int pw = this.textRenderer.getWidth(pointsText);
                ctx.drawTextWithShadow(this.textRenderer, pointsText, this.width - 12 - pw, 26, TITLE_GOLD);
            } else {
                Text nr = Text.translatable("evolution.my_addon.screen.no_route");
                int nw = this.textRenderer.getWidth(nr);
                ctx.drawTextWithShadow(this.textRenderer, nr, this.width - 12 - nw, 26, TXT_GRAY);
            }
        }
    }

    private void drawBottomBar(DrawContext ctx, EvolutionComponent comp) {
        int y0 = this.height - BOT_BAR;
        ctx.fill(0, y0, this.width, this.height, PANEL_BG);
        drawPanelBorder(ctx, 0, y0, this.width, BOT_BAR, GOLD_BOR);

        if (comp != null && comp.isOnSscaRoute()) {
            int unlocked = 0;
            for (EvolutionNode n : FamiliarFoxTree.nodes()) {
                if (comp.isUnlocked(n.id)) unlocked++;
            }
            int total = FamiliarFoxTree.nodes().size();
            Text prog = Text.translatable("evolution.my_addon.screen.progress", unlocked, total);
            ctx.drawTextWithShadow(this.textRenderer, prog, 12, y0 + 13, TXT_GOLD);

            int level = MinecraftClient.getInstance().player != null
                    ? MinecraftClient.getInstance().player.experienceLevel : 0;
            boolean spiritLord = comp.isUnlocked(FamiliarFoxTree.NODE_BRANCH_SPIRIT_LORD);
            boolean mancia = comp.isUnlocked(FamiliarFoxTree.NODE_BRANCH_MANCIANIMA);
            Text right;
            int rightColor;
            if (spiritLord && mancia) {
                right = Text.translatable("evolution.my_addon.screen.branch_ready");
                rightColor = TITLE_GOLD;
            } else {
                int nextMs = -1;
                for (int m : LEVEL_MILESTONES) {
                    if (level < m) { nextMs = m; break; }
                }
                if (nextMs < 0) {
                    right = Text.translatable("evolution.my_addon.screen.no_milestone");
                    rightColor = TXT_GRAY;
                } else {
                    right = Text.translatable("evolution.my_addon.screen.next_milestone", nextMs);
                    rightColor = TXT_GOLD;
                }
            }
            int rw = this.textRenderer.getWidth(right);
            ctx.drawTextWithShadow(this.textRenderer, right, this.width - 12 - rw, y0 + 13, rightColor);
        }
    }

    /** 树区域左下角操作提示。 */
    private void drawHints(DrawContext ctx) {
        int x = treeLeft + 6;
        int y = treeTop + treeH - 12;
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("evolution.my_addon.screen.hint_pan"), x, y, TXT_GRAY);
        if (!pending.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.translatable("evolution.my_addon.screen.pending", pending.size(), pendingCost()),
                    x, y - 12, N_PEND_BOR);
        }
    }

    // ---------------- 返回按钮 ----------------

    private void drawBackButton(DrawContext ctx, boolean hovered) {
        int x = btnRect[0], y = btnRect[1], w = btnRect[2], h = btnRect[3];
        ctx.fill(x, y, x + w, y + h, hovered ? BBGH : BBG);
        drawPanelBorder(ctx, x, y, w, h, BBOR);
        Text t = Text.translatable("gui.back");
        int tw = this.textRenderer.getWidth(t);
        ctx.drawTextWithShadow(this.textRenderer, t, x + (w - tw) / 2, y + (h - 8) / 2, BTXT);
    }

    /** 确认加点按钮：仅有待确认节点时有效，启用时金绿脉冲发光。 */
    private void drawConfirmButton(DrawContext ctx, boolean hovered, boolean enabled) {
        int x = confBtnRect[0], y = confBtnRect[1], w = confBtnRect[2], h = confBtnRect[3];
        ctx.fill(x, y, x + w, y + h, enabled && hovered ? 0xD01A3A12 : (enabled ? CONF_BG : BBG));
        if (enabled) {
            int pulse = pulseAlpha(System.currentTimeMillis(), PULSE_MS, 0x44, 0xCC);
            ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, (CONF_BOR & 0x00FFFFFF) | (pulse << 24));
        }
        drawPanelBorder(ctx, x, y, w, h, enabled ? CONF_BOR : N_LK_BOR);
        Text t = Text.translatable("evolution.my_addon.screen.confirm");
        int tw = this.textRenderer.getWidth(t);
        ctx.drawTextWithShadow(this.textRenderer, t, x + (w - tw) / 2, y + (h - 8) / 2,
                enabled ? CONF_TXT : CONF_DIS_TXT);
    }

    /** 进化树区域背景横扫流光（动态装饰，不影响布局）。 */
    private void drawAmbientGlow(DrawContext ctx) {
        long now = System.currentTimeMillis();
        double sweep = (now % 5200L) / 5200.0;
        int gx = treeLeft + (int) (sweep * treeW);
        for (int i = 0; i < 4; i++) {
            int a = (int) (0x22 * (1.0 - i / 4.0));
            ctx.fill(gx - i, treeTop, gx + i + 1, treeTop + treeH, (a << 24) | 0x00B8893A);
        }
    }

    // ---------------- Tooltip ----------------

    private void drawNodeTooltip(DrawContext ctx, EvolutionNode node, int mouseX, int mouseY, EvolutionComponent comp) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(node.nameKey).formatted(Formatting.GOLD, Formatting.BOLD));
        lines.add(Text.literal(" "));
        lines.add(Text.translatable(node.descKey).formatted(Formatting.GRAY));
        lines.add(Text.literal(" "));

        if (comp.isUnlocked(node.id)) {
            lines.add(Text.translatable("evolution.my_addon.screen.state.unlocked").formatted(Formatting.GREEN));
        } else if (pending.contains(node.id)) {
            lines.add(Text.translatable("evolution.my_addon.screen.cost", node.cost).formatted(Formatting.YELLOW));
            lines.add(Text.translatable("evolution.my_addon.screen.state.staged").formatted(Formatting.GOLD));
        } else if (node.autoUnlock) {
            lines.add(Text.translatable("evolution.my_addon.screen.state.auto").formatted(Formatting.AQUA));
        } else {
            lines.add(Text.translatable("evolution.my_addon.screen.cost", node.cost).formatted(Formatting.YELLOW));
            if (!comp.isOnSscaRoute()) {
                lines.add(Text.translatable("evolution.my_addon.screen.state.need_route").formatted(Formatting.RED));
            } else if (!preMetAll(comp, node)) {
                List<String> names = new ArrayList<>();
                for (String pid : node.prereqs) {
                    if (!comp.isUnlocked(pid)) {
                        EvolutionNode pn = FamiliarFoxTree.get(pid);
                        if (pn != null) names.add(Text.translatable(pn.nameKey).getString());
                    }
                }
                if (!names.isEmpty()) {
                    lines.add(Text.translatable("evolution.my_addon.screen.prereqs_need", String.join(" / ", names))
                            .formatted(Formatting.RED));
                } else {
                    lines.add(Text.translatable("evolution.my_addon.screen.state.locked").formatted(Formatting.RED));
                }
            } else if (comp.getPoints() < node.cost) {
                lines.add(Text.translatable("evolution.my_addon.screen.state.no_points").formatted(Formatting.RED));
            } else {
                lines.add(Text.translatable("evolution.my_addon.screen.state.click_unlock").formatted(Formatting.GREEN));
            }
        }
        ctx.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    // ---------------- 鼠标 / 滚轮事件 ----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inBtn(mouseX, mouseY)) {
                this.close();
                return true;
            }
            if (!pending.isEmpty() && inConfBtn(mouseX, mouseY)) {
                commitPending();
                return true;
            }
            EvolutionComponent comp = getComp();
            if (comp != null) {
                for (EvolutionNode node : FamiliarFoxTree.nodes()) {
                    if (!inNode(mouseX, mouseY, node)) continue;
                    if (!comp.isOnSscaRoute() && FamiliarFoxTree.NODE_BASE.equals(node.id)) {
                        sendString(SscAddonNetworking.PACKET_EVO_SELECT_ROUTE, FamiliarFoxTree.ROUTE_ID);
                        return true;
                    }
                    // 已暂存：再次点击取消（退还点数）
                    if (pending.contains(node.id)) {
                        pending.remove(node.id);
                        playClick(0.7F);
                        return true;
                    }
                    // 可加点：加入暂存（仅暂存，点击“确认加点”才生效）
                    if (canUn(comp, node)) {
                        pending.add(node.id);
                        playClick(1.0F);
                        return true;
                    }
                }
            }
            if (inTreeArea(mouseX, mouseY)) {
                dragging = true;
                dragStartMX = mouseX;
                dragStartMY = mouseY;
                dragStartPX = panX;
                dragStartPY = panY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            panX = dragStartPX + (mouseX - dragStartMX);
            panY = dragStartPY + (mouseY - dragStartMY);
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (inTreeArea(mouseX, mouseY)) {
            zoomAt(mouseX, mouseY, zoom + amount * ZOOM_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private static void sendString(Identifier packet, String value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(value);
        ClientPlayNetworking.send(packet, buf);
    }

    /** 提交全部待确认节点：一次性批量发送（服务端限频一次、按序逐个解锁），播放获得经验“叮”声，再清空暂存。 */
    private void commitPending() {
        if (pending.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(pending.size());
        for (String id : pending) {
            buf.writeString(id);
        }
        ClientPlayNetworking.send(SscAddonNetworking.PACKET_EVO_UNLOCK_BATCH, buf);
        pending.clear();
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) {
            p.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    private void playClick(float pitch) {
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, pitch);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
