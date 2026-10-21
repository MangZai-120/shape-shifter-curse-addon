package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** 死亡是一切的终点：虚无系 SPECIAL，统一禁动蓄力，完成后一次性结算。 */
public final class DeathFinaleSpell extends Spell {
    public DeathFinaleSpell() { super(new Identifier("ssc_addon", "death_finale"), SpellRarity.RED); }

    /** 领域内禁放死亡终点（2026-10-21 用户定稿，逐法术开关之一）。 */
    @Override public boolean isForbiddenInsideDomain() {
        return true;
    }

    @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity caster, int level, boolean solo) {
        return new SpellCastingRules.Profile(Math.max(1, getBaseCastTimeTicks()), 0, true);
    }

    @Override public void onChannelStarted(ServerPlayerEntity caster) {
        DeathFinaleManager.begin(caster, Math.max(1, getBaseCastTimeTicks()));
    }

    @Override public void tickChannel(ServerPlayerEntity caster, int level, ItemStack scroll, int ticks) {
        DeathFinaleManager.advance(caster, ticks);
    }

    @Override public boolean canContinueCasting(ServerPlayerEntity caster, ItemStack scroll) {
        return DeathFinaleManager.canContinue(caster);
    }

    @Override public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {
        if (interrupted) DeathFinaleManager.cancel(caster);
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo) {
        DeathFinaleManager.release(caster, power, solo ? null : ssc_addon$getRefundCastId(),
                solo ? 0 : ssc_addon$takePendingExp());
    }

    // 图标走 Spell 基类默认约定路径 textures/gui/spell_icons/death_finale.png（专属立绘已接入）。
}
