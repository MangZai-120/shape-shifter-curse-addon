package net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;

import java.util.function.IntConsumer;

/**
 * HSV 全色图拾色控件：
 *  - 左边 SV 方块：横轴 S（0→1），纵轴 V（1→0）
 *  - 右边 H 竖条：色相 0→360
 *  - 不处理 Alpha（Alpha 由调用方在 Screen 别处控制）
 *
 * 仅做"选择→回调 RGB int(无 Alpha)"，外部把 alpha 合并。
 */
public class HsvPickerWidget implements Element, Selectable {
    private final int x;
    private final int y;
    private final int svSize;        // SV 方块边长
    private final int hueBarW;       // H 竖条宽
    private final int gap = 4;
    private final IntConsumer onChange; // 回调 0x00RRGGBB

    private float hue = 0f;          // [0,1)
    private float sat = 1f;          // [0,1]
    private float val = 1f;          // [0,1]

    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean focused = false;

    public HsvPickerWidget(int x, int y, int svSize, int hueBarW, IntConsumer onChange) {
        this.x = x;
        this.y = y;
        this.svSize = svSize;
        this.hueBarW = hueBarW;
        this.onChange = onChange;
    }

    /** 外部 RGB（无 Alpha）→ HSV 同步内部状态（不触发回调）。 */
    public void setFromRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    public int totalWidth()  { return svSize + gap + hueBarW; }
    public int totalHeight() { return svSize; }

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        // SV 方块：先填当前色相的纯色作为底，再两层渐变叠出 S/V
        int baseHue = hsvToRgb(hue, 1f, 1f) | 0xFF000000;
        ctx.fill(x, y, x + svSize, y + svSize, baseHue);
        // 横向白→透明（饱和度）
        ctx.fillGradient(x, y, x + svSize, y + svSize, 0xFFFFFFFF, 0x00FFFFFF);
        // 实际饱和度需要由左到右：左白右透明 → 然而 fillGradient 是上下渐变。退化为分段填色：
        // 重新实现：行模式逐列填色，开销 svSize 次 fill，可接受（svSize ~ 96）
        // 上一行 fillGradient 实际无法实现横向渐变，撤销并改逐列绘制：
        // 这里用另一种思路：水平 gradient → 把方块绘制成 2 层：白色横渐变 + 黑色纵渐变
        // Vanilla DrawContext.fillGradient 只支持纵向；改用 enableScissor + 像素列 fill
        // 为减小开销且不引入像素扫描，使用 2x2 网格采样法绘制：把 SV 网格切成 16x16 网格，每格 fill 当前颜色
        // 真实实现：逐块 sample 颜色填充
        drawSvSampled(ctx);
        // 在 SV 上画一个 + 形指示器
        int curX = x + Math.round(sat * (svSize - 1));
        int curY = y + Math.round((1f - val) * (svSize - 1));
        int crossColor = (val > 0.5f) ? 0xFF000000 : 0xFFFFFFFF;
        ctx.fill(curX - 3, curY,     curX + 4, curY + 1, crossColor);
        ctx.fill(curX,     curY - 3, curX + 1, curY + 4, crossColor);
        // 边框
        drawBorder(ctx, x - 1, y - 1, svSize + 2, svSize + 2, 0xFF000000);

