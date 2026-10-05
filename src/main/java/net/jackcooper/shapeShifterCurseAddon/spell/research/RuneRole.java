package net.jackcooper.shapeShifterCurseAddon.spell.research;

/** Semantic identities are server-owned; a glyph is only an index into the world's language. */
public enum RuneRole {
    FIRE, ICE, LUNAR, CURSE, SUMMON, VOID, SPACE,
    SOURCE, GAIN, STABLE, SPLIT, MERGE, BRIDGE, CONVERT, STORE, INSIGHT, RECOVER, DISABLE;

    public boolean element() { return ordinal() < 7; }
    public boolean effect() { return this == GAIN || this == CONVERT || this == STORE || this == INSIGHT || this == RECOVER; }
    public String key() { return "research.ssc_addon.rune." + name().toLowerCase(java.util.Locale.ROOT); }
}
