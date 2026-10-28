package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.function.Supplier;

/** Scoped to one synchronous cast callback; never modifies shared Spell or config instances. */
public final class RuneCastContext {
    private static final ThreadLocal<RuneModifiers> CURRENT = ThreadLocal.withInitial(() -> RuneModifiers.NONE);
    private static final ThreadLocal<Double> FORMATION_RANGE = ThreadLocal.withInitial(() -> 1d);
    private RuneCastContext() {}
    public static RuneModifiers current() { return CURRENT.get(); }
    public static double formationRange() { return FORMATION_RANGE.get(); }
    public static <T> T with(RuneModifiers modifiers,double formationRange,Supplier<T> action) {
        if(formationRange==1d&&FORMATION_RANGE.get()==1d)return with(modifiers,action);
        double previous=FORMATION_RANGE.get();FORMATION_RANGE.set(formationRange);
        try { return with(modifiers,action); }
        finally { if(previous==1d)FORMATION_RANGE.remove();else FORMATION_RANGE.set(previous); }
    }
    public static void run(RuneModifiers modifiers,double formationRange,Runnable action) {
        with(modifiers,formationRange,()->{action.run();return null;});
    }
    public static <T> T with(RuneModifiers modifiers, Supplier<T> action) {
        RuneModifiers previous = CURRENT.get(); CURRENT.set(modifiers);
        try { return action.get(); } finally { if (previous == RuneModifiers.NONE) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static void run(RuneModifiers modifiers, Runnable action) { with(modifiers, () -> { action.run(); return null; }); }
}
