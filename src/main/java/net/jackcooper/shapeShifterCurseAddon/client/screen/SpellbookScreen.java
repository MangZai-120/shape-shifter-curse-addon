package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 月尘魔法书配置界面（jackcooper）。书页与玩家信息侧栏使用独立背景贴图，
 * 玩家施法数据的初始快照由 {@link SpellbookScreenHandler} 从服务端携带。
 */
public class SpellbookScreen extends HandledScreen<SpellbookScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/moon_dust_spellbook.png");
	private static final Identifier PLAYER_INFORMATION = new Identifier("ssc_addon", "textures/gui/spellbook_player_information.png");
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");
	private static final int BOOK_WIDTH=176, SIDE_X=182, SIDE_WIDTH=136;

	public SpellbookScreen(SpellbookScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = SIDE_X + SIDE_WIDTH;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;
		context.drawTexture(TEXTURE, x, y, 0, 0, BOOK_WIDTH, this.backgroundHeight, 256, 256);
		// 独立侧栏贴图沿用主书页的边框像素，按原始尺寸绘制，避免描边被拉伸。
		int sx=x+SIDE_X;
		context.drawTexture(PLAYER_INFORMATION, sx, y, 0, 0, SIDE_WIDTH, this.backgroundHeight,
				SIDE_WIDTH, this.backgroundHeight);
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
        Text info = Text.translatable("text.ssc_addon.book.contents",this.handler.bookLevel,this.handler.slotCount);
		context.drawText(this.textRenderer, info, 8, 20, 0x404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
		drawPlayerInformation(context);
	}

	private void drawPlayerInformation(DrawContext context) {
		var live=net.ssca.neomagic.client.NeoClient.hud;
		var state=live.contains("S")&&live.getDouble("S")>0?live:this.handler.playerState;
		int experience=Math.max(0,state.getInt("MasteryExpTen"));
		int level=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(experience);
		int progress=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.progressTen(experience);
		int required=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.thresholdTen(level);
		double safety=Math.max(0,state.getDouble("S"));
		row(context,9,"title",0x303030);
		row(context,25,"level",0x303030,level);
		row(context,39,"experience",0x404040,number(progress/10.0),number(required/10.0));
		context.fill(SIDE_X+8,52,SIDE_X+128,58,0xFF373737);
		context.fill(SIDE_X+9,53,SIDE_X+9+(int)(118.0*progress/required),57,0xFF609637);
		row(context,68,"safe",0x235A40,number(safety));
		row(context,82,"danger",0x854C1D,number(safety),number(safety*2));
		row(context,96,"critical",0x832D2D,number(safety*2));
		row(context,110,"current",0x303030,number(state.getDouble("C")));
		row(context,128,"growth",0x404040);
		row(context,142,"recovery",0x404040,number((state.contains("RecoveryDelay")?state.getInt("RecoveryDelay"):120)/20.0));
		row(context,154,"ownership",0x555555);
	}
	private static String number(double value){
		if(value>=1e6)return String.format(java.util.Locale.ROOT,"%.1fm",value/1e6);
		if(value>=1e3)return String.format(java.util.Locale.ROOT,"%.1fk",value/1e3);
		return value==Math.rint(value)?Long.toString((long)value):String.format(java.util.Locale.ROOT,"%.1f",value);
	}
	private void row(DrawContext context,int y,String key,int color,Object... arguments){
		Text text=Text.translatable("text.ssc_addon.book.player."+key,arguments);
		float scale=Math.min(1,120f/Math.max(1,textRenderer.getWidth(text)));
		context.getMatrices().push();context.getMatrices().translate(SIDE_X+8,y,0);context.getMatrices().scale(scale,scale,1);
		context.drawText(textRenderer,text,0,0,color,false);context.getMatrices().pop();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
