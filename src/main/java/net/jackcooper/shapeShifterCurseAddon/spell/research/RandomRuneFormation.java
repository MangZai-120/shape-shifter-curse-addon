package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.Objects;

/** Natural loot reveals only the world's spell foundation; enhancements are player assembled. */
public final class RandomRuneFormation {
    private RandomRuneFormation() {}

    /** Keep the previous entry point compatible; loot seeds no longer choose enhancement runes. */
    public static int[] generate(SlottedSpellRecipes.Recipe recipe, int level, RuneLanguage language, long seed) {
        Objects.requireNonNull(recipe, "Spell recipe");
        Objects.requireNonNull(language, "Rune language");
        return RuneLayout.foundation(recipe, level, language);
    }
}
