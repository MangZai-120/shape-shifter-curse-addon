package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class SscAddonEntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    public void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity entity) {
            if (entity.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
                ci.cancel();
            }
        }
    }
}
