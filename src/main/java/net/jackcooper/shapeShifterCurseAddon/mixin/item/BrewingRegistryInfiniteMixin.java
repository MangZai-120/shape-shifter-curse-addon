package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(BrewingRecipeRegistry.class)
public abstract class BrewingRegistryInfiniteMixin {

    /** hasRecipe(input=底部药水, ingredient=材料)：命中我们的组合则返回可酿造。 */
    @Inject(method = "hasRecipe(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void ssc_addon$hasInfiniteRecipe(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (ssc_addon$infiniteOutput(input, ingredient) != null) {
            cir.setReturnValue(true);
        }
    }

    /** craft(ingredient=材料, input=底部药水)：命中我们的组合则直接产出对应形态（全新满瓶）。 */
    @Inject(method = "craft(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void ssc_addon$craftInfinite(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        Item out = ssc_addon$infiniteOutput(input, ingredient);
        if (out != null) {
            cir.setReturnValue(new ItemStack(out));
        }
    }

    /** 命中返回产出物品，否则返回 null。 */
    @Unique
    private static Item ssc_addon$infiniteOutput(ItemStack input, ItemStack ingredient) {
        if (input == null || input.isEmpty() || ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        if (input.isOf(SscAddon.INFINITE_ENERGY_POTION) && ingredient.isOf(Items.GUNPOWDER)) {
            return SscAddon.INFINITE_ENERGY_POTION_SPLASH;
        }
        if (input.isOf(SscAddon.INFINITE_ENERGY_POTION_SPLASH) && ingredient.isOf(Items.DRAGON_BREATH)) {
            return SscAddon.INFINITE_ENERGY_POTION_LINGERING;
        }
        // 凋零药水：饮用 + 火药 → 喷溅；喷溅 + 龙息 → 滞留
        if (input.isOf(SscAddon.WITHER_POTION) && ingredient.isOf(Items.GUNPOWDER)) {
            return SscAddon.WITHER_POTION_SPLASH;
        }
        if (input.isOf(SscAddon.WITHER_POTION_SPLASH) && ingredient.isOf(Items.DRAGON_BREATH)) {
            return SscAddon.WITHER_POTION_LINGERING;
        }
        // 通用能量药水：饮用 + 火药 → 喷溅；喷溅 + 龙息 → 滞留
        if (input.isOf(SscAddon.UNIVERSAL_ENERGY_POTION) && ingredient.isOf(Items.GUNPOWDER)) {
            return SscAddon.UNIVERSAL_ENERGY_POTION_SPLASH;
        }
        if (input.isOf(SscAddon.UNIVERSAL_ENERGY_POTION_SPLASH) && ingredient.isOf(Items.DRAGON_BREATH)) {
            return SscAddon.UNIVERSAL_ENERGY_POTION_LINGERING;
        }
        return null;
    }
}


