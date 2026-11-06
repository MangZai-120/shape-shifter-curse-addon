package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.jackcooper.shapeShifterCurseAddon.item.InfiniteEnergyPotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * 让「无限压缩能量药水」（自定义 Item，未继承 PotionItem）能放进酿造台底部 3 个药水槽。
 * 原版 {@link BrewingStandBlockEntity#isValid} 对药水槽硬编码只接受 vanilla 药水/玻璃瓶，
 * 这里在 HEAD 放行我们的自定义药水（供漏斗自动化 / SidedInventory 校验使用）。
 * {@link BrewingPotionSlotMixin} 负责 GUI 手动放入的校验，
 * {@link BrewingRegistryInfiniteMixin} 负责让酿造判定/产出真正生效（不依赖 ITEM_RECIPES 列表注册）。
 */

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandInfinitePotionMixin {

    @Shadow
    public abstract ItemStack getStack(int slot);

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$allowInfinitePotion(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // 底部 3 个药水槽（0/1/2）：额外接受无限压缩能量药水 / 凋零药水 / 通用能量药水，并保持原版「该槽必须为空」的限制
        if (slot >= 0 && slot < 3
                && (stack.getItem() instanceof InfiniteEnergyPotionItem
                    || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem
                    || stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem)
                && this.getStack(slot).isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}

