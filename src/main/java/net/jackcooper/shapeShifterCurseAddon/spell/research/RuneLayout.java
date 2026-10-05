package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;

/** Current geometry is separate from legacy recipes and frozen item snapshots. */
public final class RuneLayout {
    public static final int VERSION = 3, PREVIOUS_VERSION = 2;
    private static final int[] BASE = {3, 3, 5, 11, 19};
    private RuneLayout() {}
    public static int[] layers(int level) { return level == 4 ? new int[]{6, 5, 8} : SlottedFormation.layers(level); }
    public static int size(int level) { return Arrays.stream(layers(level)).sum(); }
    public static int[] empty(int level) { int[] slots = new int[size(level)]; Arrays.fill(slots, -1); return slots; }
    public static List<FormationDiagram.Point> positions(int level) { return SlottedFormation.positions(level, layers(level)); }
    public static boolean validDraft(int level, int[] slots) {
        return level >= 1 && level <= 5 && slots != null && slots.length == size(level)
                && Arrays.stream(slots).allMatch(g -> g >= -1 && g < RuneRole.values().length);
    }
    public static boolean validDraft(int level, int[] slots, int version) {
        return version == VERSION ? validDraft(level, slots)
                : (version == SlottedFormation.VERSION || version == PREVIOUS_VERSION) && SlottedFormation.validDraft(level, slots);
    }
    public static int baseSize(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Rune level");
        return BASE[level - 1];
    }
    public static int baseSize(int level, int version) {
        return version == PREVIOUS_VERSION && level == 4 ? 13 : baseSize(level);
    }
    public static boolean enhancement(int level, int slot) {
        return slot >= baseSize(level) && slot < size(level);
    }
    public static boolean closed(int level) { return level >= 2 && level <= 5; }
    public static List<int[]> edges(int level) {
        List<int[]> edges = new ArrayList<>();
        int start = baseSize(level), end = size(level);
        for (int i = start; i + 1 < end; i++) edges.add(new int[]{i, i + 1});
        if (closed(level) && end - start > 2) edges.add(new int[]{end - 1, start});
        return edges;
    }
    public static int[] foundation(SlottedSpellRecipes.Recipe recipe, int level, RuneLanguage language) {
        int[] slots = empty(level);
        int[] original = SlottedSpellRecipes.glyphs(recipe, level, language);
        if (level == 4) original = insertSixthVertex(original);
        System.arraycopy(original, 0, slots, 0, baseSize(level));
        return slots;
    }
    public static boolean matches(SlottedSpellRecipes.Recipe recipe, int level, int[] slots, RuneLanguage language) {
        if (!validDraft(level, slots)) return false;
        int[] original = SlottedSpellRecipes.glyphs(recipe, level, language);
        if (level == 4) original = insertSixthVertex(original);
        for (int i = 0; i < baseSize(level); i++) if (original[i] != slots[i]) return false;
        return true;
    }
    private static int[] insertSixthVertex(int[] previous) {
        int[] current = new int[19];
        System.arraycopy(previous, 0, current, 0, 5); current[5] = previous[0];
        System.arraycopy(previous, 5, current, 6, previous.length - 5); return current;
    }
    /** Free the old three outer foundation sockets; keep only actual old modifiers. */
    public static int[] migrateDraft(int level, int[] slots, int version) {
        if (!validDraft(level, slots, version)) return new int[0];
        if (version == VERSION || level != 4) return slots.clone();
        int[] current = insertSixthVertex(slots);
        Arrays.fill(current, 11, 14, -1); return current;
    }
}
