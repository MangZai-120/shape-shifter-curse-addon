/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 颜色编辑器首次新手教程覆盖层。
 * <p>
 * 以半透明遮罩覆盖在 {@link AdvancedColorScreen} 之上，逐步用高亮框 + 箭头 + 文字介绍各功能区，
 * 提供「下一步」与「跳过」。看完或跳过后由调用方记录"本存档已看过"。
 * <p>
 * 纯客户端 UI，多人环境一致。高亮目标区域使用屏幕相对坐标（随分辨率自适应）由 {@link Supplier} 实时计算，
 * 避免与 AdvancedColorScreen 内部控件实例硬耦合。
 */
public class ColorTutorialOverlay extends Screen {
    /** 目标高亮矩形：[x, y, w, h]，由 supplier 在当前屏幕尺寸下实时计算。 */
    public record TutorialStep(Supplier<int[]> targetRect, String textKey, Runnable clickTargetAction, boolean boxAbove) {
        public TutorialStep(Supplier<int[]> targetRect, String textKey) {
            this(targetRect, textKey, null, false);
        }
        public TutorialStep(Supplier<int[]> targetRect, String textKey, Runnable clickTargetAction) {
            this(targetRect, textKey, clickTargetAction, false);
        }
        public TutorialStep(Supplier<int[]> targetRect, String textKey, boolean boxAbove) {
            this(targetRect, textKey, null, boxAbove);
        }
    }

    private final Screen background;
    private final Runnable onFinish;
    private final List<TutorialStep> steps = new ArrayList<>();
    private int index = 0;

    private ButtonWidget skipButton;

    /**
     * 教程是否激活中（全局）。背景界面（AdvancedColorScreen / PalettePresetsScreen）在 render 时
     * 检查此标志，跳过自身直接绘制的装饰文字——否则那些文字在 GUI 批处理 flush 时会浮到教程遮罩之上。
     */
    public static boolean ACTIVE = false;

    public ColorTutorialOverlay(Screen background, Runnable onFinish, List<TutorialStep> steps) {
        super(Text.translatable("text.ssc_addon.adv_color.tutorial.title"));
        this.background = background;
        this.onFinish = onFinish;
        this.steps.addAll(steps);
        ACTIVE = true;
    }

    @Override
    protected void init() {
        // 不重新 init 背景界面：背景实例已初始化，重复 init 会触发其教程检测造成递归。
        // 仅保留「跳过」按钮（右上角）；步骤推进改为点击鼠标左键。
        int btnW = 70, btnH = 20;
        skipButton = ButtonWidget.builder(Text.translatable("text.ssc_addon.adv_color.tutorial.skip"),
                b -> finish()).size(btnW, btnH).position(this.width - btnW - 10, 10).build();
        addDrawableChild(skipButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让「跳过」按钮处理
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            TutorialStep step = index < steps.size() ? steps.get(index) : null;
            if (step != null && step.clickTargetAction() != null) {
                // 该步需点击高亮区域（如「预设管理」按钮）才触发指定动作
                int[] r = step.targetRect().get();
                if (r != null && mouseX >= r[0] && mouseX <= r[0] + r[2]
                        && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    step.clickTargetAction().run();
                }
                return true;
            }
            advance();
            return true;
        }
        return false;
    }

    private void advance() {
        if (index >= steps.size() - 1) {
            finish();
        } else {
            index++;
        }
    }

    private void finish() {
        ACTIVE = false;
        if (onFinish != null) onFinish.run();
        if (this.client != null) this.client.setScreen(background);
    }

    @Override
    public void removed() {
        super.removed();
        ACTIVE = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先画背景界面（颜色编辑器）并立即 flush，使其完整光栅化到屏幕缓冲（含深度），不转发其交互
        if (background != null) {
            background.render(context, -1, -1, delta);
            context.draw();
        }

        // 整个教程层抬升 z：靠 GUI 深度分层盖住背景的一切（含背景界面的文字，
        // 否则文字层在 flush 时总在 fill 层之后绘制，会穿透到半透明遮罩之上）。
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);

        TutorialStep step = index < steps.size() ? steps.get(index) : null;
        int[] rect = step != null ? step.targetRect().get() : null;

