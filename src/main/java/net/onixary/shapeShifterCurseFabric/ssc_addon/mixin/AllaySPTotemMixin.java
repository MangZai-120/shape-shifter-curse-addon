package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 2000)
public class AllaySPTotemMixin {
    
    // VirtualTotemMixin uses priority 10.
    // Higher priority runs earlier, so we can check and return true (cancel death) before Anubis logic runs.
    
    @Inject(method = "tryUseTotem", at = @At("RETURN"), cancellable = true)
    private void tryUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (AllaySPTotem.tryUseAllayTotem(entity)) {
                cir.setReturnValue(true);
            }
        }
    }
}
