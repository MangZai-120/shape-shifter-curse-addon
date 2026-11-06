package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorAccess;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/** The owned absorption budget travels with the target; the persisted status effect owns its timer. */
public final class ParasiticAbsorptionState {
    private static final String KEY = "SSCA.ParasiticAbsorptionRemaining";
    private static final String END_KEY = "SSCA.ParasiticAbsorptionEndTick";
    private float remaining;
    private long endTick = Long.MAX_VALUE;
    private boolean removingOther;

    public float remaining() { return remaining; }
    public void refresh(long until) { endTick = until; }
    public boolean expired(long now) { return now >= endTick; }

    public void duringOtherRemoval(Runnable operation) {
        boolean previous = removingOther;
        removingOther = true;
        try { operation.run(); }
        finally { removingOther = previous; }
    }

    public void grant(LivingEntity entity, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        remaining += amount;
        entity.setAbsorptionAmount(entity.getAbsorptionAmount() + amount);
    }

    public void onChanged(LivingEntity entity, float before, float after) {
        // Frost armor owns the first consumed slice of the shared pool.
        float frost = entity instanceof FrostArmorAccess access ? access.ssca$frostArmor().remaining() : 0;
        if (!removingOther && after < before) remaining = Math.max(0, remaining - Math.max(0, before - after - frost));
    }

    public void remove(LivingEntity entity) {
        float owned = remaining;
        remaining = 0;
        if (owned > 0) FrostArmorState.removeOther(entity, owned);
    }

    public void write(NbtCompound nbt) {
        if (remaining > 0) nbt.putFloat(KEY, remaining);
        else nbt.remove(KEY);
        if (remaining > 0) nbt.putLong(END_KEY, endTick);
        else nbt.remove(END_KEY);
    }

    public void read(NbtCompound nbt, LivingEntity entity) {
        float saved = nbt.getFloat(KEY);
        remaining = Float.isFinite(saved) ? Math.max(0, Math.min(saved, entity.getAbsorptionAmount())) : 0;
        endTick = nbt.contains(END_KEY) ? nbt.getLong(END_KEY) : Long.MAX_VALUE;
    }
}
