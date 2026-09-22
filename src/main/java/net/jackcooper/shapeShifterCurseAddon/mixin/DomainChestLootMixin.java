package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.ItemScatterer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootableContainerBlockEntity.class)
public abstract class DomainChestLootMixin {
	@WrapOperation(method = "checkLootInteraction", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/loot/LootTable;supplyInventory(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/loot/context/LootContextParameterSet;J)V"))
	private void ssca$domainLoot(LootTable table, Inventory inventory, LootContextParameterSet parameters,
	                             long seed, Operation<Void> original) {
		original.call(table, inventory, parameters, seed);
		if (!((Object) this instanceof ChestBlockEntity chest) || chest.getWorld() == null) return;
		World world = chest.getWorld();
		// 末地 2%：领域（空间系红色）；下界 3%：爆裂魔法（烈焰系红色，2026-09-22）。
		// ScrollData.create 单参重载钳制 level=1，红色单级 NBT 天然合法。
		String spell = null;
		if (world.getRegistryKey().equals(World.END) && world.random.nextFloat() < 0.02f) {
			spell = "domain";
		} else if (world.getRegistryKey().equals(World.NETHER) && world.random.nextFloat() < 0.03f) {
			spell = "explosion";
		}
		if (spell == null) return;
		var scroll = ScrollData.create(spell);
		var empty = new java.util.ArrayList<Integer>();
		for (int slot = 0; slot < inventory.size(); slot++) if (inventory.getStack(slot).isEmpty()) empty.add(slot);
		if (!empty.isEmpty()) inventory.setStack(empty.get(world.random.nextInt(empty.size())), scroll);
		else ItemScatterer.spawn(world, chest.getPos().getX() + 0.5, chest.getPos().getY() + 1,
				chest.getPos().getZ() + 0.5, scroll);
		chest.markDirty();
	}
}