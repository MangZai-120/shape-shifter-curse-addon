package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 注魔台界面（jackcooper）。程序化紫调背景 + 三槽凹槽，右侧展示书槽内魔法书的等级 / 法力 / 经验。
 * <p>满足升级条件（经验够 + 纯晶 + 超核）时，左右两物品框中间显示「升级」按钮，
 * 点击发 C2S 包由服务端重验后扣材料升级（见 {@link SscAddonNetworking#PACKET_INFUSION_ALTAR_UPGRADE}）。</p>
 */
public class InfusionAltarScreen extends HandledScreen<InfusionAltarScreenHandler> {

	private static final Identifier TEXTURE = new Identifier("ssc_addon", "textures/gui/infusion_altar.png");

	/** 升级按钮（仅满足条件时可见，位于左右两物品框中间） */
	private ButtonWidget upgradeButton;

	public InfusionAltarScreen(InfusionAltarScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		// 居中于燃料槽(44-62)与催化槽(116-134)之间：中心 x=89；高度与槽行(52-70)对齐
		this.upgradeButton = this.addDrawableChild(ButtonWidget.builder(
					Text.translatable("gui.ssc_addon.infusion_altar.upgrade"),
				b -> ClientPlayNetworking.send(SscAddonNetworking.PACKET_INFUSION_ALTAR_UPGRADE, PacketByteBufs.empty()))
				.dimensions(this.x + 66, this.y + 52, 46, 20).build());
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
			int exp = SpellbookData.getExp(book);
			int need = SpellbookData.getExpToNext(book);
			// 三行整体上移，EXP 行(36-45)不得与下方催化槽(y=52)重叠
			ctx.drawText(this.textRenderer, Text.literal("Lv " + lv), 102, 12, 0xB8A0FF, false);
			ctx.drawText(this.textRenderer, Text.literal(mana + "/" + maxMana + " MP"), 102, 24, 0x88A0FF, false);
			String expStr = need > 0 ? ("EXP " + exp + "/" + need) : "MAX";
			ctx.drawText(this.textRenderer, Text.literal(expStr), 102, 36, 0x9A88CC, false);
		}
		ctx.drawText(this.textRenderer, this.playerInventoryTitle,
				this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
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
