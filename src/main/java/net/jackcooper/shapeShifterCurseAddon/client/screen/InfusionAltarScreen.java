package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 注魔台界面（jackcooper）。顶部书槽（五角星中心）+ 左右燃料/催化槽 + 五角星法阵角槽
 * + 程序化五角星线条与锁定角遮罩，右侧展示书槽内魔法书的等级 / 法力 / 经验 / 已装法阵加成。
 * <p>满足升级条件（经验够 + 纯晶 + 超核）时，左右两物品框中间显示「升级」按钮，
 * 点击发 C2S 包由服务端重验后扣材料升级（见 {@link SscAddonNetworking#PACKET_INFUSION_ALTAR_UPGRADE}）。</p>
 */
public class InfusionAltarScreen extends HandledScreen<InfusionAltarScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/infusion_altar.png");
	/** 槽位凹槽材质（18×18，与魔法书界面同款）。 */
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");
	/** 锁定角槽遮罩材质（18×18，挂锁样式）。 */
	private static final Identifier SLOT_LOCK = new Identifier("ssc_addon", "textures/gui/spellbook_slot_lock.png");

	/** 升级按钮（仅满足条件时可见，位于左右两物品框中间） */
	private ButtonWidget upgradeButton;

	public InfusionAltarScreen(InfusionAltarScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 212;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		// 右下角，不压五角星连线
		this.upgradeButton = this.addDrawableChild(ButtonWidget.builder(
				Text.translatable("gui.ssc_addon.infusion_altar.upgrade"),
				b -> ClientPlayNetworking.send(SscAddonNetworking.PACKET_INFUSION_ALTAR_UPGRADE, PacketByteBufs.empty()))
				.dimensions(this.x + 119, this.y + 92, 46, 20).build());
		this.upgradeButton.visible = false;
	}

	/** 客户端本地预判升级条件（服务端点击时仍会权威重验，此处仅控制按钮显示）。 */
	private boolean canUpgradeNow() {
		ItemStack book = this.handler.getSlot(0).getStack();
		if (book.isEmpty() || !SpellbookData.canLevelUp(book)) {
			return false;
		}
		if (this.handler.getSlot(2).getStack().getItem() != RegCustomItem.SUPER_MORPHSCALE_CORE) {
			return false;
		}
		return this.handler.getSlot(1).getStack().getItem() == RegCustomItem.MOONDUST_CRYSTAL_SHARD;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;
		ctx.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
		// 五角星连线先画（在槽位凹槽之下，被槽格遮住的线段自然断开）
		drawPentagram(ctx, x, y);
		// 8 个功能槽凹槽（18×18 格子材质，与魔法书界面同款；背景只留纯面板）
		for (int i = 0; i < 8; i++) {
			Slot slot = this.handler.slots.get(i);
			ctx.drawTexture(SLOT_CELL, x + slot.x - 1, y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
		// 锁定角遮罩（书未放入或角位未解锁的角盖挂锁贴图）
		ItemStack book = this.handler.getSlot(0).getStack();
		boolean hasBook = !book.isEmpty() && book.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
		for (int i = 0; i < SpellbookData.MAX_FORMATION_SLOTS; i++) {
			boolean unlocked = hasBook && SpellbookData.isFormationSlotUnlocked(book, i);
			if (!unlocked) {
				int[] pos = InfusionAltarScreenHandler.PENTAGRAM_SLOT_POS[i];
				ctx.drawTexture(SLOT_LOCK, x + pos[0] - 1, y + pos[1] - 1, 0, 0, 18, 18, 18, 18);
			}
		}
	}

	/** 程序化画五角星：以各角槽格子中心（+8,+8）为顶点的一笔画星形。 */
	private void drawPentagram(DrawContext ctx, int x, int y) {
		int[][] p = InfusionAltarScreenHandler.PENTAGRAM_SLOT_POS;
		// 角序：0顶,1左上,2右上,3左下,4右下；连线：顶→右下→左上→右上→左下→回顶
		int[][] order = {p[0], p[4], p[1], p[2], p[3], p[0]};
		int color = 0xFFB8A0FF;
		for (int i = 0; i < order.length - 1; i++) {
			// 角点 = 槽位左上角 + 8 = 格子中心
			drawLine(ctx, x + order[i][0] + 8, y + order[i][1] + 8, x + order[i + 1][0] + 8, y + order[i + 1][1] + 8, color);
		}
	}

	/** 简易整数画线（水平/垂直/斜向步进）。 */
	private void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
		int steps = Math.max(dx, dy);
		if (steps == 0) {
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			ctx.fill(px, py, px + 1, py + 1, color);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		// 标题改纯黑：背景贴图为浅色，浅色文字看不清
		ctx.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x000000, false);
		ItemStack book = this.handler.getSlot(0).getStack();
		if (!book.isEmpty()) {
			int lv = SpellbookData.getLevel(book);
			int mana = SpellbookData.getMana(book);
			int maxMana = SpellbookData.getMaxMana(book);
			int expTen = SpellbookData.getExpTen(book);
			int need = SpellbookData.getExpToNext(book);
			int formationSlots = SpellbookData.getFormationSlotCount(book);
			ctx.drawText(this.textRenderer, Text.literal("Lv " + lv), 8, 57, 0xB8A0FF, false);
			ctx.drawText(this.textRenderer, Text.literal(mana + "/" + maxMana + " MP"), 8, 67, 0x88A0FF, false);
			// 经验显示（1 位小数）：未满级显示升级进度；满级显示精通档进度（法力上限成长）
			String expStr;
			if (need > 0) {
				expStr = String.format(java.util.Locale.ROOT, "EXP %.1f/%.1f", expTen / 10.0, need / 10.0);
			} else {
				int masteryNeed = SpellbookData.getMasteryExpToNextTier(book);
				if (masteryNeed > 0) {
					// 当前档内进度：从 ×10 整数取模折算（避免浮点 % 精度误差）
					float tierProgress = (expTen % SpellbookData.MASTERY_EXP_PER_TIER) / 10.0f;
					expStr = String.format(java.util.Locale.ROOT, "精通%d档 %.1f/%.1f",
								SpellbookData.getMasteryTier(book) + 1,
								tierProgress, masteryNeed / 10.0);
				} else {
					expStr = "精通已满档 +" + SpellbookData.getMasteryManaBonus(book);
				}
			}
			ctx.drawText(this.textRenderer, Text.literal(expStr), 8, 77, 0x9A88CC, false);
			// 已装法阵加成汇总（耗蓝对全魔法同一倍率；对立对逐对展示当前加成）
			float manaMul = FormationData.sumManaCostMultiplier(book);
			ctx.drawText(this.textRenderer, Text.literal(String.format("法阵 %d/%d", countFormations(book), formationSlots)), 8, 87, 0x9A88CC, false);
			ctx.drawText(this.textRenderer, Text.literal(String.format("火×%.2f 冰×%.2f 月×%.2f 诅×%.2f", 
					FormationData.sumDamageMultiplier(book, FormationElement.FIRE),
					FormationData.sumDamageMultiplier(book, FormationElement.ICE),
					FormationData.sumDamageMultiplier(book, FormationElement.LUNAR),
					FormationData.sumDamageMultiplier(book, FormationElement.CURSE))), 8, 97, 0x8090C8, false);
			ctx.drawText(this.textRenderer, Text.literal(String.format("召×%.2f 虚×%.2f 空CD×%.2f 蓝耗×%.2f", 
					FormationData.sumDamageMultiplier(book, FormationElement.SUMMON),
					FormationData.sumDamageMultiplier(book, FormationElement.VOID),
					FormationData.sumCooldownMultiplier(book, FormationElement.SPACE),
					manaMul)), 8, 107, 0x8090C8, false);
		}
		ctx.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
	}

	private int countFormations(ItemStack book) {
		int count = 0;
		for (int i = 0; i < SpellbookData.MAX_FORMATION_SLOTS; i++) {
			if (!SpellbookData.getFormation(book, i).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx);
		// 每帧刷新按钮可见性（槽内物品 / 书 NBT 均会实时同步到客户端）
		if (this.upgradeButton != null) {
			this.upgradeButton.visible = canUpgradeNow();
		}
		super.render(ctx, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
