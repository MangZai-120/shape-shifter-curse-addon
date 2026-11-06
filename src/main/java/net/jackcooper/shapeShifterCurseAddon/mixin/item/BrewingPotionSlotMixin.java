package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.item.ItemStack;
import net.jackcooper.shapeShifterCurseAddon.item.InfiniteEnergyPotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
public abstract class BrewingPotionSlotMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void ssc_addon$allowInfinitePotion(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof InfiniteEnergyPotionItem
                || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem
                || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem) {
            cir.setReturnValue(true);
        }
    }
}

