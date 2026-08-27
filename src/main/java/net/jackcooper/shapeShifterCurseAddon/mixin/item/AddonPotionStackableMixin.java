/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem;
import net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 药水类物品统一强制视为「可堆叠」：修复 shift 快速移动不合并的根因（覆盖所有药水）。
 *
 * <p>原版 {@code ScreenHandler.insertItem} 的合并循环入口有 {@code isStackable()} 守卫
 * （实现为 {@code getMaxCount() > 1}），药水类物品 maxCount=1 时整个合并段被直接跳过，
 * {@code Slot.getMaxItemCount} / {@code insertItem} 内的上限注入都无法生效
 * （原版 SSC 的药水叠放同样不覆盖 shift 路径，即此原因）。
 *
 * <p>这里对全部药水（原版 PotionItem 系 + 附属通用能量药水 / 凋零药水）强制返回 true
 * 放行进入合并循环；实际叠放上限仍由 WitherPotionStackMixin（GUI 槽位）与 ScreenHandlerMixin
 * （insertItem / 双击合并 / 创造界面）+ 原版 PotionStackMixin 按形态 / power 抬升——
 * 无资格玩家在这些 wrap 处拿到的上限仍是 1，合并判断 {@code j+k > 1} 不通过，自然不叠、无复制风险。
 * 地面掉落物合并、漏斗等其它 isStackable 调用点因 {@code getMaxCount()=1} 同样不会实际叠放。
 * 无限能量药水（每瓶独立自充能 NBT）不在此列，保持不可叠。
 */
@Mixin(ItemStack.class)
public abstract class AddonPotionStackableMixin {

	@Inject(method = "isStackable", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$potionAlwaysStackable(CallbackInfoReturnable<Boolean> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (self.getItem() instanceof PotionItem
				|| self.getItem() instanceof UniversalEnergyPotionItem
				|| self.getItem() instanceof WitherPotionItem) {
			cir.setReturnValue(true);
		}
	}
}
