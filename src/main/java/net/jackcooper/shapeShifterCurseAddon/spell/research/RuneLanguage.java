package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.Arrays;
import java.util.Random;

/** Immutable generated definitions, also persisted as actual values rather than seed alone. */
public final class RuneLanguage {
    private final int[] meanings;
    private final int[] rules;
    public RuneLanguage(int[] meanings, int[] rules) {
        if (meanings.length != 17 || rules.length != 11) throw new IllegalArgumentException("Language length");
        boolean[] seen = new boolean[17];
        for (int i = 0; i < meanings.length; i++) {
            int m = meanings[i];
            if (m < 0 || m >= 17 || seen[m] || (i < 7) != (m < 7)) throw new IllegalArgumentException("Glyph mapping");
            seen[m] = true;
        }
        for (int r : rules) if (r < 0 || r > 7) throw new IllegalArgumentException("Family rule");
        this.meanings = meanings.clone(); this.rules = rules.clone();
    }
    public static RuneLanguage generate(long seed) {
        Random random = new Random(seed);
        int[] mapping = new int[17], rules = new int[11];
        for (int i = 0; i < 17; i++) mapping[i] = i;
        shuffle(mapping, 0, 7, random); shuffle(mapping, 7, 17, random);
        for (int i = 0; i < 11; i++) rules[i] = random.nextInt(8);
        return new RuneLanguage(mapping, rules);
    }
    private static void shuffle(int[] a, int start, int end, Random r) {
        for (int i = end - 1; i > start; i--) { int j = start + r.nextInt(i - start + 1); int v = a[i]; a[i] = a[j]; a[j] = v; }
    }
    public RuneRole meaning(int glyph) {
        if (glyph < 0 || glyph >= 17) throw new IllegalArgumentException("Glyph");
        return RuneRole.values()[meanings[glyph]];
    }
    public int glyph(RuneRole role) { for (int i = 0; i < 17; i++) if (meanings[i] == role.ordinal()) return i; throw new IllegalStateException(); }
    public boolean clockwise(int family) { return (rules[family] & 1) != 0; }
    public boolean alternate(int family) { return (rules[family] & 2) != 0; }
    public boolean branchStable(int family) { return (rules[family] & 4) != 0; }
    public int rule(int family) { return rules[family]; }
    public int[] meanings() { return meanings.clone(); }
    public int[] rules() { return rules.clone(); }
    @Override public boolean equals(Object other) { return other instanceof RuneLanguage l && Arrays.equals(meanings, l.meanings) && Arrays.equals(rules, l.rules); }
    @Override public int hashCode() { return Arrays.hashCode(meanings) * 31 + Arrays.hashCode(rules); }
}
