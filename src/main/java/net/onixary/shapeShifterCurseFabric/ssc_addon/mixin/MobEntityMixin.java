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
        if (target != null) {
            if (target.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
                // Prevent mob from optimizing onto the player
                ci.cancel();
            }
            if (target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
                // Prevent mob from optimizing onto the invisible player
                ci.cancel();
            }
        }
    }

    @Inject(method = "mobTick", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$onMobTick(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object)this;
        
        // 1. Stun Logic
        if (mob.hasStatusEffect(SscAddon.STUN)) {
            // Disable AI goals/logic while stunned, but allow basic physics (in tick/travel) to run
            ci.cancel();
            return;
        }
        
        // 2. Continuous Aggression Drop for True Invisibility
        // If the mob is currently targeting someone who is invisible, forget them immediately.
        LivingEntity target = mob.getTarget();
        if (target != null && target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
            mob.setTarget(null);
        }
    }
}
