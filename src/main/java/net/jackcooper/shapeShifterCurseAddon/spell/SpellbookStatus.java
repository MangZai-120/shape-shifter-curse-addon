package net.jackcooper.shapeShifterCurseAddon.spell;

/** Read-only server deadlines; mana itself continues to use the synchronized book NBT. */
public record SpellbookStatus(long naturalRegenAt, long swapReadyAt, long castReadyAt,
                             boolean casting, int naturalRegenPerSecond) {
    public long naturalRegenWait(long now) { return remaining(naturalRegenAt, now); }
    public long swapWait(long now) { return remaining(swapReadyAt, now); }
    public long castWait(long now) { return remaining(castReadyAt, now); }
    public boolean naturalRegenAllowed(long now) { return !casting && naturalRegenWait(now) == 0; }

    private static long remaining(long deadline, long now) {
        return deadline == 0 ? 0 : Math.max(0, deadline - now);
    }
}
