package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.item.MoonDustBrewing;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registry and both inventory paths must agree. Tubes are deliberately not potion-slot containers. */
@Mixin(BrewingRecipeRegistry.class)
public abstract class MoonDustBrewingMixin {
    @Inject(method = "isValidIngredient", at = @At("HEAD"), cancellable = true)
    private static void ssca$moonDustIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isOf(RegCustomItem.UNTREATED_MOONDUST)) cir.setReturnValue(true);
    }
    @Inject(method = "hasRecipe", at = @At("HEAD"), cancellable = true)
    private static void ssca$moonDustRecipe(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (MoonDustBrewing.output(input, ingredient) != null) cir.setReturnValue(true);
    }
    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private static void ssca$moonDustCraft(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        var output = MoonDustBrewing.output(input, ingredient);
        if (output != null) cir.setReturnValue(MoonDustBrewing.craft(input, output));
    }
}

@Mixin(BrewingStandBlockEntity.class)
abstract class MoonDustBrewingInventoryMixin {
    @Shadow public abstract ItemStack getStack(int slot);
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void ssca$moonDustBottle(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot >= 0 && slot < 3 && MoonDustBrewing.isBottle(stack)) cir.setReturnValue(getStack(slot).isEmpty());
    }
}

@Mixin(targets = "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
abstract class MoonDustBrewingSlotMixin {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void ssca$moonDustBottle(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (MoonDustBrewing.isBottle(stack)) cir.setReturnValue(true);
    }
}
