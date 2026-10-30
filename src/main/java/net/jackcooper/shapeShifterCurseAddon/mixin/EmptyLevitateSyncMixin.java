package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.LevitatePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native LevitatePower inherits Power's empty serialization/no-op deserialization. */
@Mixin(value = PowerHolderComponent.class, remap = false)
public interface EmptyLevitateSyncMixin {
    @Inject(method = "syncPower", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$omitEmptyLevitateState(Entity entity, PowerType<?> type, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity) || entity.getWorld().isClient || type == null) return;
        var power = PowerHolderComponent.KEY.get(entity).getPower(type);
        // Subclasses may add real state and must keep their normal synchronization.
        if (power != null && power.getClass() == LevitatePower.class) ci.cancel();
    }
}
