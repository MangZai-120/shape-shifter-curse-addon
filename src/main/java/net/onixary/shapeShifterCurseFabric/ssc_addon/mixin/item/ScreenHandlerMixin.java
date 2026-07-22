package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.item.PotionItem;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AllayJukeboxItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PotionBagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

	@Shadow
	public final DefaultedList<Slot> slots = DefaultedList.of();

	@Shadow
	public abstract Slot getSlot(int index);

	@Shadow
	public abstract ItemStack getCursorStack();

	@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
	private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		// 药水可叠加形态：双击(PICKUP_ALL)合并同类药水。原版 PICKUP_ALL 走静态 canInsertItemIntoSlot，
		// 其 getMaxCount 不受本类 internalOnSlotClick 重定向影响(药水原版=1)，导致 2+ 堆叠被跳过、无法合并。
		// 故对持有该 Power 的玩家自行实现合并并取消原版处理，背包与箱子等任意容器界面通用。
		if (actionType == SlotActionType.PICKUP_ALL && ssc_addon$tryMergeStackablePotions(button, player)) {
			ci.cancel();
			return;
		}
		if (slotIndex >= 0 && slotIndex < this.slots.size()) {
			Slot slot = this.getSlot(slotIndex);
			if (slot != null && slot.hasStack()) {
				ItemStack stack = slot.getStack();

				// Potion Bag: 光标拿着药水时放入袋中（左/右键均可，优先非快捷消耗栏），否则锁定不可移动
				if (stack.isOf(SscAddon.POTION_BAG)) {
					if (actionType == SlotActionType.PICKUP) {
						ItemStack cursorStack = this.getCursorStack();
						if (!cursorStack.isEmpty() && PotionBagItem.isStorable(cursorStack)
								&& PotionBagItem.insertIntoBag(stack, cursorStack) > 0) {
							player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F,
									0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
							slot.markDirty();
						}
					}
					ci.cancel();
					return;
				}

				// Block moving Allay Heal Wand
				if (stack.isOf(SscAddon.ALLAY_HEAL_WAND)) {
					ci.cancel();
					return;
				}

				// Allay Jukebox: allow disc charging, block other interactions
				if (stack.isOf(SscAddon.ALLAY_JUKEBOX)) {
					// Check if cursor has a music disc - allow charging
					ItemStack cursorStack = this.getCursorStack();
					// Try to charge the jukebox with the disc
					if (cursorStack != null && !cursorStack.isEmpty() && cursorStack.getItem() instanceof MusicDiscItem &&
							AllayJukeboxItem.tryChargeWithDisc(stack, cursorStack)) {
						// Play charge sound
						player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
								SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
					}
					ci.cancel();
				}
			}
		} else if (actionType == SlotActionType.SWAP && button >= 0 && button < 9) {
			ItemStack hotbarStack = player.getInventory().getStack(button);
			if (hotbarStack.isOf(SscAddon.POTION_BAG) || hotbarStack.isOf(SscAddon.ALLAY_HEAL_WAND) || hotbarStack.isOf(SscAddon.ALLAY_JUKEBOX)) {
				ci.cancel();
			}
		}
	}

	/**
	 * 为「药水可叠加」形态(持有 {@link ModifyPotionStackPower})的玩家自行实现双击合并同类药水，叠到 N。
	 * 与原版双击一致采用两轮收集(先取未满堆叠、保留满堆叠，再取满堆叠)，对 {@code button==0/其它} 取不同遍历方向。
	 * 仅处理光标为药水且持有该 Power 的情形；返回 {@code true} 表示已接管本次 PICKUP_ALL，应取消原版。
	 * 双端一致(用方法参数 player、与原版相同的 takeStackRange/increment 操作，不引用客户端类)。
	 */
	@Unique
	private boolean ssc_addon$tryMergeStackablePotions(int button, PlayerEntity player) {
		ItemStack cursor = this.getCursorStack();
		if (cursor.isEmpty()) {
			return false;
		}
		int maxStack;
		if (cursor.getItem() instanceof PotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream().mapToInt(ModifyPotionStackPower::getCount).max().orElse(0);
			if (n <= 0) {
				return false;
			}
			maxStack = Math.max(n, cursor.getMaxCount());
		} else if (cursor.getItem() instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem) {
			maxStack = net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem.getStackLimitFor(player);
			if (maxStack <= 1) {
				return false;
			}
		} else {
			return false;
		}
		if (cursor.getCount() >= maxStack) {
			return false;
		}
		int start = button == 0 ? 0 : this.slots.size() - 1;
		int step = button == 0 ? 1 : -1;
		for (int pass = 0; pass < 2; ++pass) {
			for (int q = start; q >= 0 && q < this.slots.size() && cursor.getCount() < maxStack; q += step) {
				Slot slot = this.slots.get(q);
				if (!slot.hasStack() || !slot.canTakeItems(player)) {
					continue;
				}
				ItemStack slotStack = slot.getStack();
				if (!ItemStack.canCombine(cursor, slotStack)) {
					continue;
				}
				if (pass == 0 && slotStack.getCount() >= maxStack) {
					continue;
				}
				ItemStack taken = slot.takeStackRange(slotStack.getCount(), maxStack - cursor.getCount(), player);
				cursor.increment(taken.getCount());
			}
		}
		return true;
	}

	/**
	 * 让「药水可叠加」形态(持有 {@link ModifyPotionStackPower})的玩家在任意容器界面操作药水时，
	 * 把 {@code internalOnSlotClick} 内用 {@link ItemStack#getMaxCount()}(药水原版=1)判定的上限改为 N。
	 * 主要修复创造模式「物品栏」标签页 / 中键复制(CLONE) 等不走 {@code Slot.getMaxItemCount} 的路径，
	 * 使其与生存物品栏(原版 PotionStackMixin)一致叠到 N。仅对药水且持有该 Power 时生效，
	 * 双端安全(用方法参数 player，不引用客户端类)。
	 */
	@WrapOperation(
			method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I")
	)
	private int ssc_addon$potionStackLimit(ItemStack stack, Operation<Integer> original, @Local(argsOnly = true) PlayerEntity player) {
		if (stack.getItem() instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem) {
			return Math.max(net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem.getStackLimitFor(player), original.call(stack));
		}
		if (stack.getItem() instanceof PotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(0);
			if (n > 0) {
				return Math.max(n, original.call(stack));
			}
		}
		return original.call(stack);
	}
}
