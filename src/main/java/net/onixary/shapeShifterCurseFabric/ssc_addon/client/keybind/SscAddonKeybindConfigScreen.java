package net.onixary.shapeShifterCurseFabric.ssc_addon.client.keybind;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import org.lwjgl.glfw.GLFW;

/**
 * 单个形态的「特殊键位」配置界面：
 * <ul>
 *   <li>开关：是否对该形态启用自定义键位（关=同步 SSC 的 G 键）；</li>
 *   <li>主技能键 / 副技能键：点击后按任意键/鼠标键绑定，按 ESC 解绑。</li>
 * </ul>
 * 改动即时写入 {@link SSCAddonClientConfig} 并持久化。
 */
public class SscAddonKeybindConfigScreen extends Screen {

	private final Screen parent;
	private final String formPath;
	private SSCAddonClientConfig.FormKeybind entry;

	private ButtonWidget enableBtn;
	private ButtonWidget primaryBtn;
	private ButtonWidget secondaryBtn;

	/** 0=未监听，1=正在绑定主键，2=正在绑定副键 */
	private int listening = 0;

	public SscAddonKeybindConfigScreen(Screen parent, String formPath) {
		super(SscAddonSkillForms.displayName(formPath));
		this.parent = parent;
		this.formPath = formPath;
	}

	@Override
	protected void init() {
		SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();
		entry = cfg.formKeybinds.computeIfAbsent(formPath, k -> new SSCAddonClientConfig.FormKeybind());

		final int w = 220;
		final int h = 20;
		final int gap = 8;
		final int x = (width - w) / 2;
		int y = 50;

		enableBtn = ButtonWidget.builder(Text.empty(), b -> {
			entry.enabled = !entry.enabled;
			save();
			updateLabels();
		}).size(w, h).position(x, y).build();
		addDrawableChild(enableBtn);
		y += h + gap;

		primaryBtn = ButtonWidget.builder(Text.empty(), b -> {
			listening = 1;
			updateLabels();
		}).size(w, h).position(x, y).build();
		addDrawableChild(primaryBtn);
		y += h + gap;

		secondaryBtn = ButtonWidget.builder(Text.empty(), b -> {
			listening = 2;
			updateLabels();
		}).size(w, h).position(x, y).build();
		addDrawableChild(secondaryBtn);
		y += h + gap * 2;

		addDrawableChild(ButtonWidget.builder(
						Text.translatable("text.ssc_addon.config.close"),
						b -> close())
				.size(w, h).position(x, y).build());

		updateLabels();
	}

	private void updateLabels() {
		enableBtn.setMessage(Text.translatable("text.ssc_addon.keybind.enabled")
				.append(": ")
				.append(entry.enabled
						? Text.translatable("text.ssc_addon.keybind.on").formatted(Formatting.GREEN)
						: Text.translatable("text.ssc_addon.keybind.off").formatted(Formatting.RED)));

		primaryBtn.setMessage(Text.translatable("text.ssc_addon.keybind.primary")
				.append(": ")
				.append(listening == 1
						? Text.translatable("text.ssc_addon.keybind.listening").formatted(Formatting.YELLOW)
						: keyName(entry.primaryKey)));

		secondaryBtn.setMessage(Text.translatable("text.ssc_addon.keybind.secondary")
				.append(": ")
				.append(listening == 2
						? Text.translatable("text.ssc_addon.keybind.listening").formatted(Formatting.YELLOW)
						: keyName(entry.secondaryKey)));
	}

	private Text keyName(String translationKey) {
		if (translationKey == null || translationKey.isEmpty() || "key.keyboard.unknown".equals(translationKey)) {
			return Text.translatable("text.ssc_addon.keybind.unbound").formatted(Formatting.DARK_GRAY);
		}
		try {
			return InputUtil.fromTranslationKey(translationKey).getLocalizedText();
		} catch (Throwable t) {
			return Text.translatable("text.ssc_addon.keybind.unbound").formatted(Formatting.DARK_GRAY);
		}
	}

	private void setKey(int which, String translationKey) {
		if (which == 1) {
			entry.primaryKey = translationKey;
		} else if (which == 2) {
			entry.secondaryKey = translationKey;
		}
	}

	private void save() {
		AutoConfig.getConfigHolder(SSCAddonClientConfig.class).save();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (listening != 0) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				setKey(listening, "key.keyboard.unknown"); // ESC = 解绑
			} else {
				setKey(listening, InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey());
			}
			listening = 0;
			save();
			updateLabels();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (listening != 0) {
			setKey(listening, InputUtil.Type.MOUSE.createFromCode(button).getTranslationKey());
			listening = 0;
			save();
			updateLabels();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 22, 0xFFFFFF);
		// 副标题提示
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("text.ssc_addon.keybind.hint").formatted(Formatting.GRAY),
				this.width / 2, 34, 0xAAAAAA);
	}
}
