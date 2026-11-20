package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan;
import net.jackcooper.shapeShifterCurseAddon.recipe.UpgradeMoisturizerRecipe;
import net.minecraft.network.packet.s2c.play.CraftFailedResponseS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeScreenHandler.class)
public abstract class MoisturizerRecipePlacementMixin {
    @Inject(method = "fillInputSlots", at = @At("HEAD"), cancellable = true)
    private void ssca$placeMatchingLevel(boolean craftAll, Recipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        if (!(recipe instanceof UpgradeMoisturizerRecipe upgrade) || upgrade.isIgnoredInRecipeBook()
                || !((Object) this instanceof CraftingScreenHandler table)) return;
        ci.cancel();
        if (player.currentScreenHandler != table || !player.getRecipeBook().contains(recipe)) return;
        var plan = upgrade.planPlacement(table);
        if (plan.outcome() == SscTransferPlan.Outcome.SUCCESS) {
            for (var click : plan.clicks()) {
                table.onSlotClick(1 + click.slot(), click.button(), SlotActionType.PICKUP, player);
            }
        } else {
            player.networkHandler.sendPacket(new CraftFailedResponseS2CPacket(table.syncId, recipe));
        }
    }
}
