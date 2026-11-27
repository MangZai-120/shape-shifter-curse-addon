package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.MerchantSpellTrades;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TradeOfferList.class)
public abstract class LibrarianTradeOfferListMixin implements MerchantSpellTrades.LibrarianOffers {
	@Unique private static final String SSC_ADDON_ROLLED = "ssc_addon:LibrarianScrollsRolled";
	@Unique private boolean sscAddon$librarianScrollsRolled;

	@Override
	public void sscAddon$rollLibrarianScrolls(Random random) {
		if (sscAddon$librarianScrollsRolled) return;
		sscAddon$librarianScrollsRolled = true;
		MerchantSpellTrades.addLibrarianOffers((TradeOfferList) (Object) this, random);
	}

	// Keep even an empty roll with its offers across saves and zombie curing.
	@Inject(method = "<init>(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("RETURN"))
	private void sscAddon$readLibrarianRoll(NbtCompound nbt, CallbackInfo ci) {
		sscAddon$librarianScrollsRolled = nbt.getBoolean(SSC_ADDON_ROLLED);
	}

	@Inject(method = "toNbt", at = @At("RETURN"))
	private void sscAddon$writeLibrarianRoll(CallbackInfoReturnable<NbtCompound> cir) {
		if (sscAddon$librarianScrollsRolled) cir.getReturnValue().putBoolean(SSC_ADDON_ROLLED, true);
	}
}
