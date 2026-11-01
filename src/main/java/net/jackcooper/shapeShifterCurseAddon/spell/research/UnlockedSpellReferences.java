package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/** Only the player's unlocked, research-rank-accessible foundations are sent as references. */
public final class UnlockedSpellReferences {
    private UnlockedSpellReferences() {}

    public static NbtList write(FormationKnowledgeComponent knowledge, WorldRuneState world, int level) {
        NbtList entries = new NbtList();
        ResearchProgress progress = knowledge.research();
        if (!world.worldId().equals(progress.world) || level < 1 || level > progress.rank) return entries;
        for (var recipe : SlottedSpellRecipes.all()) {
            if (!knowledge.hasSpell(recipe.spell()) || !SlottedSpellRecipes.available(recipe, level)) continue;
            NbtCompound entry = new NbtCompound();
            entry.putString("Spell", recipe.spell());
            entry.putInt("Level", level);
            entry.putIntArray("Slots", RuneLayout.foundation(recipe, level, world.language()));
            entries.add(entry);
        }
        return entries;
    }
}
