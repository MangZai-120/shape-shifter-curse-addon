package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public abstract class PortableBeaconSlotMixin {
    @Shadow @Final public Inventory inventory;

    @Inject(method = {"setStack(Lnet/minecraft/item/ItemStack;)V", "setStackNoCallbacks"}, at = @At("HEAD"))
    private void ssca$clearStoredBeacon(ItemStack stack, CallbackInfo ci) {
        if (AllaySPPortableBeacon.isActiveStack(stack)
                && (!(inventory instanceof PlayerInventory playerInventory)
                || !stack.getNbt().getUuid("ssca_beacon_owner").equals(playerInventory.player.getUuid()))) {
            AllaySPPortableBeacon.clearStack(stack);
        }
    }
}
