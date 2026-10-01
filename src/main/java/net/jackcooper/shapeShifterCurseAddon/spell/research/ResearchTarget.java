package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.List;

public record ResearchTarget(int family, int level) {
    public static final List<String> FAMILIES = List.of("fire", "ice", "lunar", "curse", "summon", "void", "space",
            "universal/regen", "universal/mana", "universal/exp", "universal/recovery");
    public ResearchTarget {
        if (family < 0 || family >= FAMILIES.size() || level < 1 || level > 5) throw new IllegalArgumentException("Invalid research target");
    }
    public String id() { return FAMILIES.get(family) + ":" + level; }
    public String element() { return family < 7 ? FAMILIES.get(family) : "universal"; }
    public String variant() { return family < 7 ? null : FAMILIES.get(family).substring(10); }
    public String nameKey() { return family < 7 ? "formation.ssc_addon.element." + element() : "formation.ssc_addon.variant." + variant(); }
    public RuneRole effect() { return switch (family) { case 7 -> RuneRole.CONVERT; case 8 -> RuneRole.STORE; case 9 -> RuneRole.INSIGHT; case 10 -> RuneRole.RECOVER; default -> RuneRole.GAIN; }; }
    public static int family(String element, String variant) {
        return FAMILIES.indexOf("universal".equals(element) ? "universal/" + (variant == null || variant.isEmpty() ? "regen" : variant) : element);
    }
}
