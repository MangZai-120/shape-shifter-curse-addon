package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class DomainSpell extends Spell {
	public DomainSpell() { super(new Identifier("ssc_addon", "domain"), SpellRarity.RED); }
	@Override
	protected SpellCastingRules.Profile getCustomCastingProfile(ServerPlayerEntity caster, int level, boolean solo) {
		return new SpellCastingRules.Profile(DomainRules.CHARGE_TICKS, 0, true);
	}
	@Override
	public boolean canCast(ServerPlayerEntity caster) { return DomainManager.canStart(caster); }
	@Override
	public void onChannelStarted(ServerPlayerEntity caster) { DomainManager.begin(caster); }
	@Override
	public boolean canContinueCasting(ServerPlayerEntity caster, ItemStack scroll) { return DomainManager.canContinue(caster); }
	@Override
	public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {
		if (interrupted) DomainManager.remove(caster);
	}
	@Override
	public int getInterruptedCooldown(int cooldown) { return 0; }
	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) { DomainManager.activate(caster); }
	@Override
	public String getInBookTooltipKey() { return "item.ssc_addon.magic_scroll.tip_in_book_domain"; }
}