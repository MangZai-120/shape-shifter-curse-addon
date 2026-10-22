package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/** 霜甲在共享吸收池中尚未消耗的份额。减量先消耗霜甲，其他来源新增的吸收量不归霜甲。 */
public final class FrostArmorState {
    private static final String KEY = "SSCA.FrostArmorRemaining";
    private float remaining;
    private boolean removingOther;

    public float remaining() { return remaining; }

    public void onAbsorptionChanged(float before, float after) {
        if (!removingOther && after < before) remaining = Math.max(0, remaining - (before - after));
    }

    /** 原版吸收效果到期时只能扣它的份额，不能吞掉独立计时的霜甲。 */
    public void removeOther(float target, java.util.function.Consumer<Float> update) {
        boolean previous = removingOther;
        removingOther = true;
        try { update.accept(Math.max(remaining, target)); }
        finally { removingOther = previous; }
    }

    /** 已知独立来源的到期/衰减入口；未施加霜甲时保持原有减量行为。 */
    public static void removeOther(LivingEntity entity, float amount) {
        float target = Math.max(0, entity.getAbsorptionAmount() - amount);
        if (entity instanceof FrostArmorAccess access) access.ssca$frostArmor().removeOther(target, entity::setAbsorptionAmount);
        else entity.setAbsorptionAmount(target);
    }

    /** 补至目标值；刷新不叠加，不压低已有更高的吸收量。 */
    public void grant(LivingEntity entity, float power) {
        if (!Float.isFinite(power) || power <= 0) return;
        float before = entity.getAbsorptionAmount();
        float target = Math.max(before, power);
        remaining += target - before;
        entity.setAbsorptionAmount(target);
    }

    public void remove(LivingEntity entity) {
        float owned = remaining;
        remaining = 0; // setAbsorptionAmount 的减量钩子不能重复结算这一份额
        if (owned > 0) entity.setAbsorptionAmount(Math.max(0, entity.getAbsorptionAmount() - owned));
    }

    public static void removeFrom(LivingEntity entity) {
        if (entity instanceof FrostArmorAccess access) access.ssca$frostArmor().remove(entity);
    }

    public void write(NbtCompound nbt) {
        if (remaining > 0) nbt.putFloat(KEY, remaining);
        else nbt.remove(KEY);
    }

    public void read(NbtCompound nbt, LivingEntity entity, boolean active) {
        float saved = nbt.getFloat(KEY);
        remaining = Float.isFinite(saved) ? Math.max(0, Math.min(saved, entity.getAbsorptionAmount())) : 0;
        if (!active) remove(entity);
    }
}
