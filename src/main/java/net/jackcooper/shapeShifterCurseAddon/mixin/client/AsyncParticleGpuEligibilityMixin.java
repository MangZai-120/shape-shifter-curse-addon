package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleOwnership;
import net.minecraft.client.particle.SpriteBillboardParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GPU extraction bypasses ParticleManager's per-frame opacity hook in both AsyncParticles layouts. */
@Pseudo
@Mixin(targets = {
        "fabric.fun.qu_an.minecraft.asyncparticles.client.particle.GpuParticleBehavior",
        "fabric.fun.qu_an.minecraft.asyncparticles.client.core.particle.gpu_acceleration.GpuParticleBehavior"
}, remap = false)
public abstract class AsyncParticleGpuEligibilityMixin {
    // Class literals in Desc remap for production while the optional mod's method name stays intact.
    // The newer Particle overload delegates to this SpriteBillboardParticle overload.
    @Inject(target = @Desc(value = "canRenderFast", args = SpriteBillboardParticle.class, ret = boolean.class),
            at = @At("HEAD"), cancellable = true, require = 1)
    private void ssca$useOwnedParticleRenderer(SpriteBillboardParticle particle, CallbackInfoReturnable<Boolean> cir) {
        // Per-instance decision before the class cache: unowned flames still use GPU rendering.
        if (particle instanceof ParticleOwnership owned && owned.ssca$getDecorationOwner() != null) {
            cir.setReturnValue(false);
        }
    }
}
