package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers.Stat.*;

/** Each additional resonance grants and charges one immutable package per enhancement ring. */
public enum RuneResonance {
    BURST("gain_store", RuneRole.GAIN, RuneRole.STORE, EnumSet.of(POWER, DAMAGE),
            Map.of(DAMAGE, 12, TIME, 8, CD, 15), 8),
    PROJECTILE("source_store", RuneRole.SOURCE, RuneRole.STORE, EnumSet.of(DAMAGE, SPEED),
            Map.of(DAMAGE, 6, SPEED, 5, CD, 10), 6),
    PRESSURE("split_convert", RuneRole.SPLIT, RuneRole.CONVERT, EnumSet.of(AREA, NEGATIVE),
            Map.of(AREA, 8, NEGATIVE, 10, MANA, 10, CD, 12), 6),
    MENDING("recover_merge", RuneRole.RECOVER, RuneRole.MERGE, EnumSet.of(POWER, HEAL),
            Map.of(HEAL, 12, TIME, 4, CD, 10), 6);

    private final String id;
    private final RuneRole first, second;
    private final EnumSet<RuneModifiers.Stat> required;
    private final RuneModifiers modifiers;
    private final int load;

    RuneResonance(String id, RuneRole first, RuneRole second, EnumSet<RuneModifiers.Stat> required,
                  Map<RuneModifiers.Stat, Integer> changes, int load) {
        this.id = id; this.first = first; this.second = second; this.required = required.clone(); this.load = load;
        int[] values = new int[RuneModifiers.Stat.values().length];
        changes.forEach((stat, value) -> values[stat.ordinal()] = value);
        modifiers = new RuneModifiers(values);
    }
    public String id() { return id; }
    public RuneModifiers modifiers() { return modifiers; }
    public int load() { return load; }
    public boolean matches(RuneRole a, RuneRole b) { return a == first && b == second || a == second && b == first; }
    public boolean supports(Set<RuneModifiers.Stat> capabilities) { return capabilities.containsAll(required); }
}
