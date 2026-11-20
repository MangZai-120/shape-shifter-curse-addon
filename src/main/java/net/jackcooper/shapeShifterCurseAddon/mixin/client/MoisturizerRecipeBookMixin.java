package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.IntList;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan;
import net.jackcooper.shapeShifterCurseAddon.recipe.UpgradeMoisturizerRecipe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.CraftingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RecipeResultCollection.class)
public abstract class MoisturizerRecipeBookMixin {
    @WrapOperation(method = "computeCraftables", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/recipe/RecipeMatcher;match(Lnet/minecraft/recipe/Recipe;Lit/unimi/dsi/fastutil/ints/IntList;)Z"))
    private boolean ssca$checkHumidifierLevel(RecipeMatcher matcher, Recipe<?> recipe, IntList inputs, Operation<Boolean> original) {
        if (recipe instanceof UpgradeMoisturizerRecipe upgrade && !upgrade.isIgnoredInRecipeBook()) {
            var player = MinecraftClient.getInstance().player;
            if (player != null && player.currentScreenHandler instanceof CraftingScreenHandler table) {
                return upgrade.planPlacement(table).outcome() == SscTransferPlan.Outcome.SUCCESS;
            }
        }
        return original.call(matcher, recipe, inputs);
    }
}
