package net.onixary.shapeShifterCurseFabric.ssc_addon.client.keybind;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;

/**
 * 「SSCA 特殊键位设置」二级菜单：列出所有有主动技能的 SSCA 形态。
 * 点击某形态进入其专属配置（开关 + 主/副技能自定义键）。
 */
public class SscAddonKeybindFormListScreen extends Screen {

	private final Screen parent;

	public SscAddonKeybindFormListScreen(Screen parent) {
		super(Text.translatable("text.ssc_addon.keybind.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int btnW = 150;
		final int btnH = 20;
		final int colGap = 10;
		final int rowGap = 4;
		final int cols = 2;

		java.util.List<String> formPaths = SscAddonSkillForms.getFormPaths();
		int total = formPaths.size();
		int rows = (total + cols - 1) / cols;
		int gridW = cols * btnW + (cols - 1) * colGap;
		int startX = (width - gridW) / 2;
		int startY = 40;

		SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();

		for (int i = 0; i < total; i++) {
			String formPath = formPaths.get(i);
			int col = i % cols;
			int row = i / cols;
			int x = startX + col * (btnW + colGap);
			int y = startY + row * (btnH + rowGap);

			SSCAddonClientConfig.FormKeybind entry = cfg.formKeybinds.get(formPath);
			boolean custom = entry != null && entry.enabled;
			Text status = custom
					? Text.translatable("text.ssc_addon.keybind.status.custom").formatted(Formatting.GREEN)
					: Text.translatable("text.ssc_addon.keybind.status.sync").formatted(Formatting.GRAY);
			Text label = Text.empty()
					.append(SscAddonSkillForms.displayName(formPath))
					.append(" ")
					.append(status);

			addDrawableChild(ButtonWidget.builder(label,
							b -> this.client.setScreen(new SscAddonKeybindConfigScreen(this, formPath)))
					.size(btnW, btnH).position(x, y).build());
		}

		int backY = startY + rows * (btnH + rowGap) + 10;
		addDrawableChild(ButtonWidget.builder(
						Text.translatable("text.ssc_addon.config.close"),
						b -> close())
				.size(200, btnH).position((width - 200) / 2, backY).build());
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
	}
}
