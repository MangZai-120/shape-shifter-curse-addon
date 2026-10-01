package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;

public final class ArcaneAnalysis {
    public static final String UNANALYZED = "SscaUnanalyzed";

    private ArcaneAnalysis() {}

    public static boolean isUnanalyzed(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(UNANALYZED);
    }

    public static ItemStack markUnanalyzed(ItemStack stack) {
        if (!stack.isEmpty()) stack.getOrCreateNbt().putBoolean(UNANALYZED, true);
        return stack;
    }

    public static void identify(ItemStack stack) {
        if (stack.hasNbt()) stack.getNbt().remove(UNANALYZED);
    }
}