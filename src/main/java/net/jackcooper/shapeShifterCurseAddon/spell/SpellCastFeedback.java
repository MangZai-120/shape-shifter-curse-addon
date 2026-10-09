package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;

import java.util.Locale;

/** Shared spell names and wording for local preflight and authoritative rejection. */
public final class SpellCastFeedback {
    private SpellCastFeedback() {}

    public static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.ceil(Math.max(0, ticks) / 2.0) / 10.0);
    }

    public static Text spellName(Spell spell, ItemStack scroll) {
        var name = Text.translatable(spell.getNameKey());
        if (scroll.getNbt() != null && scroll.getNbt().contains(RuneScheme.KEY, NbtElement.COMPOUND_TYPE)) {
            name.append(Text.translatable("research.ssc_addon.runes.modified_suffix"));
        }
        return name;
    }

    public static Text noMana(ItemStack book, int cost) {
        if (book == null || book.isEmpty()) return Text.translatable("message.ssc_addon.spellbook.no_book");
        return manaFailure(SpellbookData.getMana(book), SpellbookData.getMaxMana(book), cost);
    }

    public static Text manaFailure(int available, int capacity, int cost) {
        if (cost < 0) return Text.translatable("message.ssc_addon.spellbook.cost_unavailable");
        if (cost > capacity) return Text.translatable("message.ssc_addon.spellbook.capacity_insufficient", cost, capacity, cost - capacity);
        return Text.translatable("message.ssc_addon.spellbook.no_mana_details", cost, available, Math.max(0, cost - available));
    }

    public static Text cooldown(long remaining) {
        return Text.translatable("message.ssc_addon.spellbook.cooldown", seconds(remaining));
    }

    public static Text stabilizing(long remaining) {
        return Text.translatable("message.ssc_addon.spellbook.swap_wait", seconds(remaining));
    }
}
