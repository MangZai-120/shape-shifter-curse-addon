package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.List;

/** Shared pixel-atlas and column-first layout; independent of combat and rendering state. */
public final class AnomalyBadgeLayout {
    public static final int PIXELS = 32;
    public static final int ROWS = 4;
    public static final int RING_STEPS = 500;
    public static final int ATLAS_COLUMNS = 32;
    public static final int ATLAS_ROWS = 16;
    public static final int BACKGROUND_TILE = 501;
    public static final float SIZE = .20f;
    public static final float STEP = .23f;
    public static final float MODEL_GAP = .12f;
    public static final double MAX_DISTANCE_SQUARED = 32 * 32;

    public record Element(String id, int color, int glyphTile) {}

    // Fixed order prevents the badges from changing places when their amounts change.
    public static final List<Element> ELEMENTS = List.of(
            new Element("fire", 0xBD1641, 502),
            new Element("ice", 0x55DAEA, 503),
            new Element("lunar", 0xE6DAA5, 504),
            new Element("curse", 0xB570D6, 505),
            new Element("summon", 0x76CBA4, 506),
            new Element("void", 0x7E78CF, 507),
            new Element("space", 0x83ACF6, 508));

    private AnomalyBadgeLayout() {}

    public static int ringTile(double amount, double maximum) {
        if (!Double.isFinite(amount) || !Double.isFinite(maximum) || amount <= 0 || maximum <= 0) return 0;
        return (int) Math.round(Math.min(1, amount / maximum) * RING_STEPS);
    }

    public static float x(int slot) { return slot / ROWS * STEP; }
    public static float y(int slot) { return -(slot % ROWS) * STEP; }

    /** Humanoid shoulders/arms can extend past the narrow collision box. */
    public static float bodyRadius(float width, float height) { return Math.max(width * .5f, height * .30f); }

    /** Current hit points, not percentage of maximum health: 5 HP -> 30%, 50 HP -> 100%. */
    public static float healthScale(float health) {
        if (health <= 5) return .30f;
        if (health >= 50 || !Float.isFinite(health)) return 1;
        return .30f + (health - 5) / 45 * .70f;
    }

    public static float displayScale(boolean player, float health) { return player ? 1 : healthScale(health); }

    /** Small forms and swimming players must not put the lower rows below their feet. */
    public static float top(float bodyHeight, int count) {
        return top(bodyHeight, count, 1);
    }

    public static float top(float bodyHeight, int count, float scale) {
        float minimum = (Math.max(0, Math.min(ROWS, count) - 1) * STEP + SIZE * .5f) * scale + .04f;
        return Math.max(bodyHeight * .80f, minimum);
    }

    public static float u(int tile) { return (tile % ATLAS_COLUMNS) / (float) ATLAS_COLUMNS; }
    public static float v(int tile) { return (tile / ATLAS_COLUMNS) / (float) ATLAS_ROWS; }
}
