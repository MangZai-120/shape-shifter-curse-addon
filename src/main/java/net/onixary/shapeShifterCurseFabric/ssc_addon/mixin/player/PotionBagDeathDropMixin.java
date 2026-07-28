package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.GameRules;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PotionBagDeathDropMixin {

	@Shadow
	public abstract PlayerInventory getInventory();

	/**
	 * 死亡时掉落药水袋内物品（仅当 keepInventory 关闭时）
	 * 1.20.1 yarn：PlayerEntity 覆写了 dropInventory()（method_16078, ()V），
	 * 其内部在 keepInventory 关闭时执行 vanishCursedItems() + inventory.dropAll()。
	 * 在 HEAD 注入：此时物品栏尚未 dropAll，可遍历找到药水袋并掉落其内物品。
	 * 注意：dropEquipment 是 mojmap 名，yarn 名为 dropInventory，二者不可混用。
	 */
	@Inject(method = "dropInventory()V", at = @At("HEAD"))
	private void dropPotionBagItems(CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;

		// Check if keepInventory is enabled
		boolean keepInventory = player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);

		// If keepInventory is enabled, don't drop anything
		if (keepInventory) {
			return;
		}

		// Find potion bag in inventory
		ItemStack potionBag = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(SscAddon.POTION_BAG)) {
				potionBag = stack;
				break;
			}
		}

		// Drop all items from the potion bag
		if (!potionBag.isEmpty() && potionBag.hasNbt()) {
			NbtCompound nbt = potionBag.getNbt();
			if (nbt != null && nbt.contains("Items", 9)) {
				NbtList list = nbt.getList("Items", 10);
				for (int i = 0; i < list.size(); ++i) {
					NbtCompound itemTag = list.getCompound(i);
					ItemStack stack = ItemStack.fromNbt(itemTag);
					if (!stack.isEmpty()) {
						player.dropItem(stack, true, false);
					}
				}
				// Clear the potion bag's items
				nbt.remove("Items");
			}
		}
	}
}
