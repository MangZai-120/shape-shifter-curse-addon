package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionStorageBoxScreenHandler;

/**
 * 药品存储箱界面（jackcooper）：8 个存储槽（单排）+ 玩家背包，背景为正式 GUI 贴图
 * （textures/gui/container/potion_storage_box.png，176x133，与 ScreenHandler 槽位坐标逐像素对齐）。
 */
@Environment(EnvType.CLIENT)
public class PotionStorageBoxScreen extends HandledScreen<PotionStorageBoxScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/container/potion_storage_box.png");

	public PotionStorageBoxScreen(PotionStorageBoxScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 133;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		// 直接绘制正式 GUI 贴图（必须用 9 参版显式传贴图真实尺寸 176x133；6 参版默认 256x256 会导致 UV 错位）
		context.drawTexture(TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 176, 133);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
