package net.jackcooper.shapeShifterCurseAddon.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscGridClickTransfer;
import net.minecraft.text.Text;

/** Only our own display is claimed: other datapacks may craft the same output differently. */
public final class SscSpecialRecipeTransferHandler implements TransferHandler {
    @Override public double getPriority() { return 50; }
    @Override public ApplicabilityResult checkApplicable(Context context) {
        return context.getDisplay() instanceof SscSpecialCraftingDisplay display
            && SscGridClickTransfer.supports(context.getMenu(), display.recipe())
            ? ApplicabilityResult.createApplicable() : ApplicabilityResult.createNotApplicable();
    }
    @Override public Result handle(Context context) {
        if (!(context.getDisplay() instanceof SscSpecialCraftingDisplay display)) return Result.createNotApplicable();
        var outcome = SscGridClickTransfer.transfer(context.getMinecraft(), display.recipe(), !context.isActuallyCrafting());
        if (outcome == net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome.SUCCESS) {
            if (context.isActuallyCrafting()) context.getMinecraft().setScreen(context.getContainerScreen());
            return Result.createSuccessful();
        }
        String key = switch (outcome) {
            case NOT_ENOUGH -> "not_enough_materials";
            case NO_ROOM -> "no_room";
            case CURSOR_NOT_EMPTY -> "cursor_not_empty";
            default -> "failed";
        };
        return Result.createFailed(Text.translatable("error.ssc_addon.transfer." + key));
    }
}
