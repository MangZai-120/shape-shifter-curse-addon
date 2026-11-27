package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Objects;
import java.util.function.IntPredicate;

/** The input selection and the HUD share decoded, read-only slots for the equipped book. */
public final class ClientSpellbookSlots {
    private static ClientSpellbookSlots cached;
    private final NbtList entries;
    private final ItemStack[] slots;
    private final IntPredicate occupied = this::hasScroll;

    private ClientSpellbookSlots(ItemStack book, int count, NbtList entries) {
        slots = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            // Vanilla's decoder can keep a reference to the source item's nested tag.
            slots[i] = SpellbookData.getScroll(book, i).copy();
        }
        this.entries = entries == null ? null : entries.copy();
    }

    /** Called on selection checks and HUD tick updates. A server packet can edit the same book in place. */
    public static ClientSpellbookSlots forBook(ItemStack book) {
        int count = SpellbookData.getSlotCount(book);
        var raw = book.getNbt() == null ? null : book.getNbt().get(SpellbookData.NBT_ITEMS);
        NbtList entries = raw instanceof NbtList list && list.getHeldType() == NbtElement.COMPOUND_TYPE ? list : null;
        if (cached == null || cached.slots.length != count || !Objects.equals(cached.entries, entries)) {
            cached = new ClientSpellbookSlots(book, count, entries);
        }
        return cached;
    }

    public static void clear() {
        cached = null;
    }

    /** For display only. Actual casts continue reading the book directly. */
    public ItemStack scroll(int slot) {
        return slots[slot];
    }

    public boolean hasScroll(int slot) {
        return !slots[slot].isEmpty();
    }

    public int nextFilledSlot(int from, int direction) {
        return SpellbookData.nextFilledSlot(slots.length, from, direction, occupied);
    }

    public int firstFilledSlot() {
        return nextFilledSlot(slots.length - 1, 1);
    }
}
