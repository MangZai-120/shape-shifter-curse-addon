package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$onSetTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null && target.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
            // Prevent mob from optimizing onto the player
            // System.out.println("SSC ADDON DEBUG: Mob " + ((MobEntity)(Object)this).getName().getString() + " ignored Playing Dead target!");
            ci.cancel();
        }
    }
}
