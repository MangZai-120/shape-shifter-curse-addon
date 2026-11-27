package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lootr fills a player-specific inventory directly. Reopening it skips the table fill. */
@Pseudo
@Mixin(targets = {"net.zestyblaze.lootr.block.entities.LootrChestBlockEntity",
		"noobanidus.mods.lootr.block.entities.LootrChestBlockEntity"}, remap = false)
public abstract class LootrChestLootMixin {
	@Inject(method = "unpackLootTable", remap = false, require = 0,
			at = @At(value = "INVOKE", remap = true, shift = At.Shift.AFTER,
					target = "Lnet/minecraft/loot/LootTable;supplyInventory(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/loot/context/LootContextParameterSet;J)V"))
	private void ssca$onPersonalChestFilled(PlayerEntity player, Inventory inventory, Identifier table, long seed, CallbackInfo ci) {
		MagicScrollLoot.rollDimensionScroll((ChestBlockEntity)(Object)this, inventory, player);
	}
}
