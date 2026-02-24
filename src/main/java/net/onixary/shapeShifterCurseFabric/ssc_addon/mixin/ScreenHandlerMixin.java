package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AllayJukeboxItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
		if (slotIndex >= 0 && slotIndex < this.slots.size()) {
			Slot slot = this.getSlot(slotIndex);
			if (slot != null && slot.hasStack()) {
				ItemStack stack = slot.getStack();

				// Block moving Potion Bag
				if (stack.isOf(SscAddon.POTION_BAG)) {
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
					if (cursorStack != null && !cursorStack.isEmpty() && cursorStack.getItem() instanceof MusicDiscItem) {
						// Try to charge the jukebox with the disc
						if (AllayJukeboxItem.tryChargeWithDisc(stack, cursorStack)) {
							// Play charge sound
							player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
									SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
						}
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
}
