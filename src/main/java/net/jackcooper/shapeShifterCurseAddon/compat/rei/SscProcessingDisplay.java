package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.*;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.*;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Process;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.*;

public final class SscProcessingDisplay extends BasicDisplay {
    public static final CategoryIdentifier<SscProcessingDisplay> ID = CategoryIdentifier.of("ssc_addon", "processing");
    private final Process recipe;
    public SscProcessingDisplay(Process recipe) {
        super(List.of(EntryIngredients.ofItemStacks(recipe.inputs())), List.of(EntryIngredients.of(recipe.output())), Optional.of(recipe.id()));
        this.recipe = recipe;
    }
    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return ID; }

    public static final class Category implements DisplayCategory<SscProcessingDisplay> {
        @Override public CategoryIdentifier<SscProcessingDisplay> getCategoryIdentifier() { return ID; }
        @Override public Text getTitle() { return Text.translatable("gui.ssc_addon.category.processing"); }
        @Override public Renderer getIcon() { return EntryStacks.of(RegAddonBlocks.ENERGY_BOTTLER); }
        @Override public int getDisplayHeight() { return 140; }
        @Override public int getDisplayWidth(SscProcessingDisplay display) { return 170; }
        @Override public List<Widget> setupDisplay(SscProcessingDisplay display, Rectangle bounds) {
            var widgets = new ArrayList<Widget>();
            widgets.add(Widgets.createRecipeBase(bounds));
            int x = bounds.x + 6, y = bounds.y + 6;
            widgets.add(Widgets.createSlot(new Point(x, y)).entry(EntryStacks.of(display.recipe.workstation())));
            widgets.add(Widgets.createSlot(new Point(x + 36, y)).entries(display.getInputEntries().get(0)).markInput());
            widgets.add(Widgets.createArrow(new Point(x + 70, y)));
            widgets.add(Widgets.createSlot(new Point(x + 110, y)).entries(display.getOutputEntries().get(0)).markOutput());
            // OrderedText is rendered directly to preserve wrapping for all languages.
            widgets.add(Widgets.createDrawableWidget((context, mouseX, mouseY, delta) -> {
                var font = MinecraftClient.getInstance().textRenderer;
                int lineY = y + 28;
                for (var line : font.wrapLines(display.recipe.description(), 156)) {
                    context.drawText(font, line, x, lineY, 0xFF555555, false); lineY += 10;
                }
            }));
            return widgets;
        }
    }
}
