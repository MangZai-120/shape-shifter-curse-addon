package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOfferList;

public final class WanderingSpellTrades {
	private WanderingSpellTrades() {}

	public static void addOffers(TradeOfferList offers, Random random) {
		MerchantSpellTrades.addWanderingOffers(offers, random);
	}
}
