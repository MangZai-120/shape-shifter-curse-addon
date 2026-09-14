package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PocketSpaceSpell extends Spell {
	public PocketSpaceSpell() {
		super(new Identifier("ssc_addon", "pocket_space"), SpellRarity.WHITE);
	}

	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		return PocketSpaceManager.canEnter(caster);
	}

	@Override
	public boolean prepareScroll(ServerPlayerEntity caster, ItemStack scroll) {
		if (!canCast(caster) || PocketSpaceStorage.bind(caster.getServer(), scroll) == null) {
			PocketSpaceManager.message(caster, "unavailable");
			return false;
		}
		return true;
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {}

	@Override
	public void onCooldownStarted(ServerPlayerEntity caster, Runnable cancelRefund) {
		PocketSpaceManager.setCancelRefund(caster, cancelRefund);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level, ItemStack scroll) {
		PocketSpaceManager.start(caster, scroll.getNbt().getUuid(PocketSpaceStorage.SCROLL_ID),
				getBaseCastTimeTicks());
	}

	@Override
	public String getInBookTooltipKey() { return "item.ssc_addon.magic_scroll.tip_in_book_pocket"; }

	@Override
	public String getSoloTooltipKey() { return "item.ssc_addon.magic_scroll.tip_solo_pocket"; }
}