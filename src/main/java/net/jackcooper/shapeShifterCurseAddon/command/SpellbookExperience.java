package net.jackcooper.shapeShifterCurseAddon.command;

import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.item.ItemStack;
import java.util.function.Supplier;

/** Selection and bounded EXP mutation shared by both server command targets. */
final class SpellbookExperience {
    private SpellbookExperience() {}
    private static boolean isBook(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof MoonDustSpellbookItem;
    }
    static ItemStack select(boolean equipped, ItemStack main, ItemStack off, Supplier<ItemStack> worn) {
        if (equipped) { ItemStack book = worn.get(); return isBook(book) ? book : null; }
        return isBook(main) ? main : isBook(off) ? off : null;
    }
    record Result(boolean success, int totalTen, int maximumAdditional) {}
    static Result add(ItemStack book, int amount) {
        if (!isBook(book) || amount < 1) throw new IllegalArgumentException("Expected a spellbook and positive experience");
        // A rejected request must not migrate legacy Exp or mutate any other NBT.
        int before = SpellbookData.getExpTen(book.copy());
        int maximum = (Integer.MAX_VALUE - before) / 10;
        long after = before + amount * 10L;
        if (after > Integer.MAX_VALUE) return new Result(false, before, maximum);
        SpellbookData.setExpTen(book, (int) after);
        return new Result(true, (int) after, maximum);
    }
}
