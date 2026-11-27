package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.jackcooper.shapeShifterCurseAddon.client.NeoClient;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

/**
 * 月尘魔法书配置界面（jackcooper）。书页与玩家信息侧栏使用独立背景贴图，
 * 玩家施法数据的初始快照由 {@link SpellbookScreenHandler} 从服务端携带。
 */
public class SpellbookScreen extends HandledScreen<SpellbookScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/moon_dust_spellbook.png");
	private static final Identifier PLAYER_INFORMATION = new Identifier("ssc_addon", "textures/gui/spellbook_player_information.png");
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");
	private static final int BOOK_WIDTH=176, SIDE_X=182, SIDE_WIDTH=136;

	private final Text contents;
	private final InfoRow heading = new InfoRow("title", 9, 0x303030);
	private final InfoRow levelRow = new InfoRow("level", 25, 0x303030);
	private final InfoRow experienceRow = new InfoRow("experience", 39, 0x404040);
	private final InfoRow safetyRow = new InfoRow("safe", 68, 0x235A40);
	private final InfoRow dangerRow = new InfoRow("danger", 82, 0x854C1D);
	private final InfoRow criticalRow = new InfoRow("critical", 96, 0x832D2D);
	private final InfoRow currentRow = new InfoRow("current", 110, 0x303030);
	private final InfoRow growthRow = new InfoRow("growth", 128, 0x404040);
	private final InfoRow recoveryRow = new InfoRow("recovery", 142, 0x404040);
	private final InfoRow ownershipRow = new InfoRow("ownership", 154, 0x555555);
	private Language measuredLanguage;
	private TextRenderer measuredRenderer;
	private boolean measuredUnicode;
	private int experience, recoveryDelay, progressWidth;
	private double safety, current;

	public SpellbookScreen(SpellbookScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.contents = Text.translatable("text.ssc_addon.book.contents", handler.bookLevel, handler.slotCount);
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
		context.drawText(this.textRenderer, contents, 8, 20, 0x404040, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
		drawPlayerInformation(context);
	}

	private void drawPlayerInformation(DrawContext context) {
		refreshPlayerInformation();
		heading.draw(context);
		levelRow.draw(context);
		experienceRow.draw(context);
		context.fill(SIDE_X+8,52,SIDE_X+128,58,0xFF373737);
		context.fill(SIDE_X+9,53,SIDE_X+9+progressWidth,57,0xFF609637);
		safetyRow.draw(context);
		dangerRow.draw(context);
		criticalRow.draw(context);
		currentRow.draw(context);
		growthRow.draw(context);
		recoveryRow.draw(context);
		ownershipRow.draw(context);
	}

	private void refreshPlayerInformation() {
		Language language = Language.getInstance(); // Replaced on every client resource reload.
		boolean unicode = client.forcesUnicodeFont();
		boolean reloading = client.getOverlay() != null;
		boolean fontChanged = reloading || measuredLanguage != language || measuredRenderer != textRenderer || measuredUnicode != unicode;
		if (fontChanged) {
			measuredLanguage = reloading ? null : language;
			measuredRenderer = textRenderer;
			measuredUnicode = unicode;
			heading.update();
			growthRow.update();
			ownershipRow.update();
		}

		var live = NeoClient.hud;
		var state = live.getDouble("S") > 0 ? live : handler.playerState;
		int nextExperience = Math.max(0, state.getInt("MasteryExpTen"));
		if (fontChanged || nextExperience != experience) {
			experience = nextExperience;
			int level = PlayerSpellMastery.tier(experience);
			int progress = PlayerSpellMastery.progressTen(experience);
			int required = PlayerSpellMastery.thresholdTen(level);
			levelRow.update(level);
			experienceRow.update(number(progress / 10.0), number(required / 10.0));
			progressWidth = (int) (118.0 * progress / required);
		}
		double nextSafety = Math.max(0, state.getDouble("S"));
		if (fontChanged || Double.compare(nextSafety, safety) != 0) {
			safety = nextSafety;
			String safe = number(safety);
			String critical = number(safety * 2);
			safetyRow.update(safe);
			dangerRow.update(safe, critical);
			criticalRow.update(critical);
		}
		double nextCurrent = state.getDouble("C");
		if (fontChanged || Double.compare(nextCurrent, current) != 0) {
			current = nextCurrent;
			currentRow.update(number(current));
		}
		int nextRecoveryDelay = state.contains("RecoveryDelay") ? state.getInt("RecoveryDelay") : 120;
		if (fontChanged || nextRecoveryDelay != recoveryDelay) {
			recoveryDelay = nextRecoveryDelay;
			recoveryRow.update(number(recoveryDelay / 20.0));
		}
	}
	private static String number(double value){
		if(value>=1e6)return String.format(java.util.Locale.ROOT,"%.1fm",value/1e6);
		if(value>=1e3)return String.format(java.util.Locale.ROOT,"%.1fk",value/1e3);
		return value==Math.rint(value)?Long.toString((long)value):String.format(java.util.Locale.ROOT,"%.1f",value);
	}
	private final class InfoRow {
		private final String translationKey;
		private final int y, color;
		private Text text;
		private float scale;

		private InfoRow(String key, int y, int color) {
			this.translationKey = "text.ssc_addon.book.player." + key;
			this.y = y;
			this.color = color;
		}

		private void update(Object... arguments) {
			text = Text.translatable(translationKey, arguments);
			scale = Math.min(1, 120f / Math.max(1, textRenderer.getWidth(text)));
		}

		private void draw(DrawContext context) {
			var matrices = context.getMatrices();
			matrices.push();
			matrices.translate(SIDE_X + 8, y, 0);
			matrices.scale(scale, scale, 1);
			context.drawText(textRenderer, text, 0, 0, color, false);
			matrices.pop();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
