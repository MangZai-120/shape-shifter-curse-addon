package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaleGeometry.Box;

/** User-approved offsets are relative to the unchanged 33x68 main panel. */
public final class SkillHudLayout {
    public static final int MAIN_WIDTH = 33, MAIN_HEIGHT = 68, MAIN_STEP = 34;
    public static final int EXTRA_SIZE = 18, EXTRA_ICON_SIZE = 14, PADDING = 8;
    public static final int LOWER_X = 4, UPPER_X = 3, LOWER_Y = 63, UPPER_Y = -13;
    public enum Slot { PRIMARY, SECONDARY, LOWER, UPPER;
        public boolean extra() { return this == LOWER || this == UPPER; }
    }
    public record Entry<T>(T skill, Slot slot) {}
    public record Origin(int x, int y) {}

    private SkillHudLayout() {}

    /** Keep the existing first primary/secondary pair; append extras below, then above. */
    public static <T> List<Entry<T>> select(List<T> skills, Predicate<T> primary) {
        return select(skills, primary, ignored -> true, List.of());
    }

    public static <T> List<Entry<T>> select(List<T> skills, Predicate<T> primary,
                                           Predicate<T> extraEligible, List<T> learnedExtras) {
        T first = skills.stream().filter(primary).findFirst().orElse(null);
        T second = skills.stream().filter(primary.negate()).findFirst().orElse(null);
        var result = new ArrayList<Entry<T>>(4);
        if (first != null) result.add(new Entry<>(first, Slot.PRIMARY));
        if (second != null) result.add(new Entry<>(second, Slot.SECONDARY));
        int extras = 0;
        var candidates = new ArrayList<T>();
        skills.stream().filter(extraEligible).forEach(candidates::add);
        candidates.addAll(learnedExtras);
        for (T skill : candidates) {
            if (skill.equals(first) || skill.equals(second)) continue;
            if (extras == 2) break;
            result.add(new Entry<>(skill, extras++ == 0 ? Slot.LOWER : Slot.UPPER));
        }
        return List.copyOf(result);
    }

    public static int extraCount(List<? extends Entry<?>> entries) {
        return (int) entries.stream().filter(entry -> entry.slot().extra()).count();
    }

    public static Box bounds(int extras, boolean right) {
        var background = backgroundBounds(extras, right);
        return new Box(0, background.y(), MAIN_WIDTH, background.height());
    }

    public static Box backgroundBounds(int extras, boolean right) {
        int top = extras >= 2 ? -16 : 0;
        int height = extras >= 2 ? 100 : extras == 1 ? 84 : 68;
        return new Box(right ? MAIN_WIDTH - 14 : 0, top, 14, height);
    }

    public static int extraX(Slot slot, boolean right) {
        if (!slot.extra()) throw new IllegalArgumentException("Expected an extra-skill slot");
        int x = slot == Slot.LOWER ? LOWER_X : UPPER_X;
        return right ? MAIN_WIDTH - x - EXTRA_SIZE : x;
    }

    public static int extraY(Slot slot) {
        if (!slot.extra()) throw new IllegalArgumentException("Expected an extra-skill slot");
        return slot == Slot.LOWER ? LOWER_Y : UPPER_Y;
    }

    /** Growing above the pair must not change its scale pivot when a Perk is learned. */
    public static HudScaleGeometry.View scaledView(Box source, int percent, int width, int height, boolean right, int extras) {
        var fitted = HudScaleGeometry.place(source, percent, width, height, right);
        var extent = bounds(extras, right);
        float scale = fitted.scale();
        int x = right ? source.x() + source.width() - fitted.bounds().width()
                : source.x() - extent.x() + (int) Math.floor(extent.x() * scale);
        int y = source.y() - extent.y() + (int) Math.floor(extent.y() * scale);
        x = Math.max(0, Math.min(Math.max(0, width - fitted.bounds().width()), x));
        y = Math.max(0, Math.min(Math.max(0, height - fitted.bounds().height()), y));
        return new HudScaleGeometry.View(source, new Box(x, y, fitted.bounds().width(), fitted.bounds().height()), scale);
    }

    /** Clamp the complete panel while preserving the old main-panel anchor and mirroring rule. */
    public static Origin place(int x, int y, boolean right, int width, int height, int extras) {
        x = Math.max(0, Math.min(Math.max(0, width - MAIN_WIDTH), x));
        if (right) x = Math.max(0, width - x - MAIN_WIDTH);
        Box b = bounds(extras, right);
        int left = Math.max(0, Math.min(Math.max(0, width - b.width()), x + b.x()));
        int top = Math.max(0, Math.min(Math.max(0, height - b.height()), y + b.y()));
        return new Origin(left - b.x(), top - b.y());
    }
}
