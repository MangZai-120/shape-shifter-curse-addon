package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

/** Stable saved and projectile values; AUTO is resolved against the host on impact. */
public enum FruitSelection {
    AUTO("auto"), VINE("vine"), BITTER("bitter"), ROTTEN("rotten"), SOUR("sour");

    private final String key;
    FruitSelection(String key) { this.key = key; }
    public String key() { return key; }
    public FruitSelection next() { return values()[(ordinal() + 1) % values().length]; }
    public static FruitSelection fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : AUTO;
    }
    public static float speed(int ticks) {
        return 0.5f + 0.9f * Math.max(0, Math.min(20, ticks - 10)) / 20.0f;
    }
    public static boolean canRelease(int ticks) { return ticks >= 10; }
}
