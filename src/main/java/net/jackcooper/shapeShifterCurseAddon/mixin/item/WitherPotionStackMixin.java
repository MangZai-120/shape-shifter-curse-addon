/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem;
import net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附属药水按形态/power 分档堆叠（仿原版 PotionStackMixin）：
 * ① 凋零药水按形态分档：使魔系（使魔SP / 进化使魔 / 契灵 / 红堕落使魔）叠 8；SP阿努比斯叠 3；其它保持默认。
 * ② 通用能量药水按 power 分档（与原版药水叠放同源）：默认叠 1（物品 maxCount=1），
 * 持有 {@link ModifyPotionStackPower}（非水瓶限定，如使魔系的 modify_potion_stack_8）的形态叠 N。
 * 在 {@code Slot.getMaxItemCount(ItemStack)} 的 RETURN 处根据槽位所属玩家抬升堆叠上限。
 * 仅影响 GUI 槽位堆叠（含双击合并 / shift 移动）；地面捡拾/箱子同原版药水仍为 1/格。
 */
@Mixin(Slot.class)
public abstract class WitherPotionStackMixin {

	@Shadow
	@Final
	public Inventory inventory;

	@Inject(method = "getMaxItemCount(Lnet/minecraft/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void ssc_addon$addonPotionStack(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (!(this.inventory instanceof PlayerInventory playerInventory)) {
			return;
		}
		if (stack.getItem() instanceof WitherPotionItem) {
			int limit = WitherPotionItem.getStackLimitFor(playerInventory.player);
			if (limit > 1) {
				cir.setReturnValue(Math.max(cir.getReturnValue(), limit));
			}
			return;
		}
		if (stack.getItem() instanceof UniversalEnergyPotionItem) {
			// 与原版药水叠放同源：取非水瓶限定 power 的最大 count（未持有则保持默认 1）
			int limit = PowerHolderComponent.getPowers(playerInventory.player, ModifyPotionStackPower.class)
					.stream()
					.filter(power -> !power.isOnlyWaterPotion())
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(1);
			if (limit > 1) {
				cir.setReturnValue(Math.max(cir.getReturnValue(), limit));
			}
		}
	}
}
