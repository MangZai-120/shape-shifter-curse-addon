package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.function.IntPredicate;

/** One locked mana quote, paid every two preparation ticks without losing integer remainders. */
public final class SpellManaPayment {
    public static final int INTERVAL_TICKS = 2;
    private final int total;
    private final int duration;
    private int paid;

    public SpellManaPayment(int total, int duration) {
        this.total = Math.max(0, total);
        this.duration = Math.max(0, duration);
    }

    public int paid() { return paid; }

    public boolean advance(int elapsed, IntPredicate consume) {
        int tick = Math.max(0, Math.min(elapsed, duration));
        // Odd-duration and instant casts settle exactly at completion; holding release cannot repay.
        int billedTick = tick == duration ? duration : tick / INTERVAL_TICKS * INTERVAL_TICKS;
        int target = SpellCastingRules.cumulativeMana(total, billedTick, duration);
        int due = target - paid;
        if (due <= 0) return true;
        if (!consume.test(due)) return false;
        paid = target;
        return true;
    }
}
