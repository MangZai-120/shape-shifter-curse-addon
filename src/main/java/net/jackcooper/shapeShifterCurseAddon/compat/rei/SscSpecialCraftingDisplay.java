package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Crafting;
import net.minecraft.recipe.Recipe;
import java.util.*;

public final class SscSpecialCraftingDisplay extends DefaultCraftingDisplay<Recipe<?>> {
    private final Crafting recipe;
    public SscSpecialCraftingDisplay(Crafting recipe) {
        super(recipe.gridFor(recipe.width()).stream().limit(recipe.width() * recipe.height()).map(EntryIngredients::ofItemStacks).toList(),
            List.of(EntryIngredients.of(recipe.output())), Optional.of(recipe.id()), Optional.empty());
        this.recipe = recipe;
    }
    public Crafting recipe() { return recipe; }
    @Override public int getWidth() { return recipe.width(); }
    @Override public int getHeight() { return recipe.height(); }
    @Override public boolean isShapeless() { return recipe.shapeless(); }
}
