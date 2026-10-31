package net.jackcooper.shapeShifterCurseAddon.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** 只隐藏原版模板伪装的内部效果载体，保留真正独立的月尘、通用能量等附属药水。 */
public final class PotionCarrierVisibility {
    private static boolean registered;

    private PotionCarrierVisibility() {}

    public static void register() {
        if (registered) return;
        registered = true;
        // 两种可修改列表均需过滤：查看器可能收集展示栏或搜索栏任意一份内容。
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            entries.getDisplayStacks().removeIf(PotionCarrierVisibility::isInternalVanillaCarrier);
            entries.getSearchTabStacks().removeIf(PotionCarrierVisibility::isInternalVanillaCarrier);
        });
    }

    public static boolean isInternalVanillaCarrier(ItemStack stack) {
        if (!(stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION) || stack.isOf(Items.TIPPED_ARROW)) || !stack.hasNbt()) {
            return false;
        }
        // 比较保存的类型ID，不要求已停用的月尘Potion继续注册，也不触发效果注册的静态初始化。
        return switch (stack.getNbt().getString("Potion")) {
            case "ssc_addon:moon_dust_tube", "ssca_neomagic:moon_dust_tube", "ssc_addon:universal_energy" -> true;
            default -> false;
        };
    }
}
