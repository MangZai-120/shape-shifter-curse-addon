package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.MerchantSpellTrades.LibrarianOffers;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantEntity.class)
public abstract class LibrarianTradeMixin {
	@Inject(method = "getOffers", at = @At("RETURN"))
	private void sscAddon$addLibrarianScrolls(CallbackInfoReturnable<TradeOfferList> cir) {
		if (!((Object) this instanceof VillagerEntity villager) || villager.getWorld().isClient
				|| villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN) return;

		// Vanilla also calls getOffers() from inside fillRecipes(), before adding its own trades.
		TradeOfferList offers = cir.getReturnValue();
		if (!offers.isEmpty()) ((LibrarianOffers) offers).sscAddon$rollLibrarianScrolls(villager.getRandom());
	}
}
