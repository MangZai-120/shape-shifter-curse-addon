package net.jackcooper.shapeShifterCurseAddon.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastFeedback;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 左侧配置法阵，右侧充能与升级；背包和详细属性不占用法阵的操作区域。 */
public class InfusionAltarScreen extends HandledScreen<InfusionAltarScreenHandler> {
	private static final Identifier BACKGROUND = texture("gui/infusion_altar_background");
	private static final Identifier HEXAGRAM = texture("gui/infusion_altar_hexagram");
	private static final Identifier SLOT_CELL = texture("gui/spellbook_slot");
	private static final Identifier SLOT_LOCK = texture("gui/spellbook_slot_lock");
	// 与 Curios / Trinkets 的 spellbook 槽配置直接共用原图；物品贴图是 3D 模型 UV，不能当槽图标。
	private static final Identifier BOOK_ICON = texture("slot/spellbook");
	private static final int INK = 0x393240, MUTED = 0x615B68, ACCENT = 0x65508A;
	private static final int GOOD = 0x306540, MISSING = 0x8E432E;
	private ButtonWidget upgradeButton;
	private ItemStack observedBook = ItemStack.EMPTY;
	private ItemStack nextBook = ItemStack.EMPTY;

	public InfusionAltarScreen(InfusionAltarScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = InfusionAltarScreenHandler.GUI_WIDTH;
		this.backgroundHeight = InfusionAltarScreenHandler.GUI_HEIGHT;
		this.titleX = 21;
		this.titleY = 8;
		this.playerInventoryTitleX = InfusionAltarScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = 137;
	}

	private static Identifier texture(String path) {
		return new Identifier("ssc_addon", "textures/" + path + ".png");
	}

	private static Text label(String key, Object... args) {
		return Text.translatable("gui.ssc_addon.infusion_altar." + key, args);
	}

	@Override
	protected void init() {
		super.init();
		this.upgradeButton = this.addDrawableChild(ButtonWidget.builder(label("upgrade"), button -> {
			if (canUpgradeNow()) {
				ClientPlayNetworking.send(SscAddonNetworking.PACKET_INFUSION_ALTAR_UPGRADE, PacketByteBufs.empty());
			}
		}).dimensions(this.x + 194, this.y + 180, 110, 20).build());
		refreshControls();
	}

	private ItemStack book() {
		return this.handler.getSlot(0).getStack();
	}

	private boolean hasBook() {
		return !book().isEmpty() && book().getItem() instanceof MoonDustSpellbookItem;
	}

