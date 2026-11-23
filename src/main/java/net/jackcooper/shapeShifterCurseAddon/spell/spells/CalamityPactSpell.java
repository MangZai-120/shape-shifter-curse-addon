package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.CalamityPact;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class CalamityPactSpell extends Spell {
    public CalamityPactSpell() {
        super(new Identifier("ssc_addon", "calamity_pact"), SpellRarity.WHITE);
    }

    @Override public boolean canCast(ServerPlayerEntity caster) {
        return CalamityPact.hasSummon(caster);
    }

    @Override public Text getCastFailureMessage(ServerPlayerEntity caster) {
        return Text.translatable("message.ssc_addon.calamity_pact.no_summons");
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo) {
        cast(caster, power, solo, 1);
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
        int tier = Math.max(1, Math.min(getMaxLevel(), level));
        int duration = RuneCastContext.current().duration(20 * (6 + tier), RuneModifiers.Stat.ALLY_DURATION);
        CalamityPact.start(caster, duration, (2 + tier) / 10.0F);
    }
}
