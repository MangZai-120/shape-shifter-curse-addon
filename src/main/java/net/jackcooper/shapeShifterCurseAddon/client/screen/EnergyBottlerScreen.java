package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.screen.EnergyBottlerScreenHandler;

/**
 * 能量装瓶器界面（jackcooper）。
 * <p>纯 fill 自绘占位（不依赖容器贴图）：竖直能量条 + 三行「空瓶输入 → 进度箭头 → 能量瓶输出」+ 手动/自动按钮。
 * 后续替换正式贴图时只需把 {@link #drawBackground} 改为 drawTexture，服务端逻辑不受影响。
 */
@Environment(EnvType.CLIENT)
public class EnergyBottlerScreen extends HandledScreen<EnergyBottlerScreenHandler> {

	/** 背景图资源（176x184：含边框/面板/全部槽位，坐标与槽位一一对应）。 */
	private static final Identifier BACKGROUND = new Identifier("ssc_addon", "textures/gui/container/energy_bottler.png");

	private static final int ENERGY_BAR_X = 8;
	private static final int ENERGY_BAR_Y = 17;
	private static final int ENERGY_BAR_W = 14;
	private static final int ENERGY_BAR_H = 71;

	private ButtonWidget modeButton;
	private ButtonWidget craftButton;

	public EnergyBottlerScreen(EnergyBottlerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 184;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		// 按钮放到输出槽右侧的空白区（x+128 起，避免压住 x108 的输出槽与进度条）
		modeButton = ButtonWidget.builder(Text.empty(), b -> clickButton(EnergyBottlerScreenHandler.BUTTON_TOGGLE_MODE))
				.dimensions(this.x + 128, this.y + 20, 42, 18).build();
		craftButton = ButtonWidget.builder(Text.translatable("gui.ssc_addon.energy_bottler.craft"),
						b -> clickButton(EnergyBottlerScreenHandler.BUTTON_MANUAL_CRAFT))
				.dimensions(this.x + 128, this.y + 44, 42, 18).build();
		this.addDrawableChild(modeButton);
		this.addDrawableChild(craftButton);
	}

	private void clickButton(int id) {
		if (this.client != null && this.client.interactionManager != null) {
			this.client.interactionManager.clickButton(this.handler.syncId, id);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		// 整张背景图（176x184 容器图：含边框/面板/全部槽位），与槽位坐标一一对应
		// 背景图整体往左上偏移 1px（用户校准）；槽位 Y 也已上移 1px（ScreenHandler 17+i*24）
		context.drawTexture(BACKGROUND, x - 1, y - 1, 0, 0, this.backgroundWidth, this.backgroundHeight, 176, 184);
		// 三行进度箭头（输入 → 输出）
		for (int i = 0; i < EnergyBottlerBlockEntity.LINES; i++) {
			int ay = y + 22 + i * 24;
			int ax = x + 70;
			int aw = 36;
			context.fill(ax, ay, ax + aw, ay + 10, 0xFF3B3B3B);
			int prog = this.handler.getProgress(i);
			int filled = prog <= 0 ? 0 : Math.max(1, aw * prog / EnergyBottlerBlockEntity.CRAFT_TIME);
			context.fill(ax, ay, ax + filled, ay + 10, 0xFFFFC24B);
		}
		// 竖直能量条（自底向上填充）
		int bx = x + 8;
		int by = y + 17;
		context.fill(bx - 1, by - 1, bx + 15, by + 72, 0xFF2B2B2B);
		context.fill(bx, by, bx + 14, by + 71, 0xFF101820);
		int cap = Math.max(1, this.handler.getCapacity());
		int energy = Math.min(cap, this.handler.getEnergy());
		int fh = ENERGY_BAR_H * energy / cap;
		if (fh > 0) {
			context.fill(bx, by + ENERGY_BAR_H - fh, bx + ENERGY_BAR_W, by + ENERGY_BAR_H, 0xFF35D6FF);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		boolean auto = this.handler.isAutoMode();
		modeButton.setMessage(Text.translatable(auto
				? "gui.ssc_addon.energy_bottler.mode.auto"
				: "gui.ssc_addon.energy_bottler.mode.manual"));
		craftButton.visible = !auto;
		super.render(context, mouseX, mouseY, delta);
		// 能量条悬停显示数值
		int bx = this.x + ENERGY_BAR_X;
		int by = this.y + ENERGY_BAR_Y;
		if (mouseX >= bx && mouseX < bx + ENERGY_BAR_W && mouseY >= by && mouseY < by + ENERGY_BAR_H) {
			context.drawTooltip(this.textRenderer,
					Text.translatable("gui.ssc_addon.energy_bottler.energy", this.handler.getEnergy(), this.handler.getCapacity()),
					mouseX, mouseY);
		}
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
