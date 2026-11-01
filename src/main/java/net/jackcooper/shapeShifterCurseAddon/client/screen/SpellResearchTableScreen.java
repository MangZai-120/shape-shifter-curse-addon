package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpellResearchTableScreen extends HandledScreen<SpellResearchTableScreenHandler> {

	private static final Identifier BACKGROUND = new Identifier("ssc_addon", "textures/gui/spell_research_background.png");
	private static final Identifier ANALYSIS_PROGRESS = new Identifier("ssc_addon", "textures/gui/formation_research/analysis_progress.png");
	private static final Identifier SLOT_CELL = new Identifier("ssc_addon", "textures/gui/spellbook_slot.png");
	private static final Identifier LIST_PANEL = new Identifier("ssc_addon", "textures/gui/magic_slot_panel.png");
	private static final Identifier FRAME = new Identifier("ssc_addon", "textures/gui/formation_frame.png");
	private static final Identifier SCROLL_THUMB = new Identifier("ssc_addon", "textures/gui/scroll_thumb.png");
	private static final int TAB_HEIGHT = 28;
	private static final int PANEL_X = 8, PANEL_Y = 42, PANEL_W = 131, PANEL_H = 72;
	private static final int CELL = 22, COLUMNS = 5, VISIBLE_ROWS = 3;
	private static final int TRACK_X = 117, THUMB_W = 8, THUMB_H = 13;
	private static final int THUMB_Y_MIN = 6, THUMB_Y_MAX = 53;
	private static final int DETAIL_X = 152, DETAIL_W = 152;

	private ButtonWidget projectionButton;
	private TextFieldWidget searchField;
	private ButtonWidget levelDownButton;
	private ButtonWidget levelUpButton;
	private ButtonWidget formationButton;
	private List<Entry> entries = List.of();
	private int tab;
	private int scroll;
	private int selected = -1;
	private int referenceLevel = 1;
	private int thumbY = THUMB_Y_MIN;
	private boolean draggingThumb;
	private boolean researchHandoff;

	private record Entry(String id, String variant, int level, Spell spell) {
		ItemStack stack(int referenceLevel) {
			return spell == null ? FormationData.create(FormationElement.byId(id), level, variant)
					: ScrollData.create(id, referenceLevel);
		}

		FormationElement element() {
			return spell == null ? FormationElement.byId(id) : spell.getElement();
		}

		Text name() {
			return spell == null ? stack(1).getName() : Text.translatable(spell.getNameKey());
		}
	}

	public SpellResearchTableScreen(SpellResearchTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = SpellResearchTableScreenHandler.GUI_WIDTH;
		this.backgroundHeight = SpellResearchTableScreenHandler.GUI_HEIGHT;
		this.playerInventoryTitleX = SpellResearchTableScreenHandler.INVENTORY_X;
		this.playerInventoryTitleY = 118;
	}

	@Override
	protected void init() {
		this.researchHandoff = false;
		String query = this.searchField == null ? "" : this.searchField.getText();
		super.init();
		this.y = (this.height - this.backgroundHeight - TAB_HEIGHT) / 2 + TAB_HEIGHT;
		this.handler.setActivePage(this.tab);
		if (this.client.interactionManager != null) this.client.interactionManager.clickButton(this.handler.syncId, this.tab);
		this.draggingThumb = false;
		for (int index = 0; index < ResearchTableTabs.COUNT; index++) {
			final int targetTab = index;
			this.addDrawableChild(ResearchTableTabs.create(this.x, this.y, index, () -> this.tab == targetTab,
					() -> selectTab(targetTab)));
		}
		this.addDrawableChild(ResearchTableTabs.tutorial(this.x,this.y,
                net.jackcooper.shapeShifterCurseAddon.client.tutorial.SpellTutorialClient::replay));
		this.searchField = this.addDrawableChild(new TextFieldWidget(this.textRenderer,
				this.x + PANEL_X, this.y + 23, PANEL_W, 16, label("search")));
		this.searchField.setMaxLength(64);
		this.searchField.setPlaceholder(label("search"));
		this.searchField.setText(query);
		this.searchField.setChangedListener(value -> {
			this.scroll = 0;
				refreshEntries();
		});
		this.levelDownButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustLevel(-1))
				.dimensions(this.x + 270, this.y + 36, 16, 14).build());
		this.levelUpButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustLevel(1))
				.dimensions(this.x + 288, this.y + 36, 16, 14).build());
		this.levelDownButton.setTooltip(Tooltip.of(label("level_down")));
		this.levelUpButton.setTooltip(Tooltip.of(label("level_up")));
		this.formationButton=this.addDrawableChild(ButtonWidget.builder(label("scribe"),button->onFormationAction())
				.dimensions(this.x+DETAIL_X,this.y+94,DETAIL_W,20).build());
        this.projectionButton=this.addDrawableChild(ButtonWidget.builder(label("project_spell"),button->projectSelected())
                .dimensions(this.x+DETAIL_X,this.y+94,DETAIL_W,20).build());
		refreshEntries();
		refreshWidgets();
	}

	public void openResearchTab(int selectedTab) {
		this.client.setScreen(this);
		selectTab(selectedTab);
	}

	@Override
	public void removed() {
		if (!this.researchHandoff) super.removed();
	}

	private static Text label(String suffix, Object... args) {
		return Text.translatable("gui.ssc_addon.research." + suffix, args);
	}

	private void selectTab(int nextTab) {
		if (nextTab == ResearchTableTabs.RESEARCH) {
			this.researchHandoff = true;
			this.client.setScreen(new SlottedFormationScreen(this.handler, this.client.player.getInventory(), this));
			return;
		}
		if (this.tab == nextTab) {
			return;
		}
		this.tab = nextTab;
		this.handler.setActivePage(nextTab);
		if (this.client.interactionManager != null) this.client.interactionManager.clickButton(this.handler.syncId, nextTab);
		this.entries = List.of();
		this.selected = -1;
		this.scroll = 0;
		this.draggingThumb = false;
		this.searchField.setText("");
		this.searchField.setFocused(false);
		refreshEntries();
		refreshWidgets();
	}

	private void adjustLevel(int delta) {
		this.referenceLevel = Math.max(1, Math.min(ScrollData.MAX_SPELL_LEVEL, this.referenceLevel + delta));
		refreshWidgets();
	}

	private Entry selectedEntry() {
		return this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected) : null;
	}

	private void refreshEntries() {
		Entry previous = selectedEntry();
		List<Entry> next = new ArrayList<>();
		if (this.client != null && this.client.player != null) {
			FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(this.client.player);
			if (this.tab == 2) {
				for (Spell spell : SpellRegistry.all()) {
					if (net.jackcooper.shapeShifterCurseAddon.spell.research.SlottedSpellRecipes.get(spell.getId().getPath()) != null && knowledge.hasSpell(spell.getId().getPath())) {
						next.add(new Entry(spell.getId().getPath(), null, 0, spell));
					}
				}
			} else if (this.tab==ResearchTableTabs.SCRIBE||this.tab==ResearchTableTabs.LEARN) {
				for(FormationElement element:FormationElement.values()){
					String[] variants=element==FormationElement.UNIVERSAL?new String[]{FormationData.VARIANT_REGEN,FormationData.VARIANT_MANA,FormationData.VARIANT_EXP,FormationData.VARIANT_RECOVERY}:new String[]{null};
					for(String variant:variants)for(int level=1;level<=FormationData.MAX_FORMATION_LEVEL;level++){
						boolean learned=knowledge.hasLearned(element,variant,level);
						if(this.tab==ResearchTableTabs.SCRIBE?learned:!learned&&knowledge.hasRecorded(element,variant,level))next.add(new Entry(element.id,variant,level,null));
					}
				}
			}
		}
		String query = this.searchField == null ? "" : this.searchField.getText().strip().toLowerCase(Locale.ROOT);
		if (!query.isEmpty()) {
			next.removeIf(entry -> !entry.name().getString().toLowerCase(Locale.ROOT).contains(query)
					&& !entry.id.contains(query) && !elementName(entry.element()).getString().toLowerCase(Locale.ROOT).contains(query));
		}
		this.entries = next;
		this.selected = previous == null ? -1 : this.entries.indexOf(previous);
		this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
		if (!this.draggingThumb) {
			syncThumbFromScroll();
		}
	}

	private void refreshWidgets() {
		boolean workshop = this.tab == 2;
		this.searchField.visible = this.tab!=ResearchTableTabs.ANALYSIS;
		this.formationButton.visible=this.tab==ResearchTableTabs.SCRIBE||this.tab==ResearchTableTabs.LEARN;
		this.formationButton.active=selectedEntry()!=null;
		this.formationButton.setMessage(label(this.tab==ResearchTableTabs.LEARN?"learn":"scribe"));
		this.levelDownButton.visible = workshop;
		this.levelUpButton.visible = workshop;
		this.levelDownButton.active = this.referenceLevel > 1;
		this.levelUpButton.active = this.referenceLevel < ScrollData.MAX_SPELL_LEVEL;
        this.projectionButton.visible=workshop;
        Entry selected=selectedEntry();
        int rank=this.client.player==null?0:FormationKnowledgeComponent.get(this.client.player).research().rank;
        this.projectionButton.active=workshop&&selected!=null&&this.referenceLevel<=rank&&this.referenceLevel<=selected.spell.getMaxLevel();
        this.projectionButton.setTooltip(Tooltip.of(label(this.projectionButton.active?"projection_help":"projection_rank")));

	}

	private void onFormationAction(){
		refreshEntries();Entry entry=selectedEntry();
		if(entry==null||this.tab!=ResearchTableTabs.SCRIBE&&this.tab!=ResearchTableTabs.LEARN)return;
		PacketByteBuf buffer=PacketByteBufs.create();buffer.writeString(entry.id);buffer.writeString(entry.variant==null?"":entry.variant);buffer.writeVarInt(entry.level);
		ClientPlayNetworking.send(this.tab==ResearchTableTabs.LEARN?SscAddonNetworking.PACKET_FORMATION_LEARN:SscAddonNetworking.PACKET_FORMATION_SCRIBE,buffer);
	}

    private void projectSelected(){
        Entry entry=selectedEntry();if(entry==null||!projectionButton.active)return;
        this.researchHandoff=true;
        var screen=new SlottedFormationScreen(this.handler,this.client.player.getInventory(),this);
        screen.projectUnlocked(entry.id,this.referenceLevel);
        this.client.setScreen(screen);
    }

    private void drawMaterialShortfalls(DrawContext context) {
        if(this.tab==ResearchTableTabs.WORKSHOP)return;
        Entry entry=selectedEntry();if(entry==null)return;
        if(this.tab==ResearchTableTabs.LEARN){
            drawSlotShortfall(context,2,learnDustCost(entry.level),this.handler.getSlot(2).getStack().isOf(RegCustomItem.UNTREATED_MOONDUST));return;
        }
        drawSlotShortfall(context,0,1,this.handler.getSlot(0).getStack().getItem() instanceof BlankFormationPaperItem);
        ItemStack ink=this.handler.getSlot(1).getStack();
        drawSlotShortfall(context,1,entry.level,ink.getItem() instanceof FormationInkItem inkItem
                &&inkItem.getType().element==(entry.element()==FormationElement.UNIVERSAL?null:entry.element()));
    }

	private void drawSlotShortfall(DrawContext context, int index, int required, boolean valid) {
		Slot slot = this.handler.getSlot(index);
		ItemStack stack = slot.getStack();
		drawShortfall(context, slot.x, slot.y, Math.max(0, required - (valid ? stack.getCount() : 0)), stack);
	}

	private void drawShortfall(DrawContext context, int left, int top, int missing, ItemStack stack) {
		if (missing <= 0) {
			return;
		}
		String value = Integer.toString(missing);
		int availableWidth = stack.getCount() > 1 ? 17 - this.textRenderer.getWidth(Integer.toString(stack.getCount())) : 16;
		float scale = Math.min(1.0f, (float) Math.max(1, availableWidth) / this.textRenderer.getWidth(value));
		context.getMatrices().push();
		context.getMatrices().translate(left - 1, top + 16 - this.textRenderer.fontHeight * scale, 300);
		context.getMatrices().scale(scale, scale, 1);
		context.fill(0, 0, this.textRenderer.getWidth(value), this.textRenderer.fontHeight, 0xB0000000);
		context.drawText(this.textRenderer, value, 0, 0, 0xFFFF55, false);
		context.getMatrices().pop();
	}

	private static Text elementName(FormationElement element) {
		return Text.translatable(element == null ? "element.ssc_addon.universal" : element.getNameKey());
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		this.searchField.tick();
		refreshEntries();
		refreshWidgets();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.searchField.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
			if (keyCode != GLFW.GLFW_KEY_TAB) {
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.tab == ResearchTableTabs.ANALYSIS) return super.mouseClicked(mouseX, mouseY, button);
		refreshEntries();
		int localX = (int) mouseX - this.x;
		int localY = (int) mouseY - this.y;
		if (button == 0 && maxScroll() > 0 && localX >= PANEL_X + TRACK_X && localX < PANEL_X + TRACK_X + THUMB_W
				&& localY >= PANEL_Y + 4 && localY < PANEL_Y + 68) {
			this.draggingThumb = true;
			this.searchField.setFocused(false);
			updateThumbDrag(mouseY);
			return true;
		}
		if (localX >= PANEL_X && localX < PANEL_X + PANEL_W && localY >= PANEL_Y && localY < PANEL_Y + PANEL_H) {
			if (button == 0) {
				this.selected = hoveredEntryIndex(mouseX, mouseY);
				this.searchField.setFocused(false);
				refreshWidgets();
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.draggingThumb && button == 0) {
			updateThumbDrag(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.draggingThumb && button == 0) {
			this.draggingThumb = false;
			syncThumbFromScroll();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= this.x + PANEL_X && mouseX < this.x + PANEL_X + PANEL_W
				&& mouseY >= this.y + PANEL_Y && mouseY < this.y + PANEL_Y + PANEL_H) {
			this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(amount)));
			syncThumbFromScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	private int maxScroll() {
		return Math.max(0, (this.entries.size() + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
	}

	private void updateThumbDrag(double mouseY) {
		this.thumbY = Math.max(THUMB_Y_MIN, Math.min(THUMB_Y_MAX, (int) mouseY - this.y - PANEL_Y - THUMB_H / 2));
		float progress = (float) (this.thumbY - THUMB_Y_MIN) / (THUMB_Y_MAX - THUMB_Y_MIN);
		this.scroll = Math.round(progress * maxScroll());
	}

	private void syncThumbFromScroll() {
		this.thumbY = THUMB_Y_MIN + (maxScroll() == 0 ? 0 : Math.round((float) this.scroll / maxScroll() * (THUMB_Y_MAX - THUMB_Y_MIN)));
	}

	private int hoveredEntryIndex(double mouseX, double mouseY) {
		int localX = (int) mouseX - this.x - PANEL_X - 4;
		int localY = (int) mouseY - this.y - PANEL_Y - 4;
		if (localX < 0 || localY < 0 || localX >= COLUMNS * CELL || localY >= VISIBLE_ROWS * CELL
				|| localX % CELL >= 20 || localY % CELL >= 20) {
			return -1;
		}
		int index = (this.scroll + localY / CELL) * COLUMNS + localX / CELL;
		return index < this.entries.size() ? index : -1;
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		return mouseX < left || mouseX >= left + this.backgroundWidth
				|| mouseY < top - TAB_HEIGHT || mouseY >= top + this.backgroundHeight;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		context.drawTexture(BACKGROUND, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 312, 212);
		if (this.tab == ResearchTableTabs.ANALYSIS) context.drawTexture(BACKGROUND, this.x + 144, this.y + 20, 138, 20, 4, 96, 312, 212);
		for (Slot slot : this.handler.slots) {
			if (!slot.isEnabled()) continue;
			context.drawTexture(SLOT_CELL, this.x + slot.x - 1, this.y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
		if (this.tab == ResearchTableTabs.ANALYSIS) {
			ItemStack input = this.handler.getSlot(SpellResearchTableBlockEntity.SLOT_ANALYSIS).getStack();
			Text heading = SpellResearchTableBlockEntity.acceptsAnalysisInput(input) ? input.getName() : Text.translatable("research.ssc_addon.slotted.analysis_hint");
			drawClipped(context, heading, this.x + 10, this.y + 29, 290, 0x333333);
			drawClipped(context, Text.translatable("research.ssc_addon.slotted.analysis_cost"), this.x + 10, this.y + 45, 290, 0x555555);
			int ticks = this.handler.getAnalysisTicks();
			drawClipped(context, Text.translatable("research.ssc_addon.analysis.input"), this.x + 24, this.y + 60, 108, 0x555555);
			drawClipped(context, label("paper_slot"), this.x + 138, this.y + 60, 70, 0x555555);
			drawClipped(context, label("output_slot"), this.x + 254, this.y + 60, 50, 0x555555);
			context.drawText(this.textRenderer, "+", this.x + 97, this.y + 76, 0x666666, false);
			context.drawText(this.textRenderer, ">", this.x + 271, this.y + 76, 0x666666, false);
			context.drawTexture(ANALYSIS_PROGRESS, this.x + 186, this.y + 74, 0, 0, 78, 12, 78, 24);
			int filled = Math.min(78, ticks * 78 / SpellResearchTableBlockEntity.ANALYSIS_TICKS);
			if (filled > 0) context.drawTexture(ANALYSIS_PROGRESS, this.x + 186, this.y + 74, 0, 12, filled, 12, 78, 24);
			drawClipped(context, Text.translatable("research.ssc_addon.slotted.analysis_time", ticks / 20, SpellResearchTableBlockEntity.ANALYSIS_TICKS / 20), this.x + 184, this.y + 91, 116, 0x333333);
			var status = SpellResearchTableBlockEntity.analysisStatus(this.handler.getInventory(), this.handler.getAnalysisTicks());
			drawClipped(context, Text.translatable("research.ssc_addon.slotted.analysis_status." + status.name().toLowerCase(Locale.ROOT)), this.x + 10, this.y + 104, 292,
					status == SpellResearchTableBlockEntity.AnalysisStatus.READY ? 0x36545A : 0x885533);
			return;
		}
		Item[] placeholders = {SscAddon.BLANK_FORMATION_PAPER, SscAddon.FORMATION_INK_NORMAL,
				RegCustomItem.UNTREATED_MOONDUST, SscAddon.MAGIC_SCROLL, RegCustomItem.MOONDUST_CRYSTAL_SHARD};
		for (int index = 0; index < placeholders.length; index++) {
			Slot slot = this.handler.getSlot(index);
			if (slot.isEnabled() && !slot.hasStack() && index != 3) {
				context.drawItem(new ItemStack(placeholders[index]), this.x + slot.x, this.y + slot.y);
				context.getMatrices().push();
				context.getMatrices().translate(0, 0, 200);
				context.fill(this.x + slot.x, this.y + slot.y, this.x + slot.x + 16, this.y + slot.y + 16, 0xAA8B8B8B);
				context.getMatrices().pop();
			}
		}
		drawEntries(context, mouseX, mouseY);
		drawDetails(context);
	}

	private void drawEntries(DrawContext context, int mouseX, int mouseY) {
		int panelX = this.x + PANEL_X;
		int panelY = this.y + PANEL_Y;
		context.drawTexture(LIST_PANEL, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
		if (this.entries.isEmpty()) {
			Text message = !this.searchField.getText().isBlank() ? label("search_empty")
					: label(this.tab == ResearchTableTabs.SCRIBE ? "scribe_empty" : this.tab == ResearchTableTabs.LEARN ? "learn_empty" : "workshop_empty");
			List<OrderedText> lines = this.textRenderer.wrapLines(message, 104);
			int lineY = panelY + (PANEL_H - Math.min(lines.size(), 4) * 10) / 2;
			for (OrderedText line : lines.subList(0, Math.min(lines.size(), 4))) {
				context.drawText(this.textRenderer, line, panelX + 58 - this.textRenderer.getWidth(line) / 2, lineY, 0xE0E0E0, true);
				lineY += 10;
			}
			return;
		}
		int hover = hoveredEntryIndex(mouseX, mouseY);
		for (int cellIndex = 0; cellIndex < COLUMNS * VISIBLE_ROWS; cellIndex++) {
			int index = this.scroll * COLUMNS + cellIndex;
			if (index >= this.entries.size()) {
				break;
			}
			Entry entry = this.entries.get(index);
			int cellX = panelX + 4 + cellIndex % COLUMNS * CELL;
			int cellY = panelY + 4 + cellIndex / COLUMNS * CELL;
			context.drawTexture(FRAME, cellX, cellY, 0, 0, 20, 20, 20, 20);
			Identifier icon = entry.spell == null ? null : entry.spell.getIconTexture();
			if (icon != null) {
				context.drawTexture(icon, cellX + 2, cellY + 2, 16, 16, 0, 0, 32, 32, 32, 32);
			} else {
				context.drawItem(entry.stack(1), cellX + 2, cellY + 2);
			}
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			int rarityColor = (entry.spell == null ? FormationData.getRarity(entry.level)
					: entry.spell.getRarity(this.referenceLevel)).color.getColorValue();
			context.drawBorder(cellX + 1, cellY + 1, 18, 18, 0xFF000000 | rarityColor);
			if (entry.spell == null) {
				context.drawText(this.textRenderer, Integer.toString(entry.level), cellX + 13, cellY + 11,
						rarityColor, true);
			}
			if (index == this.selected || index == hover) {
				int border = index == this.selected ? 0xFFFFD46A : 0xFFFFFFFF;
				context.drawBorder(cellX, cellY, 20, 20, border);
			}
			context.getMatrices().pop();
		}
		if (maxScroll() > 0) {
			context.drawTexture(SCROLL_THUMB, panelX + TRACK_X, panelY + this.thumbY, 0, 0, THUMB_W, THUMB_H, THUMB_W, THUMB_H);
		}
	}

	private void drawDetails(DrawContext context) {
		Entry entry = selectedEntry();
		Text heading = entry == null ? label(this.tab == 2 ? "select_spell" : "select_formation") : entry.name();
		drawClipped(context, heading, this.x + DETAIL_X, this.y + 24, DETAIL_W, 0x252525);
		if (this.tab == 2) {
			drawClipped(context, label("workshop_level", this.referenceLevel), this.x + DETAIL_X, this.y + 39, 114, 0x404040);
		} else if (entry != null) {
			drawClipped(context, label("workshop_level", entry.level), this.x + DETAIL_X, this.y + 39, DETAIL_W, 0x404040);
		}
		if (entry != null) {
			Text detail = this.tab == 2 ? elementName(entry.element()) : this.tab == ResearchTableTabs.LEARN
					? label("learn_cost", learnDustCost(entry.level))
					: label("scribe_cost", elementName(entry.element()), entry.level);
			drawClipped(context, detail, this.x + DETAIL_X, this.y + 51, DETAIL_W, 0x36545A);
		}
        if(this.tab==ResearchTableTabs.WORKSHOP){
            var lines=this.textRenderer.wrapLines(label("projection_help"),DETAIL_W);
            int top=this.y+61;for(var line:lines){context.drawText(this.textRenderer,line,this.x+DETAIL_X,top,0x555555,false);top+=10;}
        }else{
            drawClipped(context,label("materials"),this.x+DETAIL_X,this.y+62,92,0x555555);
            drawClipped(context,label("output_slot"),this.x+250,this.y+62,54,0x555555);
            context.drawText(this.textRenderer,">",this.x+263,this.y+75,0x666666,false);
        }

	}

	private void drawClipped(DrawContext context, Text text, int left, int top, int maxWidth, int color) {
		String value = text.getString();
		if (this.textRenderer.getWidth(value) > maxWidth) {
			value = this.textRenderer.trimToWidth(value, maxWidth - this.textRenderer.getWidth("...")) + "...";
		}
		context.drawText(this.textRenderer, value, left, top, color, false);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		drawClipped(context, Text.translatable("research.ssc_addon.slotted.tab." + this.tab), 8, 8, 200, 0x252525);
		if (this.tab != ResearchTableTabs.ANALYSIS) {
			Text count = label("entry_count", this.entries.size());
			context.drawText(this.textRenderer, count, this.backgroundWidth - 8 - this.textRenderer.getWidth(count), 8, 0x555555, false);
		}
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
		if (this.tab != ResearchTableTabs.ANALYSIS) drawMaterialShortfalls(context);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
		if (this.tab == ResearchTableTabs.ANALYSIS) {
			int[] indices = {SpellResearchTableBlockEntity.SLOT_ANALYSIS, SpellResearchTableBlockEntity.SLOT_PAPER, SpellResearchTableBlockEntity.SLOT_OUTPUT};
			String[] labels = {"insert_scroll", "paper_slot", "output_slot"};
			for (int index = 0; index < indices.length; index++) {
				Slot slot = this.handler.getSlot(indices[index]);
				if (!slot.hasStack() && this.isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) context.drawTooltip(this.textRenderer, label(labels[index]), mouseX, mouseY);
			}
			if (this.isPointWithinBounds(10, 104, 292, 10, mouseX, mouseY)) {
				var status = SpellResearchTableBlockEntity.analysisStatus(this.handler.getInventory(), this.handler.getAnalysisTicks());
				context.drawTooltip(this.textRenderer, Text.translatable("research.ssc_addon.slotted.analysis_status." + status.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
			}
			if (this.isPointWithinBounds(10, 45, 290, 10, mouseX, mouseY)) context.drawTooltip(this.textRenderer, Text.translatable("research.ssc_addon.slotted.analysis_cost"), mouseX, mouseY);
			return;
		}
		int hover = hoveredEntryIndex(mouseX, mouseY);
		Entry entry = hover >= 0 ? this.entries.get(hover) : selectedEntry();
		boolean overDetails = mouseX >= this.x + DETAIL_X && mouseX < this.x + DETAIL_X + DETAIL_W
				&& mouseY >= this.y + 23 && mouseY < this.y + 34;
		if (entry != null && (hover >= 0 || overDetails) && this.client != null && this.client.player != null) {
			TooltipContext tooltipContext = this.client.options.advancedItemTooltips ? TooltipContext.ADVANCED : TooltipContext.BASIC;
			List<Text> tooltip = new ArrayList<>(entry.stack(this.referenceLevel).getTooltip(this.client.player, tooltipContext));
			if (this.tab == ResearchTableTabs.SCRIBE) {
				tooltip.add(label("scribe_cost", elementName(entry.element()), entry.level));
			} else if (this.tab == ResearchTableTabs.LEARN) {
				tooltip.add(label("learn_cost", learnDustCost(entry.level)));
			}
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
		String[] slotLabels = {"paper_slot", "ink_slot", "dust_slot", this.tab == 2 ? "insert_scroll" : "output_slot", "catalyst_slot"};
		for (int index = 0; index < slotLabels.length; index++) {
			Slot slot = this.handler.getSlot(index);
			if (slot.isEnabled() && !slot.hasStack() && this.isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
				context.drawTooltip(this.textRenderer, label(slotLabels[index]), mouseX, mouseY);
			}
		}
	}

	public static int learnDustCost(int level) {
		return level * 2;
	}

}
