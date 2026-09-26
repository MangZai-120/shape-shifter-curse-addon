package net.jackcooper.shapeShifterCurseAddon.client.keybind;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 「SSCA 特殊键位设置」二级菜单：列出所有有主动技能的 SSCA 形态。
 * 点击某形态进入其专属配置（开关 + 主/副技能自定义键）。
 * <p>
 * 2026-09-26：形态较多时按钮网格会超出屏幕——支持滚轮上下滑：
 * 按钮位置随 scrollY 平移、绘制用 scissor 裁剪到可视区、右缘画滚动条；返回键固定底部。
 */
public class SscAddonKeybindFormListScreen extends Screen {

	private static final int BTN_W = 150;
	private static final int BTN_H = 20;
	private static final int COL_GAP = 10;
	private static final int ROW_GAP = 4;
	private static final int COLS = 2;
	private static final int TOP_Y = 36;
	/** 单次滚轮步进（约 1.5 行），与原版列表手感一致 */
	private static final int SCROLL_STEP = 36;

	private final Screen parent;
	private final List<ButtonWidget> formButtons = new ArrayList<>();
	private final List<Integer> baseYs = new ArrayList<>();
	private ButtonWidget backButton;
	/** 滚动偏移（0 = 顶部；内容不超高时恒 0） */
	private double scrollY;
	private int contentHeight;

	public SscAddonKeybindFormListScreen(Screen parent) {
		super(Text.translatable("text.ssc_addon.keybind.title"));
		this.parent = parent;
	}

	/** 可视区上沿（标题下方） */
	private int viewTop() { return TOP_Y; }

	/** 可视区下沿（返回按钮上方留 8px） */
	private int viewBottom() { return this.height - 34; }

	private int viewHeight() { return Math.max(1, viewBottom() - viewTop()); }

	private double maxScroll() { return Math.max(0, contentHeight - viewHeight()); }

	@Override
	protected void init() {
		formButtons.clear();
		baseYs.clear();

		List<String> formPaths = SscAddonSkillForms.getFormPaths();
		int total = formPaths.size();
		int rows = (total + COLS - 1) / COLS;
		contentHeight = rows * (BTN_H + ROW_GAP);
		int gridW = COLS * BTN_W + (COLS - 1) * COL_GAP;
		int startX = (width - gridW) / 2;

		SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();

		for (int i = 0; i < total; i++) {
			String formPath = formPaths.get(i);
			int col = i % COLS;
			int row = i / COLS;
			int x = startX + col * (BTN_W + COL_GAP);
			int baseY = TOP_Y + row * (BTN_H + ROW_GAP);

			SSCAddonClientConfig.FormKeybind entry = cfg.formKeybinds.get(formPath);
			boolean custom = entry != null && entry.enabled;
			Text status = custom
					? Text.translatable("text.ssc_addon.keybind.status.custom").formatted(Formatting.GREEN)
					: Text.translatable("text.ssc_addon.keybind.status.sync").formatted(Formatting.GRAY);
			Text label = Text.empty()
					.append(SscAddonSkillForms.displayName(formPath))
					.append(" ")
					.append(status);

			ButtonWidget btn = ButtonWidget.builder(label,
							b -> this.client.setScreen(new SscAddonKeybindConfigScreen(this, formPath)))
					.size(BTN_W, BTN_H).position(x, baseY).build();
			formButtons.add(btn);
			baseYs.add(baseY);
			addDrawableChild(btn);
		}

		// 返回键固定底部（不随滚动）
		backButton = addDrawableChild(ButtonWidget.builder(
						Text.translatable("text.ssc_addon.config.close"),
						b -> close())
				.size(200, BTN_H).position((width - 200) / 2, this.height - 26).build());

		clampScroll();
		updatePositions();
	}

	private void clampScroll() {
		if (scrollY < 0) scrollY = 0;
		double max = maxScroll();
		if (scrollY > max) scrollY = max;
	}

	/** 把所有形态按钮平移到当前滚动位置（返回键不动）。 */
	private void updatePositions() {
		for (int i = 0; i < formButtons.size(); i++) {
			formButtons.get(i).setY(baseYs.get(i) - (int) scrollY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (maxScroll() <= 0) return super.mouseScrolled(mouseX, mouseY, amount);
		scrollY -= amount * SCROLL_STEP;
		clampScroll();
		updatePositions();
		return true;
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		// 形态按钮裁剪到可视区绘制（按钮 hitbox 已随滚动平移，半截按钮可点与原版列表一致）
		context.enableScissor(0, viewTop(), width, viewBottom());
		for (ButtonWidget btn : formButtons) {
			btn.render(context, mouseX, mouseY, delta);
		}
		context.disableScissor();
		// 返回键与标题固定不动
		if (backButton != null) backButton.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFF);

		// 滚动条（仅在内容超高时显示）
		double max = maxScroll();
		if (max > 0) {
			int viewH = viewHeight();
			int barX = width - 8;
			context.fill(barX, viewTop(), barX + 4, viewBottom(), 0xFF555555);
			int knobH = Math.max(18, viewH * viewH / contentHeight);
			int knobY = viewTop() + (int) ((viewH - knobH) * (scrollY / max));
			context.fill(barX, knobY, barX + 4, knobY + knobH, 0xFFC8C8C8);
		}
	}
}
