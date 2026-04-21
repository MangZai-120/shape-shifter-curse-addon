package net.onixary.shapeShifterCurseFabric.ssc_addon.compat;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonServerConfig;

import java.util.function.Supplier;

/**
 * 仿照 SSC 原版的 ConfigMenuScreen 设计 —— 在进入具体配置前先选择"客户端 / 服务端"。
 * <p>
 * - 客户端配置：仅修改本机显示效果，与服务器隔离
 * - 服务端配置：仅服务器/单机宿主修改才生效（含玩法平衡数值与白名单总开关）
 */
public class SSCAddonConfigMenuScreen extends Screen {

	private final Screen parent;

	public SSCAddonConfigMenuScreen(Screen parent) {
		super(Text.translatable("text.ssc_addon.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		final int btnW = 240;
		final int btnH = 20;
		final int gap = 10;
		final int totalRows = 3; // 客户端 + 服务端 + 关闭
		final int xPos = (width - btnW) / 2;
		int yPos = (height - btnH * totalRows - gap * (totalRows - 1)) / 2;

		// 客户端配置按钮
		addBtn(xPos, yPos, btnW, btnH,
				Text.translatable("text.autoconfig.ssc_addon_client.title"),
				() -> AutoConfig.getConfigScreen(SSCAddonClientConfig.class, this).get());
		yPos += btnH + gap;

		// 服务端配置按钮
		addBtn(xPos, yPos, btnW, btnH,
				Text.translatable("text.autoconfig.ssc_addon_server.title"),
				() -> AutoConfig.getConfigScreen(SSCAddonServerConfig.class, this).get());
		yPos += btnH + gap;

		// 关闭按钮
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("text.ssc_addon.config.close"),
				button -> close()
		).size(btnW, btnH).position(xPos, yPos).build());
	}

	private void addBtn(int x, int y, int w, int h, Text text, Supplier<Screen> screenSupplier) {
		addDrawableChild(ButtonWidget.builder(text, button ->
				MinecraftClient.getInstance().setScreen(screenSupplier.get())
		).size(w, h).position(x, y).build());
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		// 在标题位置渲染界面标题
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
	}
}
