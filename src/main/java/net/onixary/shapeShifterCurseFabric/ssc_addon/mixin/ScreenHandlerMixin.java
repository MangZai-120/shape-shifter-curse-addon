package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.collection.DefaultedList;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Shadow public final DefaultedList<Slot> slots = DefaultedList.of();
    @Shadow public abstract Slot getSlot(int index);

	@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
	private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		if (slotIndex >= 0 && slotIndex < this.slots.size()) {
			Slot slot = this.getSlot(slotIndex);
			if (slot != null && slot.hasStack()) {
				ItemStack stack = slot.getStack();
				if (stack.isOf(SscAddon.POTION_BAG)) {
					ci.cancel();
					return;
				}
			}
		} else if (actionType == SlotActionType.SWAP && button >= 0 && button < 9) {
			ItemStack hotbarStack = player.getInventory().getStack(button);
			if (hotbarStack.isOf(SscAddon.POTION_BAG)) {
				ci.cancel();
			}
		}

	}
}
