/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 凋零药水按形态分档堆叠：使魔系（使魔SP / 进化使魔 / 契灵 / 红堕落使魔）叠 8；
 * SP阿努比斯叠 3；其它形态保持默认（物品 maxCount=1，不可叠）。
 * 仿原版 PotionStackMixin：在 {@code Slot.getMaxItemCount(ItemStack)} 的 RETURN 处，
 * 根据槽位所属玩家的形态抬高堆叠上限。仅影响 GUI 槽位堆叠。
 */
@Mixin(Slot.class)
public abstract class WitherPotionStackMixin {

	@Shadow
	@Final
	public Inventory inventory;

	@Inject(method = "getMaxItemCount(Lnet/minecraft/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void ssc_addon$witherPotionFormStack(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (!(this.inventory instanceof PlayerInventory playerInventory)) {
			return;
		}
		if (!(stack.getItem() instanceof WitherPotionItem)) {
			return;
		}
		int limit = WitherPotionItem.getStackLimitFor(playerInventory.player);
		if (limit > 1) {
			cir.setReturnValue(Math.max(cir.getReturnValue(), limit));
		}
	}
}
