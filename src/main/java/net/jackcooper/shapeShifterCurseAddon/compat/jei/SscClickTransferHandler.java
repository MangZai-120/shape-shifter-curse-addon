package net.jackcooper.shapeShifterCurseAddon.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscGridClickTransfer;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.*;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Crafting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import java.util.Optional;

public final class SscClickTransferHandler<T extends ScreenHandler> implements IRecipeTransferHandler<T, Crafting> {
    private final Class<T> container;
    private final IRecipeTransferHandlerHelper helper;
    public SscClickTransferHandler(Class<T> container, IRecipeTransferHandlerHelper helper) {
        this.container = container; this.helper = helper;
    }
    @Override public Class<T> getContainerClass() { return container; }
    @Override public Optional<ScreenHandlerType<T>> getMenuType() { return Optional.empty(); }
    @Override public RecipeType<Crafting> getRecipeType() { return SSCA_JEIPlugin.SPECIAL_CRAFTING; }
    @Override public IRecipeTransferError transferRecipe(T menu, Crafting recipe, IRecipeSlotsView slots,
                                                        PlayerEntity player, boolean maxTransfer, boolean doTransfer) {
        if (!SscGridClickTransfer.supports(menu, recipe)) return helper.createInternalError();
        var outcome = SscGridClickTransfer.transfer(MinecraftClient.getInstance(), recipe, !doTransfer);
        if (outcome == net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome.SUCCESS) return null;
        String key = switch (outcome) {
            case NOT_ENOUGH -> "not_enough_materials";
            case NO_ROOM -> "no_room";
            case CURSOR_NOT_EMPTY -> "cursor_not_empty";
            default -> "failed";
        };
        return helper.createUserErrorWithTooltip(Text.translatable("error.ssc_addon.transfer." + key));
    }
}