	private boolean hasCrystal() {
		return this.handler.getSlot(1).getStack().isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD);
	}

	private boolean hasCore() {
		return this.handler.getSlot(2).getStack().isOf(RegCustomItem.SUPER_MORPHSCALE_CORE);
	}

	/** 此处只控制按钮；升级扣费仍由现有 C2S 接收器在服务端重验。 */
	private boolean canUpgradeNow() {
		return hasBook() && SpellbookData.canLevelUp(book()) && hasCrystal() && hasCore();
	}

	private void refreshControls() {
		ItemStack book = book();
		this.upgradeButton.active = canUpgradeNow();
		this.upgradeButton.setMessage(!hasBook() ? label("upgrade")
				: SpellbookData.getLevel(book) == SpellbookData.MAX_LEVEL ? label("max_level")
				: label("upgrade_to", SpellbookData.getLevel(book) + 1));
		if (!ItemStack.areEqual(this.observedBook, book)) {
			this.observedBook = book.copy();
			this.nextBook = ItemStack.EMPTY;
			if (hasBook() && SpellbookData.getLevel(book) < SpellbookData.MAX_LEVEL) {
				// 和服务端升级顺序一致，尤其是 Lv2→3 时不能把旧经验算成精通奖励。
				this.nextBook = book.copy();
				SpellbookData.setLevel(this.nextBook, SpellbookData.getLevel(book) + 1);
				SpellbookData.setExpTen(this.nextBook, 0);
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		// 整张背景和正六芒星都来自绘制的 PNG；六芒星等比缩放，不随槽位拉伸或连线。
		RenderSystem.disableBlend();
		ctx.drawTexture(BACKGROUND, this.x, this.y, 0, 0,
				this.backgroundWidth, this.backgroundHeight, this.backgroundWidth, this.backgroundHeight);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		ctx.drawTexture(HEXAGRAM, this.x + 56, this.y + 31, 0, 0, 80, 80, 80, 80);
		for (Slot slot : this.handler.slots) {
			ctx.drawTexture(SLOT_CELL, this.x + slot.x - 1, this.y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
		if (!hasBook()) {
			ctx.drawTexture(BOOK_ICON, this.x + InfusionAltarScreenHandler.BOOK_X,
					this.y + InfusionAltarScreenHandler.BOOK_Y, 0, 0, 16, 16, 16, 16);
		}
		for (int i = 0; i < SpellbookData.MAX_FORMATION_SLOTS; i++) {
			if (!hasBook() || !SpellbookData.isFormationSlotUnlocked(book(), i)) {
				Slot slot = this.handler.getSlot(3 + i);
				ctx.drawTexture(SLOT_LOCK, this.x + slot.x - 1, this.y + slot.y - 1, 0, 0, 18, 18, 18, 18);
			}
		}
		RenderSystem.disableBlend();
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		// 标题右移 13px（一个字位+余量），避免贴住左侧面板边框
		text(ctx, this.title, 21, 8, 160, INK);
		// 书籍状态右移 8px，避开右面板框线
		text(ctx, label("book_status"), 202, 8, 110, INK);
		text(ctx, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 162, INK);
		ItemStack book = book();
		text(ctx, hasBook() ? label("formations", countFormations(), SpellbookData.getFormationSlotCount(book))
				: label("insert_book"), 14, 124, 156, ACCENT);
		text(ctx, hasBook() ? label("level", SpellbookData.getLevel(book), SpellbookData.getSlotCount(book))
				: label("waiting_book"), 198, 29, 100, INK);
		text(ctx, label("mana"), 198, 44, 28, MUTED);
		rightText(ctx, hasBook() ? SpellbookData.getMana(book) + "/" + SpellbookData.getMaxMana(book) : "— / —", 298, 44);
		bar(ctx, 56, hasBook() ? SpellbookData.getMana(book) : 0,
				hasBook() ? SpellbookData.getMaxMana(book) : 1, 0xFF647EA8);
		boolean mastery = hasBook() && SpellbookData.getLevel(book) == SpellbookData.MAX_LEVEL;
		text(ctx, label(mastery ? "mastery" : "experience"), 198, 68, 28, MUTED);
		int progress = 0, total = 1;
		String progressText = "— / —";
		if (hasBook()) {
			if (mastery) {
				total = SpellbookData.masteryExpPerTier();
				progress = SpellbookData.getMasteryExpToNextTier(book) == 0 ? total : SpellbookData.getExpTen(book) % total;
			} else {
				total = SpellbookData.getExpToNext(book);
				progress = SpellbookData.getExpTen(book);
			}
			progressText = mastery && SpellbookData.getMasteryExpToNextTier(book) == 0
					? label("complete").getString() : decimal(progress) + "/" + decimal(total);
		}
		rightText(ctx, progressText, 298, 68);
		bar(ctx, 80, progress, total, 0xFF8A70A4);
		text(ctx, label("materials"), 194, 96, 110, INK);
		// 燃料标签位置不随槽位移动，保持原位
		text(ctx, label("fuel_short"), 194, 132, 60, MUTED);
		// 「核心」与上方催化槽对齐（槽 x=264，与燃料标签 194=槽192+2 同规律）
		text(ctx, label("core_short"), 264, 132, 40, MUTED);
		text(ctx, chargingStatus(), 194, 149, 110, canUpgradeNow() ? GOOD : MUTED);
		if (mastery) {
			text(ctx, label("mastery_automatic"), 194, 165, 110, MUTED);
		} else {
			requirement(ctx, 194, "experience", hasBook() && SpellbookData.canLevelUp(book));
			requirement(ctx, 231, "crystal_short", hasCrystal());
			requirement(ctx, 268, "core_short", hasCore());
		}
		if (!this.nextBook.isEmpty()) {
			text(ctx, label("next_slots", SpellbookData.getSlotCount(this.nextBook),
					SpellbookData.getFormationSlotCount(this.nextBook)), 194, 206, 110, ACCENT);
			text(ctx, label("next_mana", SpellbookData.getMaxMana(this.nextBook)), 194, 218, 110, MUTED);
		} else if (hasBook()) {
			text(ctx, label("mastery_tier", SpellbookData.getMasteryTier(book),
					SpellbookData.masteryMaxBonus() / SpellbookData.masteryManaPerTier()), 194, 206, 110, ACCENT);
			text(ctx, label("mastery_bonus", SpellbookData.getMasteryManaBonus(book)), 194, 218, 110, MUTED);
		} else {
			text(ctx, label("gain_exp"), 194, 206, 110, MUTED);
			text(ctx, label("unlock_hint"), 194, 218, 110, MUTED);
		}
	}

	private void text(DrawContext ctx, Text text, int x, int y, int width, int color) {
		// 其他语言或自定义名称保持在各自面板内；完整解释在悬停提示中。
		if (this.textRenderer.getWidth(text) <= width) {
			ctx.drawText(this.textRenderer, text, x, y, color, false);
		} else {
			String clipped = this.textRenderer.trimToWidth(text.getString(), width - this.textRenderer.getWidth("…"));
			ctx.drawText(this.textRenderer, clipped + "…", x, y, color, false);
		}
	}

	private void rightText(DrawContext ctx, String value, int right, int y) {
		text(ctx, Text.literal(value), right - Math.min(72, this.textRenderer.getWidth(value)), y, 72, INK);
	}

	private void bar(DrawContext ctx, int y, int value, int max, int color) {
		ctx.fill(198, y, 298, y + 5, 0xFFAAA4B1);
		int filled = (int) (98L * Math.max(0, Math.min(value, max)) / Math.max(1, max));
		ctx.fill(199, y + 1, 199 + filled, y + 4, color);
	}

	private void requirement(DrawContext ctx, int x, String key, boolean met) {
		int color = met ? GOOD : MISSING;
		if (!hasBook() || SpellbookData.getLevel(book()) == SpellbookData.MAX_LEVEL) color = MUTED;
		ctx.drawBorder(x, 166, 6, 6, 0xFF000000 | color);
		if (met) ctx.fill(x + 2, 168, x + 4, 170, 0xFF000000 | color);
		text(ctx, label(key), x + 9, 165, 29, color);
	}

	private Text chargingStatus() {
		if (!hasBook()) return label("waiting_book");
		if (canUpgradeNow()) return label("charge_paused");
		if (SpellbookData.getMana(book()) >= SpellbookData.getMaxMana(book())) return label("mana_full");
		ItemStack fuel = this.handler.getSlot(1).getStack();
		if (fuel.isOf(RegCustomItem.UNTREATED_MOONDUST)) return label("charging", 10);
		if (fuel.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD)) return label("charging", 80);
		return label("fuel_needed");
	}

	private int countFormations() {
		int count = 0;
		for (int i = 3; i < InfusionAltarScreenHandler.ALTAR_SLOT_COUNT; i++) {
			if (!this.handler.getSlot(i).getStack().isEmpty()) count++;
		}
		return count;
	}

	private static String decimal(int tenths) {
		return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
	}

	private static String multiplier(float value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private List<Text> formationTooltip() {
		List<Text> lines = new ArrayList<>();
		lines.add(label("formation_details").copy().formatted(Formatting.LIGHT_PURPLE));
		lines.add(label("formation_help"));
		if (!hasBook()) return lines;
		for (FormationElement element : FormationElement.values()) {
			float effect = element == FormationElement.SPACE ? FormationData.sumSpaceRangeMultiplier(book(), element)
					: FormationData.sumDamageMultiplier(book(), element);
			lines.add(label(element == FormationElement.SPACE ? "space_effect" : "element_effect",
					Text.translatable(element.getNameKey()), multiplier(effect),
					multiplier(FormationData.sumCooldownMultiplier(book(), element)),
					multiplier(FormationData.sumManaCostMultiplier(book(), element))));
		}
		int expLevel = FormationData.getBestUniversalVariantLevel(book(), FormationData.VARIANT_EXP);
		lines.add(label("universal_exp", multiplier(expLevel == 0 ? 1f : FormationData.universalExpMultiplier(expLevel))));
		lines.add(label("universal_mana", SpellbookData.getUniversalFormationManaBonus(book())));
		lines.add(label("universal_recovery", multiplier(FormationData.universalRecoveryMultiplier(book()))));
		return lines;
	}

	/** Before/after quotes come from the same formula as the HUD and server payment gate. */
	private boolean drawFormationPreview(DrawContext ctx, int mouseX, int mouseY) {
		if (!hasBook() || this.focusedSlot == null || this.focusedSlot.id < 3
				|| this.focusedSlot.id >= InfusionAltarScreenHandler.ALTAR_SLOT_COUNT) return false;
		ItemStack cursor = this.handler.getCursorStack();
		boolean removal = cursor.isEmpty();
		if (removal && !this.focusedSlot.hasStack()
				|| !removal && !this.focusedSlot.canInsert(cursor)) return false;
		ItemStack before = book();
		ItemStack after = SpellbookData.previewFormation(before, this.focusedSlot.id - 3, cursor);
		List<Text> lines = new ArrayList<>();
		lines.add((removal ? this.focusedSlot.getStack() : cursor).getName());
		lines.add(label(removal ? "preview_remove" : "preview_insert").copy().formatted(Formatting.LIGHT_PURPLE));
		lines.add(label("preview_mana", SpellbookData.getMaxMana(before), SpellbookData.getMaxMana(after)));
		lines.add(label("preview_recovery", FormCastingStyle.naturalRegenPerSecond(before),
				FormCastingStyle.naturalRegenPerSecond(after)));
		lines.add(label("preview_delay", SpellCastFeedback.seconds(FormCastingStyle.naturalRegenDelayTicks())));
		boolean hasSpell = false;
		for (int slot = 0; slot < SpellbookData.getSlotCount(before); slot++) {
			ItemStack scroll = SpellbookData.getScroll(before, slot);
			Spell spell = ScrollData.getSpell(scroll);
			if (spell == null) continue;
			hasSpell = true;
			int level = ScrollData.getCastLevel(scroll);
			int oldCost = SpellNumbers.finalManaCost(spell, before, this.client.player, level, scroll);
			int cost = SpellNumbers.finalManaCost(spell, after, this.client.player, level, scroll);
			Text name = SpellCastFeedback.spellName(spell, scroll);
			if (oldCost < 0 || cost < 0) {
				lines.add(label("preview_cost_unavailable", name).copy().formatted(Formatting.GRAY));
				continue;
			}
			var line = label("preview_spell", name, level, oldCost, cost).copy();
			if (cost > SpellbookData.getMaxMana(after)) {
				line.append(label("preview_capacity", cost - SpellbookData.getMaxMana(after)));
				line.formatted(Formatting.RED);
			} else if (cost > SpellbookData.getMana(after)) {
				line.append(label("preview_missing", cost - SpellbookData.getMana(after)));
				line.formatted(Formatting.GOLD);
			}
			lines.add(line);
		}
		if (!hasSpell) lines.add(label("preview_no_spells"));
		lines.add(label("preview_current_form").copy().formatted(Formatting.GRAY));
		drawWrappedTooltip(ctx, lines, mouseX, mouseY);
		return true;
	}

	private void drawWrappedTooltip(DrawContext ctx, List<Text> lines, int mouseX, int mouseY) {
		List<net.minecraft.text.OrderedText> wrapped = new ArrayList<>();
		int maxWidth = Math.min(270, this.width - 24);
		for (Text line : lines) wrapped.addAll(this.textRenderer.wrapLines(line, maxWidth));
		ctx.drawOrderedTooltip(this.textRenderer, wrapped, mouseX, mouseY);
	}

	private List<Text> upgradeTooltip() {
		List<Text> lines = new ArrayList<>();
		if (!hasBook()) {
			lines.add(label("insert_book"));
			return lines;
		}
		if (SpellbookData.getLevel(book()) == SpellbookData.MAX_LEVEL) {
			lines.add(label("mastery_help", decimal(SpellbookData.masteryExpPerTier()), SpellbookData.masteryManaPerTier()));
			return lines;
		}
		lines.add(label("upgrade_to", SpellbookData.getLevel(book()) + 1));
		lines.add(condition(label("exp_required", decimal(SpellbookData.getExpTen(book())), decimal(SpellbookData.getExpToNext(book()))), SpellbookData.canLevelUp(book())));
		lines.add(condition(label("item_required", RegCustomItem.MOONDUST_CRYSTAL_SHARD.getName()), hasCrystal()));
		lines.add(condition(label("item_required", RegCustomItem.SUPER_MORPHSCALE_CORE.getName()), hasCore()));
		lines.add(label("upgrade_effect"));
		return lines;
	}

	private static Text condition(Text value, boolean met) {
		return Text.literal(met ? "+ " : "- ").append(value).formatted(met ? Formatting.GREEN : Formatting.RED);
	}

	private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= this.x + x && mouseX < this.x + x + width
				&& mouseY >= this.y + y && mouseY < this.y + y + height;
	}

	private void drawHelp(DrawContext ctx, int mouseX, int mouseY) {
		List<Text> lines = null;
		if (inside(mouseX, mouseY, 12, 122, 160, 13)) {
			lines = formationTooltip();
		} else if (inside(mouseX, mouseY, 194, 163, 110, 38) || inside(mouseX, mouseY, 194, 204, 110, 24)) {
			lines = upgradeTooltip();
		} else if (inside(mouseX, mouseY, 194, 144, 110, 15) || inside(mouseX, mouseY, 198, 43, 100, 19)) {
			lines = List.of(chargingStatus(), label("fuel_help"), label("fuel_consumption"), label("fuel_reservation"));
		} else if (inside(mouseX, mouseY, 194, 130, 60, 12)) {
			lines = List.of(label("fuel_help"), label("fuel_consumption"), label("fuel_reservation"));
		} else if (inside(mouseX, mouseY, 264, 130, 38, 12)) {
			lines = List.of(RegCustomItem.SUPER_MORPHSCALE_CORE.getName(), label("core_help"));
		} else if (inside(mouseX, mouseY, 198, 66, 100, 21)) {
			lines = hasBook() && SpellbookData.getLevel(book()) == SpellbookData.MAX_LEVEL
					? List.of(label("mastery_tier", SpellbookData.getMasteryTier(book()), SpellbookData.masteryMaxBonus() / SpellbookData.masteryManaPerTier()),
					label("mastery_help", decimal(SpellbookData.masteryExpPerTier()), SpellbookData.masteryManaPerTier()))
					: List.of(label("experience_help"));
		} else if (this.focusedSlot != null && !this.focusedSlot.hasStack()) {
			int index = this.focusedSlot.id;
			if (index == 0) lines = List.of(label("insert_book"), label("formation_help"));
			else if (index == 1) lines = List.of(label("fuel_help"), label("fuel_consumption"), label("fuel_reservation"));
			else if (index == 2) lines = List.of(RegCustomItem.SUPER_MORPHSCALE_CORE.getName(), label("core_help"));
			else if (index >= 3 && index < InfusionAltarScreenHandler.ALTAR_SLOT_COUNT) {
				int slot = index - 3;
				lines = List.of(!hasBook() ? label("insert_book") : SpellbookData.isFormationSlotUnlocked(book(), slot)
						? label("formation_insert") : label("formation_locked", slot == 0 ? 1 : slot < 3 ? 2 : 3));
			}
		}
		if (lines != null && this.handler.getCursorStack().isEmpty()) {
			drawWrappedTooltip(ctx, lines, mouseX, mouseY);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx);
		refreshControls();
		super.render(ctx, mouseX, mouseY, delta);
		if (!drawFormationPreview(ctx, mouseX, mouseY)) this.drawMouseoverTooltip(ctx, mouseX, mouseY);
		drawHelp(ctx, mouseX, mouseY);
	}
}
