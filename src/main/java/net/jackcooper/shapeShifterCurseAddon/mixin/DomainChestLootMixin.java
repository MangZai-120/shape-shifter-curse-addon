package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootableContainerBlockEntity.class)
public abstract class DomainChestLootMixin {
	@WrapOperation(method = "checkLootInteraction", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/loot/LootTable;supplyInventory(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/loot/context/LootContextParameterSet;J)V"))
	private void ssca$domainLoot(LootTable table, Inventory inventory, LootContextParameterSet parameters,
	                             long seed, Operation<Void> original) {
		original.call(table, inventory, parameters, seed);
		if ((Object) this instanceof ChestBlockEntity chest) {
			MagicScrollLoot.rollDimensionScroll(chest, inventory, null);
		}
	}
}
