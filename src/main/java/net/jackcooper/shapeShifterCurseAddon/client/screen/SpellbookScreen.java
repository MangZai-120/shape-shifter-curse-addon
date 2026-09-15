package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 月尘魔法书配置界面（jackcooper）。程序化绘制书页风格背景 + 动态卷轴槽凹槽，
 * 顶部显示等级 / 法力（快照由 {@link SpellbookScreenHandler} 从服务端携带）。
 */
public class SpellbookScreen extends HandledScreen<SpellbookScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/moon_dust_spellbook.png");
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");

	public SpellbookScreen(SpellbookScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;
		context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
		// 动态卷轴槽凹槽（数量随书等级 3/5/7，居中；用 18×18 格子材质，背包槽已在贴图内）
		for (int i = 0; i < this.handler.slotCount; i++) {
			Slot slot = this.handler.slots.get(i);
			context.drawTexture(SLOT_CELL, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// 标题改纯黑：背景贴图为浅色书页，浅色文字看不清
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x000000, false);
		Text info = Text.translatable("screen.ssc_addon.spellbook.info",
				this.handler.bookLevel, this.handler.bookMana, this.handler.bookMaxMana);
		context.drawText(this.textRenderer, info, 8, 20, 0x88A0FF, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0xC8C0B0, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
