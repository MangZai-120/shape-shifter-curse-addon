package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.function.Supplier;

/** Scoped to one synchronous cast callback; never modifies shared Spell or config instances. */
public final class RuneCastContext {
    private static final ThreadLocal<RuneModifiers> CURRENT = ThreadLocal.withInitial(() -> RuneModifiers.NONE);
    private RuneCastContext() {}
    public static RuneModifiers current() { return CURRENT.get(); }
    public static <T> T with(RuneModifiers modifiers, Supplier<T> action) {
        RuneModifiers previous = CURRENT.get(); CURRENT.set(modifiers);
        try { return action.get(); } finally { if (previous == RuneModifiers.NONE) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static void run(RuneModifiers modifiers, Runnable action) { with(modifiers, () -> { action.run(); return null; }); }
}
