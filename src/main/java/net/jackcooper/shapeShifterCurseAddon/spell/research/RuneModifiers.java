package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.nbt.NbtCompound;
import java.util.Arrays;

/** Integer percentage points and ticks. Item previews are not trusted by server transactions. */
public final class RuneModifiers {
    public enum Stat { POWER, DAMAGE, AREA, DISTANCE, SPEED, BURN, NEGATIVE, MARK, HEAL,
        SHIELD_DURATION, SUMMON_DURATION, ALLY_DURATION, MANA, TIME, CD, FLAT_MANA }
    public static final RuneModifiers NONE = new RuneModifiers(new int[Stat.values().length]);
    private final int[] values;
    public RuneModifiers(int[] values) {
        if (values.length != Stat.values().length || Arrays.stream(values).anyMatch(v -> v < -10000 || v > 10000))
            throw new IllegalArgumentException("Rune modifiers");
        this.values = values.clone();
    }
    public int get(Stat stat) { return values[stat.ordinal()]; }
    public int[] values() { return values.clone(); }
    public NbtCompound write() { NbtCompound n = new NbtCompound(); n.putIntArray("Values", values); return n; }
    public static RuneModifiers read(NbtCompound n) {
        try { return new RuneModifiers(n.getIntArray("Values")); }
        catch (IllegalArgumentException malformed) { return NONE; }
    }
    public static int rounded(long numerator, long denominator) {
        if (denominator <= 0) throw new IllegalArgumentException("Rune divisor");
        long result = (Math.abs(numerator) * 2 + denominator) / (denominator * 2);
        return Math.toIntExact(numerator < 0 ? -result : result);
    }
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public int rawPower(boolean damage, boolean healing) {
        return get(Stat.POWER) + (damage ? get(Stat.DAMAGE) : 0) + (healing ? get(Stat.HEAL) : 0);
    }
    public int effectivePower(boolean damage, boolean healing) { return clamp(rawPower(damage, healing), -50, 60); }
    public int effective(Stat stat) { return clamp(get(stat), -50, stat == Stat.SPEED ? 30 : 60); }
    public float power(float base, boolean damage, boolean healing) {
        return base * (100 + effectivePower(damage, healing)) / 100f;
    }
    public double scale(double base, Stat stat) {
        return base * (100 + effective(stat)) / 100d;
    }
    public int duration(int base, Stat stat) { return Math.max(1, rounded((long)base * (100 + effective(stat)), 100)); }
    public int duration(int base, Stat first, Stat second) { return Math.max(1, rounded((long)base * (100 + clamp(get(first)+get(second), -50, 60)), 100)); }
    public int mana(int base) { return Math.max(0, rounded((long)base * (100 + get(Stat.MANA)), 100) + get(Stat.FLAT_MANA)); }
    public int time(int base) { return get(Stat.TIME)==0 ? base : Math.max(base > 0 ? 4 : 0, base + get(Stat.TIME)); }
    public int cooldown(int base) { return Math.max(base, rounded((long)base * (100 + get(Stat.CD)), 100)); }
}
