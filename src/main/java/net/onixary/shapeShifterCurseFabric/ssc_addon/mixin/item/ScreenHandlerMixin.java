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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
	 * 让「药水可叠加」形态(持有 {@link ModifyPotionStackPower})的玩家在任意容器界面操作药水时，
	 * 把 {@code internalOnSlotClick} 内用 {@link ItemStack#getMaxCount()}(药水原版=1)判定的上限改为 N。
	 * 主要修复创造模式「物品栏」标签页 / 中键复制(CLONE) 等不走 {@code Slot.getMaxItemCount} 的路径，
	 * 使其与生存物品栏(原版 PotionStackMixin)一致叠到 N。仅对药水且持有该 Power 时生效，
	 * 双端安全(用方法参数 player，不引用客户端类)。
	 */
	@Redirect(
			method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I")
	)
	private int ssc_addon$potionStackLimit(ItemStack stack, int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (stack.getItem() instanceof PotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(0);
			if (n > 0) {
				return Math.max(n, stack.getMaxCount());
			}
		}
		return stack.getMaxCount();
	}
}
