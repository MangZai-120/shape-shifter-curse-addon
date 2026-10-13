package net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer;

import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Crafting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import java.util.ArrayList;

/** Shared client adapter for JEI, REI and EMI. The server processes ordinary inventory clicks. */
public final class SscGridClickTransfer {
    private SscGridClickTransfer() {}
    public static boolean supports(ScreenHandler handler, Crafting recipe) {
        return handler instanceof CraftingScreenHandler || (recipe.shapeless() && handler instanceof PlayerScreenHandler);
    }
    public static Outcome transfer(MinecraftClient client, Crafting recipe, boolean checkOnly) {
        if (client.player == null || client.interactionManager == null) return Outcome.FAILED;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (!supports(handler, recipe)) return Outcome.FAILED;
        int width = handler instanceof CraftingScreenHandler ? 3 : 2;
        var grid = new ArrayList<ItemStack>();
        var inventory = new ArrayList<ItemStack>();
        for (int i = 0; i < width * width; i++) grid.add(handler.getSlot(1 + i).getStack());
        for (int i = 0; i < 36; i++) inventory.add(client.player.getInventory().getStack(i));
        var plan = SscTransferPlan.create(recipe.gridFor(width), grid, inventory, handler.getCursorStack());
        if (checkOnly || plan.outcome() != Outcome.SUCCESS) return plan.outcome();
        for (var click : plan.clicks()) {
            int slot;
            if (click.slot() < grid.size()) slot = 1 + click.slot();
            else {
                int inv = click.slot() - grid.size();
                slot = inv < 9 ? (width == 3 ? 37 : 36) + inv : (width == 3 ? 1 : 0) + inv;
            }
            client.interactionManager.clickSlot(handler.syncId, slot, click.button(), SlotActionType.PICKUP, client.player);
        }
        return handler.getCursorStack().isEmpty() ? Outcome.SUCCESS : Outcome.FAILED;
    }
}
