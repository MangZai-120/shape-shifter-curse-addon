package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class PortableBeaconInventoryMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void ssca$checkCarriedBeacon(ItemStack stack, World world, Entity holder, int slot,
                                         boolean selected, CallbackInfo ci) {
        if ((Object) this == Items.BEACON && holder instanceof ServerPlayerEntity player) {
            AllaySPPortableBeacon.clearStaleCarriedMarker(player, stack);
        }
    }
}