        // 色相竖条
        int hx = x + svSize + gap;
        drawHueBar(ctx, hx, y, hueBarW, svSize);
        int hY = y + Math.round(hue * (svSize - 1));
        ctx.fill(hx - 2, hY,     hx + hueBarW + 2, hY + 1, 0xFF000000);
        ctx.fill(hx - 2, hY + 1, hx + hueBarW + 2, hY + 2, 0xFFFFFFFF);
        drawBorder(ctx, hx - 1, y - 1, hueBarW + 2, svSize + 2, 0xFF000000);
    }

    /** SV 方块用 24x24 采样块绘制，避免逐像素 fill 性能压力。 */
    private void drawSvSampled(DrawContext ctx) {
        final int cells = 24;
        for (int cx = 0; cx < cells; cx++) {
            for (int cy = 0; cy < cells; cy++) {
                float s = (cx + 0.5f) / cells;
                float v = 1f - (cy + 0.5f) / cells;
                int rgb = hsvToRgb(hue, s, v) | 0xFF000000;
                int x0 = x + cx * svSize / cells;
                int y0 = y + cy * svSize / cells;
                int x1 = x + (cx + 1) * svSize / cells;
                int y1 = y + (cy + 1) * svSize / cells;
                ctx.fill(x0, y0, x1, y1, rgb);
            }
        }
    }

    private void drawHueBar(DrawContext ctx, int hx, int hy, int hw, int hh) {
        // 32 段近似
        final int seg = 32;
        for (int i = 0; i < seg; i++) {
            float h = (i + 0.5f) / seg;
            int rgb = hsvToRgb(h, 1f, 1f) | 0xFF000000;
            int y0 = hy + i * hh / seg;
            int y1 = hy + (i + 1) * hh / seg;
            ctx.fill(hx, y0, hx + hw, y1, rgb);
        }
    }

    private void drawBorder(DrawContext ctx, int bx, int by, int bw, int bh, int color) {
        ctx.fill(bx, by, bx + bw, by + 1, color);
        ctx.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        ctx.fill(bx, by, bx + 1, by + bh, color);
        ctx.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (isInSv(mx, my)) {
            draggingSV = true;
            updateSvFromMouse(mx, my);
            return true;
        }
        if (isInHue(mx, my)) {
            draggingHue = true;
            updateHueFromMouse(my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (draggingSV)  { updateSvFromMouse(mx, my); return true; }
        if (draggingHue) { updateHueFromMouse(my);    return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = draggingSV || draggingHue;
        draggingSV = draggingHue = false;
        return any;
    }

    @Override
    public void setFocused(boolean f) { this.focused = f; }
    @Override
    public boolean isFocused() { return focused; }

    @Override
    public SelectionType getType() { return focused ? SelectionType.FOCUSED : SelectionType.NONE; }
    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {}

    private boolean isInSv(double mx, double my) {
        return mx >= x && mx < x + svSize && my >= y && my < y + svSize;
    }

    private boolean isInHue(double mx, double my) {
        int hx = x + svSize + gap;
        return mx >= hx && mx < hx + hueBarW && my >= y && my < y + svSize;
    }

    private void updateSvFromMouse(double mx, double my) {
        float ns = (float) Math.max(0, Math.min(svSize - 1, mx - x)) / (svSize - 1);
        float nv = 1f - (float) Math.max(0, Math.min(svSize - 1, my - y)) / (svSize - 1);
        if (ns != sat || nv != val) {
            sat = ns; val = nv;
            fire();
        }
    }

    private void updateHueFromMouse(double my) {
        float nh = (float) Math.max(0, Math.min(svSize - 1, my - y)) / (svSize - 1);
        if (nh != hue) {
            hue = nh;
            fire();
        }
    }

    private void fire() {
        if (onChange != null) onChange.accept(hsvToRgb(hue, sat, val));
    }

    // ====== HSV/RGB 转换 ======

    /** @return 0x00RRGGBB */
    public static int hsvToRgb(float h, float s, float v) {
        if (s <= 0f) {
            int g = (int) (v * 255f);
            return (g << 16) | (g << 8) | g;
        }
        float hh = (h % 1f) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int R = Math.round(r * 255f);
        int G = Math.round(g * 255f);
        int B = Math.round(b * 255f);
        return (R << 16) | (G << 8) | B;
    }

    /** @return float[3] = {h[0,1), s[0,1], v[0,1]} */
    public static float[] rgbToHsv(int R, int G, int B) {
        float r = R / 255f, g = G / 255f, b = B / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h, s;
        float v = max;
        s = (max == 0f) ? 0f : d / max;
        if (d == 0f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / d) % 6f;
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        h /= 6f;
        if (h < 0f) h += 1f;
        return new float[]{h, s, v};
    }
}
