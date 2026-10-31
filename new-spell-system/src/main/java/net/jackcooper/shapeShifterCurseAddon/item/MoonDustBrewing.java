package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.item.*;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/** Item-aware recipes: only drinking bottles may produce full-strength moon-dust potions. */
public final class MoonDustBrewing {
    private MoonDustBrewing() {}
    public static Item output(ItemStack input, ItemStack ingredient) {
        if (input.isOf(Items.POTION) && PotionUtil.getPotion(input) == Potions.AWKWARD
                && ingredient.isOf(RegCustomItem.UNTREATED_MOONDUST)) return MoonDustReliefItem.BOTTLE;
        if (input.isOf(MoonDustReliefItem.BOTTLE) && ingredient.isOf(Items.GUNPOWDER)) return MoonDustReliefItem.SPLASH;
        return null;
    }
    public static ItemStack craft(ItemStack input, Item output) {
        ItemStack result = new ItemStack(output, input.getCount());
        if (input.hasNbt()) result.setNbt(input.getNbt().copy());
        // Custom relief items own their dose; don't carry an awkward/base effect into the thrown entity.
        result.getOrCreateNbt().remove("Potion");
        result.getNbt().remove("CustomPotionEffects");
        return result;
    }
    public static boolean isBottle(ItemStack stack) {
        return stack.isOf(MoonDustReliefItem.BOTTLE) || stack.isOf(MoonDustReliefItem.SPLASH);
    }
}
