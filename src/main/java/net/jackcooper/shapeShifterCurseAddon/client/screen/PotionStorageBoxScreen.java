package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionStorageBoxScreenHandler;

/**
 * 药品存储箱界面（jackcooper）：纯 fill 自绘占位。8 个存储槽（单排）+ 玩家背包。
 * 后续替换正式贴图时只需把 {@link #drawBackground} 改为 drawTexture。
 */
@Environment(EnvType.CLIENT)
public class PotionStorageBoxScreen extends HandledScreen<PotionStorageBoxScreenHandler> {

	public PotionStorageBoxScreen(PotionStorageBoxScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 133;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = this.x;
		int y = this.y;
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
		context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 43, 0xFF8B8B8B);
		for (Slot slot : this.handler.slots) {
			drawSlotBg(context, x + slot.x, y + slot.y);
		}
	}

	private void drawSlotBg(DrawContext ctx, int sx, int sy) {
		ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
		ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
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