        // 四周半透明遮罩，挖空高亮目标区域
        int dim = 0xAA000000;
        if (rect != null) {
            int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
            context.fill(0, 0, this.width, y, dim);                       // 上
            context.fill(0, y + h, this.width, this.height, dim);         // 下
            context.fill(0, y, x, y + h, dim);                            // 左
            context.fill(x + w, y, this.width, y + h, dim);               // 右
            // 高亮边框
            int border = 0xFF7CF77C;
            context.fill(x - 2, y - 2, x + w + 2, y, border);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, border);
            context.fill(x - 2, y, x, y + h, border);
            context.fill(x + w, y, x + w + 2, y + h, border);
        } else {
            context.fill(0, 0, this.width, this.height, dim);
        }

        // 文字说明框：根据高亮区域位置动态放在上方或下方，避免遮挡目标控件
        if (step != null) {
            Text body = Text.translatable(step.textKey());
            Text hint = (step.clickTargetAction() != null)
                    ? Text.translatable("text.ssc_addon.adv_color.tutorial.click_target")
                    : Text.translatable("text.ssc_addon.adv_color.tutorial.click_continue");
            int boxW = Math.min(340, this.width - 40);
            int boxX = (this.width - boxW) / 2;
            List<net.minecraft.text.OrderedText> lines = this.textRenderer.wrapLines(body, boxW - 12);
            int textTop = 18;          // 顶部留给步骤计数
            int lineH = 10;
            int hintH = 12;            // 框内底部「点击继续」提示行
            int boxH = textTop + lines.size() * lineH + hintH + 4;
            // 文字框默认放在下方；仅指定 boxAbove 的步骤放上方（避免框挡住目标控件，如灰度反转开关）
            boolean boxOnTop = step.boxAbove();
            int boxY = boxOnTop ? 50 : this.height - 36 - boxH;
            // 不透明背景（先纯黑底彻底遮挡下层控件文字，再叠深蓝色）
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF000000);
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF101820);
            context.drawHorizontalLine(boxX, boxX + boxW, boxY, 0xFF7CF77C);
            context.drawHorizontalLine(boxX, boxX + boxW, boxY + boxH, 0xFF7CF77C);
            context.drawVerticalLine(boxX, boxY, boxY + boxH, 0xFF7CF77C);
            context.drawVerticalLine(boxX + boxW, boxY, boxY + boxH, 0xFF7CF77C);
            // 步骤计数
            Text counter = Text.literal((index + 1) + " / " + steps.size());
            context.drawTextWithShadow(this.textRenderer, counter, boxX + 6, boxY + 4, 0xFF7CF77C);
            // 正文逐行绘制
            int ty = boxY + textTop;
            for (net.minecraft.text.OrderedText line : lines) {
                context.drawTextWithShadow(this.textRenderer, line, boxX + 6, ty, 0xFFFFFFFF);
                ty += lineH;
            }
            // 操作提示（框内底部）
            context.drawTextWithShadow(this.textRenderer, hint, boxX + 6, boxY + boxH - hintH, 0xFF7CF77C);

            // 箭头：从文字框靠近目标的一侧出发，直接指向目标（不穿过文字框）
            if (rect != null) {
                int cx = rect[0] + rect[2] / 2;
                int cy = rect[1] + rect[3] / 2;
                int sx = MathHelper.clamp(cx, boxX + 4, boxX + boxW - 4);
                int sy = (cy < boxY) ? boxY : boxY + boxH;   // 目标在框上方→从框顶出发；下方→从框底出发
                drawArrow(context, sx, sy, cx, cy);
            }
        }

        // 教程自身的按钮（跳过）——同在高 z 层内绘制，避免被深度剔除
        super.render(context, mouseX, mouseY, delta);

        // flush 教程层并恢复矩阵
        context.draw();
        context.getMatrices().pop();
    }

    /** 画一条从 (x1,y1) 指向 (x2,y2) 的简易箭头（绿色）。 */
    private void drawArrow(DrawContext context, int x1, int y1, int x2, int y2) {
        int color = 0xFF7CF77C;
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(MathHelper.lerp(t, x1, x2));
            int py = Math.round(MathHelper.lerp(t, y1, y2));
            context.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
        // 箭头头部
        context.fill(x2 - 3, y2 - 3, x2 + 3, y2 + 3, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
