package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.InfiniteEnergyPotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * 让「无限压缩能量药水」（自定义 Item，未继承 PotionItem）能放进酿造台底部 3 个药水槽。
 * 原版 {@link BrewingStandBlockEntity#isValid} 对药水槽硬编码只接受 vanilla 药水/玻璃瓶，
 * 这里在 HEAD 放行我们的自定义药水（供漏斗自动化 / SidedInventory 校验使用）。
 * 同文件的 {@link BrewingPotionSlotMixin} 负责 GUI 手动放入的校验，
 * {@link BrewingRegistryInfiniteMixin} 负责让酿造判定/产出真正生效（不依赖 ITEM_RECIPES 列表注册）。
 */

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandInfinitePotionMixin {

    @Shadow
    public abstract ItemStack getStack(int slot);

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$allowInfinitePotion(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // 底部 3 个药水槽（0/1/2）：额外接受无限压缩能量药水 / 凋零药水，并保持原版「该槽必须为空」的限制
        if (slot >= 0 && slot < 3
                && (stack.getItem() instanceof InfiniteEnergyPotionItem
                    || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem)
                && this.getStack(slot).isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}

/**
 * 让 GUI 内的药水槽（{@code BrewingStandScreenHandler$PotionSlot}）接受无限压缩能量药水。
 * {@code matches} 是 public static 方法，玩家手动放入 / shift 转移都走它，必须放行否则 GUI 拒绝放入。
 */
@Mixin(targets = "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
abstract class BrewingPotionSlotMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void ssc_addon$allowInfinitePotion(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof InfiniteEnergyPotionItem
                || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem) {
            cir.setReturnValue(true);
        }
    }
}

/**
 * 让酿造台对无限压缩能量药水的「能否酿造」判定与「产出」真正生效，
 * 不依赖向 {@code ITEM_RECIPES} 列表注册（该列表注册在本项目运行时被发现并不可靠）。
 * 直接在 {@link BrewingRecipeRegistry#hasRecipe} / {@code craft} 的 HEAD 拦截我们的组合：
 *   饮用型 + 火药   → 喷溅型；
 *   喷溅型 + 龙息   → 滞留型。
 * 仅对我们自己的物品生效，其它输入一律放行给原版逻辑（不影响普通药水酿造）。
 */
@Mixin(BrewingRecipeRegistry.class)
abstract class BrewingRegistryInfiniteMixin {

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
        return null;
    }
}


