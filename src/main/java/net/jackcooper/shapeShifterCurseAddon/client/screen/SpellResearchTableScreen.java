package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollWorkshopManager;
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
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpellResearchTableScreen extends HandledScreen<SpellResearchTableScreenHandler> {

	private static final Identifier BACKGROUND = new Identifier("ssc_addon", "textures/gui/spell_research_background.png");
	private static final Identifier WIDGETS = new Identifier("ssc_addon", "textures/gui/spell_research_widgets.png");
	private static final Identifier TABS = new Identifier("minecraft", "textures/gui/container/creative_inventory/tabs.png");
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
	private static final String[] TAB_KEYS = {"tab_scribe", "tab_learn", "tab_workshop"};
	private static final String[] ACTION_KEYS = {"workshop_craft", "workshop_upgrade", "workshop_repair", "workshop_salvage"};

	private final ButtonWidget[] workshopButtons = new ButtonWidget[4];
	private TextFieldWidget searchField;
	private ButtonWidget actionButton;
	private ButtonWidget levelDownButton;
	private ButtonWidget levelUpButton;
	private List<Entry> entries = List.of();
	private int tab;
	private int scroll;
	private int selected = -1;
	private int workshopLevel = 1;
	private int previewOperation;
	private int thumbY = THUMB_Y_MIN;
	private boolean draggingThumb;
	private ItemStack observedOutput = ItemStack.EMPTY;
	private ItemStack salvageTarget = ItemStack.EMPTY;
	private int salvageConfirmTicks;

	private record Entry(String id, String variant, int level, Spell spell) {
		ItemStack stack(int workshopLevel) {
			return spell == null ? FormationData.create(FormationElement.byId(id), level, variant)
					: ScrollData.create(id, workshopLevel);
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
		String query = this.searchField == null ? "" : this.searchField.getText();
		super.init();
		this.y = (this.height - this.backgroundHeight - TAB_HEIGHT) / 2 + TAB_HEIGHT;
		this.draggingThumb = false;
		Item[] tabIcons = {SscAddon.BLANK_FORMATION_PAPER, Items.BOOK, SscAddon.MAGIC_SCROLL};
		for (int index = 0; index < TAB_KEYS.length; index++) {
			final int targetTab = index;
			this.addDrawableChild(new IconButton(this.x + index * 27, this.y - TAB_HEIGHT, 26, 28,
					label(TAB_KEYS[index]), tabIcons[index], index, button -> selectTab(targetTab)));
		}
		this.searchField = this.addDrawableChild(new TextFieldWidget(this.textRenderer,
				this.x + PANEL_X, this.y + 23, PANEL_W, 16, label("search")));
		this.searchField.setMaxLength(64);
		this.searchField.setPlaceholder(label("search"));
		this.searchField.setText(query);
		this.searchField.setChangedListener(value -> {
			this.scroll = 0;
			cancelSalvage();
			refreshEntries();
		});
		this.actionButton = this.addDrawableChild(ButtonWidget.builder(label("scribe"), button -> onFormationAction())
				.dimensions(this.x + DETAIL_X, this.y + 94, DETAIL_W, 20).build());
		this.levelDownButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustLevel(-1))
				.dimensions(this.x + 270, this.y + 36, 16, 14).build());
		this.levelUpButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustLevel(1))
				.dimensions(this.x + 288, this.y + 36, 16, 14).build());
		this.levelDownButton.setTooltip(Tooltip.of(label("level_down")));
		this.levelUpButton.setTooltip(Tooltip.of(label("level_up")));
		Item[] actionIcons = {SscAddon.MAGIC_SCROLL, Items.ANVIL, Items.PHANTOM_MEMBRANE, Items.GRINDSTONE};
		for (int index = 0; index < this.workshopButtons.length; index++) {
			final int operation = index;
			this.workshopButtons[index] = this.addDrawableChild(new IconButton(
					this.x + DETAIL_X + index * 38, this.y + 94, 34, 20,
					label(ACTION_KEYS[index]), actionIcons[index], -1, button -> onWorkshopAction(operation)));
		}
		refreshEntries();
		refreshWidgets();
	}

	private static Text label(String suffix, Object... args) {
		return Text.translatable("gui.ssc_addon.research." + suffix, args);
	}

	private void selectTab(int nextTab) {
		if (this.tab == nextTab) {
			return;
		}
		this.tab = nextTab;
		this.entries = List.of();
		this.selected = -1;
		this.scroll = 0;
		this.draggingThumb = false;
		this.observedOutput = ItemStack.EMPTY;
		cancelSalvage();
		this.searchField.setText("");
		this.searchField.setFocused(false);
		refreshEntries();
		refreshWidgets();
	}

	private void adjustLevel(int delta) {
		this.workshopLevel = Math.max(1, Math.min(ScrollData.MAX_SPELL_LEVEL, this.workshopLevel + delta));
		cancelSalvage();
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
					if (knowledge.hasSpell(spell.getId().getPath())) {
						next.add(new Entry(spell.getId().getPath(), null, 0, spell));
					}
				}
			} else {
				for (FormationElement element : FormationElement.values()) {
					String[] variants = element == FormationElement.UNIVERSAL
							? new String[]{FormationData.VARIANT_REGEN, FormationData.VARIANT_MANA,
									FormationData.VARIANT_EXP, FormationData.VARIANT_RECOVERY}
							: new String[]{null};
					for (String variant : variants) {
						int learned = knowledge.getLearnedLevel(element, variant);
						for (int level = 1; level <= FormationData.MAX_FORMATION_LEVEL; level++) {
							if (this.tab == 0 ? level <= learned : level > learned && knowledge.hasRecorded(element, variant, level)) {
								next.add(new Entry(element.id, variant, level, null));
							}
						}
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
		ItemStack output = this.handler.getSlot(3).getStack();
		if (!ItemStack.areEqual(this.observedOutput, output)) {
			this.observedOutput = output.copy();
			cancelSalvage();
			if (this.tab == 2 && ScrollData.getSpell(output) != null) {
				for (int index = 0; index < this.entries.size(); index++) {
					if (this.entries.get(index).spell == ScrollData.getSpell(output)) {
						this.selected = index;
						this.workshopLevel = Math.min(ScrollData.MAX_SPELL_LEVEL, ScrollData.getLevel(output) + 1);
						this.scroll = Math.min(maxScroll(), index / COLUMNS);
						break;
					}
				}
			}
		}
		if (!this.draggingThumb) {
			syncThumbFromScroll();
		}
	}

	private void refreshWidgets() {
		boolean workshop = this.tab == 2;
		this.actionButton.visible = !workshop;
		this.actionButton.active = selectedEntry() != null;
		this.actionButton.setMessage(label(this.tab == 0 ? "scribe" : "learn"));
		this.levelDownButton.visible = workshop;
		this.levelUpButton.visible = workshop;
		this.levelDownButton.active = this.workshopLevel > 1;
		this.levelUpButton.active = this.workshopLevel < ScrollData.MAX_SPELL_LEVEL;
		for (int operation = 0; operation < this.workshopButtons.length; operation++) {
			ButtonWidget button = this.workshopButtons[operation];
			button.visible = workshop;
			if (workshop) {
				Text problem = workshopProblem(operation);
				button.active = problem == null;
				button.setTooltip(Tooltip.of(workshopTooltip(operation, problem)));
				button.setMessage(operation == 3 && this.salvageConfirmTicks > 0 ? label("salvage_confirm") : label(ACTION_KEYS[operation]));
			}
		}
	}

	private void onFormationAction() {
		refreshEntries();
		Entry entry = selectedEntry();
		if (entry == null || this.tab == 2) {
			return;
		}
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeString(entry.id);
		buffer.writeString(entry.variant == null ? "" : entry.variant);
		buffer.writeVarInt(entry.level);
		ClientPlayNetworking.send(this.tab == 0 ? SscAddonNetworking.PACKET_FORMATION_SCRIBE
				: SscAddonNetworking.PACKET_FORMATION_LEARN, buffer);
	}

	private void onWorkshopAction(int operation) {
		refreshEntries();
		if (this.tab != 2 || workshopProblem(operation) != null) {
			return;
		}
		if (operation == 3) {
			ItemStack output = this.handler.getSlot(3).getStack();
			if (this.salvageConfirmTicks <= 0 || !ItemStack.areEqual(this.salvageTarget, output)) {
				this.salvageTarget = output.copy();
				this.salvageConfirmTicks = 60;
				refreshWidgets();
				return;
			}
		}
		cancelSalvage();
		PacketByteBuf buffer = PacketByteBufs.create();
		if (operation < 2) {
			buffer.writeString(selectedEntry().id);
			buffer.writeVarInt(this.workshopLevel);
		}
		ClientPlayNetworking.send(switch (operation) {
			case 0 -> SscAddonNetworking.PACKET_SCROLL_CRAFT;
			case 1 -> SscAddonNetworking.PACKET_SCROLL_UPGRADE;
			case 2 -> SscAddonNetworking.PACKET_SCROLL_REPAIR;
			default -> SscAddonNetworking.PACKET_SCROLL_SALVAGE;
		}, buffer);
	}

	private Text workshopProblem(int operation) {
		Entry selectedEntry = selectedEntry();
		ItemStack output = this.handler.getSlot(3).getStack();
		Spell spell = operation < 2 ? selectedEntry == null ? null : selectedEntry.spell : ScrollData.getSpell(output);
		if (spell == null || operation >= 2 && !(output.getItem() instanceof MagicScrollItem)) {
			return label(operation < 2 ? "select_spell" : "insert_scroll");
		}
		if (operation == 0) {
			if (this.workshopLevel > ScrollWorkshopManager.CRAFT_MAX_LEVEL) {
				return label("craft_limit", ScrollWorkshopManager.CRAFT_MAX_LEVEL);
			}
			if (!output.isEmpty()) {
				return Text.translatable("message.ssc_addon.research.output_full");
			}
			if (!(this.handler.getSlot(0).getStack().getItem() instanceof BlankFormationPaperItem)) {
				return Text.translatable("message.ssc_addon.research.no_paper");
			}
		} else if (operation == 1) {
			if (!(output.getItem() instanceof MagicScrollItem) || ScrollData.getSpell(output) != spell
					|| this.workshopLevel < 2 || ScrollData.getLevel(output) != this.workshopLevel - 1) {
				return label("upgrade_input", Text.translatable(spell.getNameKey()), Math.max(1, this.workshopLevel - 1));
			}
		} else if (operation == 2) {
			if (ScrollData.getMaxUses(output) <= 0) {
				return Text.translatable("message.ssc_addon.workshop.cannot_repair");
			}
			if (ScrollData.getUses(output) >= ScrollData.getMaxUses(output)) {
				return Text.translatable("message.ssc_addon.workshop.already_full");
			}
		} else {
			return null;
		}
		int inkCount = operation == 2 ? 1 : this.workshopLevel;
		ItemStack ink = this.handler.getSlot(1).getStack();
		FormationElement element = spell.getElement();
		if (!(ink.getItem() instanceof FormationInkItem inkItem) || ink.getCount() < inkCount
				|| inkItem.getType().element != (element == FormationElement.UNIVERSAL ? null : element)) {
			return label("ink_required", elementName(element), inkCount);
		}
		if (operation == 1) {
			int catalystCount = Math.max(0, this.workshopLevel - 2);
			return countCatalysts() < catalystCount ? label("catalyst_required", catalystCount) : null;
		}
		int dustCount = operation == 2 ? 2 : this.workshopLevel;
		ItemStack dust = this.handler.getSlot(2).getStack();
		return !dust.isOf(RegCustomItem.UNTREATED_MOONDUST) || dust.getCount() < dustCount
				? label("dust_required", dustCount) : null;
	}

	private int countCatalysts() {
		ItemStack stack = this.handler.getSlot(SpellResearchTableBlockEntity.SLOT_CATALYST).getStack();
		return stack.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD) ? stack.getCount() : 0;
	}

	private int previewOperation(int mouseX, int mouseY) {
		for (int operation = 0; operation < this.workshopButtons.length; operation++) {
			ButtonWidget button = this.workshopButtons[operation];
			if (button.visible && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
					&& mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
				return operation;
			}
		}
		if (this.salvageConfirmTicks > 0) {
			return 3;
		}
		Entry entry = selectedEntry();
		ItemStack output = this.handler.getSlot(3).getStack();
		if (entry != null && entry.spell != null && output.getItem() instanceof MagicScrollItem
				&& ScrollData.getSpell(output) == entry.spell) {
			return ScrollData.getLevel(output) == ScrollData.MAX_SPELL_LEVEL ? 2 : 1;
		}
		return this.workshopLevel > ScrollWorkshopManager.CRAFT_MAX_LEVEL ? 1 : 0;
	}

	private void drawMaterialShortfalls(DrawContext context) {
		Entry entry = selectedEntry();
		if (this.tab != 2 && entry == null) {
			return;
		}
		if (this.tab == 1) {
			drawSlotShortfall(context, 2, learnDustCost(entry.level),
					this.handler.getSlot(2).getStack().isOf(RegCustomItem.UNTREATED_MOONDUST));
			return;
		}
		int operation = this.tab == 2 ? this.previewOperation : 0;
		ItemStack output = this.handler.getSlot(3).getStack();
		Spell spell = operation < 2 ? entry == null ? null : entry.spell : ScrollData.getSpell(output);
		if (this.tab == 2 && operation > 0) {
			boolean valid = output.getItem() instanceof MagicScrollItem && spell != null
					&& ScrollData.getSpell(output) == spell
					&& (operation != 1 || ScrollData.getLevel(output) == this.workshopLevel - 1);
			drawSlotShortfall(context, 3, 1, valid);
		}
		if (this.tab == 2 && (spell == null || operation == 3)) {
			return;
		}
		FormationElement element = this.tab == 2 ? spell.getElement() : entry.element();
		int level = this.tab == 2 ? this.workshopLevel : entry.level;
		if (operation == 0) {
			drawSlotShortfall(context, 0, 1, this.handler.getSlot(0).getStack().getItem() instanceof BlankFormationPaperItem);
		}
		ItemStack ink = this.handler.getSlot(1).getStack();
		drawSlotShortfall(context, 1, operation == 2 ? 1 : level,
				ink.getItem() instanceof FormationInkItem inkItem
						&& inkItem.getType().element == (element == FormationElement.UNIVERSAL ? null : element));
		if (this.tab == 2) {
			if (operation == 1) {
				drawSlotShortfall(context, SpellResearchTableBlockEntity.SLOT_CATALYST, Math.max(0, level - 2),
						this.handler.getSlot(SpellResearchTableBlockEntity.SLOT_CATALYST).getStack().isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD));
			} else {
				drawSlotShortfall(context, 2, operation == 2 ? 2 : level,
						this.handler.getSlot(2).getStack().isOf(RegCustomItem.UNTREATED_MOONDUST));
			}
		}
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

	private Text workshopTooltip(int operation, Text problem) {
		ItemStack output = this.handler.getSlot(3).getStack();
		Entry entry = selectedEntry();
		Spell spell = operation < 2 ? entry == null ? null : entry.spell : ScrollData.getSpell(output);
		Text heading = label(operation == 3 && this.salvageConfirmTicks > 0 ? "salvage_confirm" : ACTION_KEYS[operation]);
		var tooltip = heading.copy().formatted(Formatting.WHITE);
		if (spell != null) {
			tooltip.append(Text.literal("\n")).append(Text.translatable(spell.getNameKey()).formatted(Formatting.AQUA));
			Text recipe = switch (operation) {
				case 0 -> label("craft_recipe", this.workshopLevel, elementName(spell.getElement()));
				case 1 -> label("upgrade_recipe", this.workshopLevel, elementName(spell.getElement()), Math.max(0, this.workshopLevel - 2));
				case 2 -> label("repair_recipe", elementName(spell.getElement()));
				default -> label("salvage_recipe", (ScrollData.getLevel(output) + 1) / 2);
			};
			tooltip.append(Text.literal("\n")).append(recipe.copy().formatted(Formatting.GRAY));
			if (operation == 3 && spell.getId().getPath().equals("pocket_space")) {
				tooltip.append(Text.literal("\n")).append(Text.translatable("message.ssc_addon.workshop.salvage_pocket_hint").formatted(Formatting.YELLOW));
			}
		}
		if (problem != null) {
			tooltip.append(Text.literal("\n")).append(problem.copy().formatted(Formatting.RED));
		}
		return tooltip;
	}

	private static Text elementName(FormationElement element) {
		return Text.translatable(element == null ? "element.ssc_addon.universal" : element.getNameKey());
	}

	private void cancelSalvage() {
		this.salvageConfirmTicks = 0;
		this.salvageTarget = ItemStack.EMPTY;
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		this.searchField.tick();
		if (this.salvageConfirmTicks > 0 && --this.salvageConfirmTicks == 0) {
			cancelSalvage();
		}
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
				cancelSalvage();
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
		context.drawTexture(BACKGROUND, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 312, 212);
		for (Slot slot : this.handler.slots) {
			context.drawTexture(SLOT_CELL, this.x + slot.x - 1, this.y + slot.y - 1, 0, 0, 18, 18, 18, 18);
		}
		Item[] placeholders = {SscAddon.BLANK_FORMATION_PAPER, SscAddon.FORMATION_INK_NORMAL,
				RegCustomItem.UNTREATED_MOONDUST, SscAddon.MAGIC_SCROLL, RegCustomItem.MOONDUST_CRYSTAL_SHARD};
		for (int index = 0; index < placeholders.length; index++) {
			Slot slot = this.handler.getSlot(index);
			if (!slot.hasStack() && (index != 3 || this.tab == 2)) {
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
					: label(this.tab == 0 ? "scribe_empty" : this.tab == 1 ? "learn_empty" : "workshop_empty");
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
					: entry.spell.getRarity(this.workshopLevel)).color.getColorValue();
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
			drawClipped(context, label("workshop_level", this.workshopLevel), this.x + DETAIL_X, this.y + 39, 114, 0x404040);
		} else if (entry != null) {
			drawClipped(context, label("workshop_level", entry.level), this.x + DETAIL_X, this.y + 39, DETAIL_W, 0x404040);
		}
		if (entry != null) {
			Text detail = this.tab == 2 ? elementName(entry.element()) : this.tab == 1
					? label("learn_cost", learnDustCost(entry.level))
					: label("scribe_cost", elementName(entry.element()), entry.level);
			drawClipped(context, detail, this.x + DETAIL_X, this.y + 51, DETAIL_W, 0x36545A);
		}
		drawClipped(context, label(this.tab == 2 ? ACTION_KEYS[this.previewOperation] : "materials"),
				this.x + DETAIL_X, this.y + 62, 92, 0x555555);
		drawClipped(context, label(this.tab == 2 ? "scroll_slot" : "output_slot"), this.x + 250, this.y + 62, 54, 0x555555);
		context.drawText(this.textRenderer, ">", this.x + 263, this.y + 75, 0x666666, false);
		if (this.salvageConfirmTicks > 0) {
			context.drawBorder(this.x + DETAIL_X + 3 * 38 - 1, this.y + 93, 36, 22, 0xFFFF8844);
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
		drawClipped(context, label(TAB_KEYS[this.tab]), 8, 8, 200, 0x252525);
		Text count = label("entry_count", this.entries.size());
		context.drawText(this.textRenderer, count, this.backgroundWidth - 8 - this.textRenderer.getWidth(count), 8, 0x555555, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
		drawMaterialShortfalls(context);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.previewOperation = previewOperation(mouseX, mouseY);
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
		int hover = hoveredEntryIndex(mouseX, mouseY);
		Entry entry = hover >= 0 ? this.entries.get(hover) : selectedEntry();
		boolean overDetails = mouseX >= this.x + DETAIL_X && mouseX < this.x + DETAIL_X + DETAIL_W
				&& mouseY >= this.y + 23 && mouseY < this.y + 34;
		if (entry != null && (hover >= 0 || overDetails) && this.client != null && this.client.player != null) {
			TooltipContext tooltipContext = this.client.options.advancedItemTooltips ? TooltipContext.ADVANCED : TooltipContext.BASIC;
			List<Text> tooltip = new ArrayList<>(entry.stack(this.workshopLevel).getTooltip(this.client.player, tooltipContext));
			if (this.tab == 0) {
				tooltip.add(label("scribe_cost", elementName(entry.element()), entry.level));
			} else if (this.tab == 1) {
				tooltip.add(label("learn_cost", learnDustCost(entry.level)));
			}
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
		String[] slotLabels = {"paper_slot", "ink_slot", "dust_slot", this.tab == 2 ? "insert_scroll" : "output_slot", "catalyst_slot"};
		for (int index = 0; index < slotLabels.length; index++) {
			Slot slot = this.handler.getSlot(index);
			if (!slot.hasStack() && this.isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
				context.drawTooltip(this.textRenderer, label(slotLabels[index]), mouseX, mouseY);
			}
		}
	}

	public static int learnDustCost(int level) {
		return level * 2;
	}

	private final class IconButton extends ButtonWidget {
		private final ItemStack icon;
		private final int tabIndex;

		private IconButton(int left, int top, int width, int height, Text message, Item icon, int tabIndex, PressAction action) {
			super(left, top, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
			this.icon = new ItemStack(icon);
			this.tabIndex = tabIndex;
			this.setTooltip(Tooltip.of(message));
		}

		@Override
		public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
			if (this.tabIndex >= 0) {
				boolean selectedTab = this.tabIndex == SpellResearchTableScreen.this.tab;
				context.drawTexture(TABS, getX(), getY(), this.tabIndex * 26, selectedTab ? 32 : 0,
						26, selectedTab ? 32 : 28, 256, 256);
				context.drawItem(this.icon, getX() + 5, getY() + 9);
				return;
			}
			int state = !this.active ? 2 : this.isHovered() ? 1 : 0;
			int textureY = this.isFocused() ? 20 : 0;
			context.drawTexture(WIDGETS, getX(), getY(), state * this.width, textureY, this.width, this.height, 128, 128);
			context.drawItem(this.icon, getX() + (this.width - 16) / 2, getY() + (this.height - 16) / 2);
		}
	}
}