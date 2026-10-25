package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import net.jackcooper.shapeShifterCurseAddon.compat.SnowFoxSpPerks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReachEntityAttributes.class)
public abstract class SnowFoxSpReachMixin {
    // The library's reach attribute is an additive bonus, normally zero. Scale its complete
    // result once on both sides, rather than multiplying only that zero-valued attribute.
    @Inject(method = "getReachDistance", at = @At("RETURN"), cancellable = true, remap = false)
    private static void ssca$elusivePaws(LivingEntity entity, double baseReachDistance,
                                       CallbackInfoReturnable<Double> cir) {
        if (entity instanceof PlayerEntity player && SnowFoxSpPerks.hasElusivePaws(player)) {
            cir.setReturnValue(cir.getReturnValue() * 1.5);
        }
    }
}
