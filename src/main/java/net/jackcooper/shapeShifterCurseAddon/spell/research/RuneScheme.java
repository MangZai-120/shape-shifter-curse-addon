package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Item data is a display mirror. Only the world's immutable registry authorizes a scheme. */
public final class RuneScheme {
    public static final String KEY = "RuneScheme";
    private RuneScheme() {}
    public static boolean hasEnhancements(NbtCompound scheme) {
        return scheme != null && RuneLayout.hasEnhancements(scheme.getInt("Level"),
                scheme.getIntArray("Slots"), scheme.getInt("Version"));
    }
    public static boolean isModified(ItemStack stack) {
        return stack != null && stack.getNbt() != null && stack.getNbt().contains(KEY, NbtElement.COMPOUND_TYPE)
                && hasEnhancements(stack.getNbt().getCompound(KEY));
    }
    public static NbtCompound create(WorldRuneState world, int level, int[] slots, RuneBuildEvaluator.Result result) {
        if (!result.valid()) throw new IllegalArgumentException("Invalid rune scheme");
        NbtCompound n = new NbtCompound();
        n.putUuid("World", world.worldId()); n.putInt("Version", RuneLayout.VERSION);
        n.putInt("Rules", RuneBuildEvaluator.RULE_VERSION); n.putString("Spell", result.recipe().spell());
        n.putInt("Level", level); n.putIntArray("Slots", slots); n.putInt("Stability", result.stability());
        n.put("Modifiers", result.modifiers().write());
        n.put("Summary", RuneSummary.write(result));
        n.putUuid("Id", world.registerScheme(n)); return n;
    }
    public static NbtCompound authoritative(WorldRuneState world, NbtCompound mirror) {
        if (mirror == null || !mirror.containsUuid("Id") || !mirror.containsUuid("World")
                || !world.worldId().equals(mirror.getUuid("World"))) return null;
        NbtCompound saved = world.scheme(mirror.getUuid("Id"));
        if (saved == null) return null;
        NbtCompound expected = mirror.copy(); expected.remove("Id");
        return expected.equals(saved) ? saved : null;
    }
    public static boolean validScroll(ServerPlayerEntity player, ItemStack scroll, Spell spell) {
        if (scroll.getNbt() == null || !scroll.getNbt().contains(KEY)) return true;
        NbtCompound saved = authoritative(WorldRuneState.get(player.getServer()), scroll.getNbt().getCompound(KEY));
        boolean valid = saved != null && saved.getString("Spell").equals(spell.getId().getPath())
                && saved.getInt("Level") >= 1 && saved.getInt("Level") <= ScrollData.getLevel(scroll);
        if (valid) normalizeUnmodified(scroll, saved);
        if (!valid) player.sendMessage(Text.translatable("research.ssc_addon.runes.invalid_scheme"), true);
        return valid;
    }
    public static NbtCompound authoritativeScroll(WorldRuneState world, ItemStack scroll) {
        if (scroll.getNbt() == null || !scroll.getNbt().contains(KEY)) return null;
        NbtCompound saved = authoritative(world, scroll.getNbt().getCompound(KEY));
        Spell spell = ScrollData.getSpell(scroll);
        return saved != null && spell != null && saved.getString("Spell").equals(spell.getId().getPath())
                && saved.getInt("Level") >= 1 && saved.getInt("Level") <= ScrollData.getLevel(scroll) ? saved : null;
    }
    /** Strip only an authorized legacy foundation-only profile, preserving every ordinary scroll field. */
    public static boolean normalizeUnmodified(WorldRuneState world, ItemStack scroll) {
        if (scroll.getNbt() == null || !scroll.getNbt().contains(KEY) || isModified(scroll)) return false;
        return normalizeUnmodified(scroll, authoritativeScroll(world, scroll));
    }
    private static boolean normalizeUnmodified(ItemStack scroll, NbtCompound saved) {
        if (saved == null || hasEnhancements(saved)) return false;
        scroll.getNbt().remove(KEY);
        return true;
    }
    public static RuneModifiers modifiers(PlayerEntity player, ItemStack scroll, int selectedLevel) {
        if (scroll == null || scroll.getNbt() == null || !scroll.getNbt().contains(KEY)) return RuneModifiers.NONE;
        NbtCompound n = scroll.getNbt().getCompound(KEY);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            n = authoritative(WorldRuneState.get(serverPlayer.getServer()), n);
            if (n == null) return RuneModifiers.NONE;
        }
        if (!hasEnhancements(n)) return RuneModifiers.NONE;
        if (selectedLevel <= 1 || selectedLevel < n.getInt("Level")) return RuneModifiers.NONE;
        return RuneModifiers.read(n.getCompound("Modifiers"));
    }
    public static int imprint(net.minecraft.inventory.Inventory table, WorldRuneState world) {
        ItemStack scheme = table.getStack(0), scroll = table.getStack(3);
        if (!scheme.isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.SPELL_FORMATION)
                || scheme.getNbt() == null || !(scroll.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem)
                || ArcaneAnalysis.isUnanalyzed(scroll)) return 1;
        NbtCompound mirror = scheme.getNbt().getCompound(KEY), saved = authoritative(world, mirror);
        Spell spell = ScrollData.getSpell(scroll);
        if (saved == null || spell == null) return 2;
        if (!spell.getId().getPath().equals(saved.getString("Spell")) || ScrollData.getLevel(scroll) != saved.getInt("Level")) return 3;
        // All checks precede both mutations. Keep uses, selected level, cooldown and spatial bindings intact.
        if (hasEnhancements(saved)) scroll.getOrCreateNbt().put(KEY, mirror.copy());
        else scroll.getOrCreateNbt().remove(KEY);
        scheme.decrement(1); table.markDirty(); return 0;
    }
}
