package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.util.TrinketUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value=TrinketUtils.class,remap=false)
public class DualAccessoryPowersMixin {
    @Inject(method="getAllAccessory",at=@At("HEAD"),cancellable=true)
    private static void ssca$effective(PlayerEntity player,CallbackInfoReturnable<List<Pair<AccessoryItem.SlotData,ItemStack>>> cir) {
        cir.setReturnValue(DualAccessoryEquipment.all(player));
    }
}
