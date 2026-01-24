package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public abstract class SscPlayerMixin {
    
    @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), ordinal = 2)
    private boolean forceCrit(boolean isCritical) {
        if (((PlayerEntity)(Object)this).hasStatusEffect(SscAddon.GUARANTEED_CRIT)) {
            return true;
        }
        return isCritical;
    }
}
