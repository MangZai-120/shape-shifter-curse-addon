package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SlottedFormation {
    public static final int VERSION = 1;
    private static final int[][] COUNTS = {{3}, {3, 5}, {5, 5}, {5, 5, 8}, {6, 5, 8, 12}};
    private static final int[][] RADII = {{185}, {110, 226}, {110, 226}, {70, 148, 226}, {60, 114, 170, 226}};
    private SlottedFormation() {}
    public static int[] layers(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("无效法阵等级");
        return COUNTS[level - 1].clone();
    }
    public static int size(int level) { return Arrays.stream(layers(level)).sum(); }
    public static int[] empty(int level) { int[] result = new int[size(level)]; Arrays.fill(result, -1); return result; }
    public static boolean validDraft(int level, int[] slots) {
        return level >= 1 && level <= 5 && slots.length == size(level)
                && Arrays.stream(slots).allMatch(glyph -> glyph >= -1 && glyph < 17);
    }
    public static List<FormationDiagram.Point> positions(int level) {
        int[] counts = layers(level); List<FormationDiagram.Point> result = new ArrayList<>();
        for (int layer = 0; layer < counts.length; layer++) {
            for (int slot = 0; slot < counts[layer]; slot++) {
                double angle = -Math.PI / 2 + slot * Math.PI * 2 / counts[layer];
                result.add(new FormationDiagram.Point(256 + Math.cos(angle) * RADII[level - 1][layer],
                        256 + Math.sin(angle) * RADII[level - 1][layer]));
            }
        }
        return List.copyOf(result);
    }
    public static int school(RuneLanguage language, int glyph) {
        RuneRole role = language.meaning(glyph); return role.element() ? role.ordinal() : 7;
    }
    public static int[] ink(RuneLanguage language, int[] slots) {
        int[] counts = new int[8];
        for (int glyph : slots) { if (glyph == -1) continue; if (glyph < 0 || glyph > 16) throw new IllegalArgumentException("无效符文"); counts[school(language, glyph)]++; }
        for (int index = 0; index < counts.length; index++) counts[index] = (counts[index] + 1) / 2;
        return counts;
    }
}