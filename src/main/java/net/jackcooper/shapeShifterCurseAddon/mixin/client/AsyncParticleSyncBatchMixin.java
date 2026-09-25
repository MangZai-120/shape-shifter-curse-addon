package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.particle.AsyncParticleCompatibility;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

/** Async rendering maintains its own sync sets; mirror the normal queue's temporary translucent routing. */
@Pseudo
@Mixin(targets = "fabric.fun.qu_an.minecraft.asyncparticles.client.particle.AsyncRenderBehavior", remap = false)
public abstract class AsyncParticleSyncBatchMixin {
    @Shadow @Final private Map<ParticleTextureSheet, Set<Particle>> SYNC_PARTICLES;

    @Inject(method = "getSync", at = @At("RETURN"), cancellable = true, require = 0)
    private void ssca$syncBatch(ParticleTextureSheet sheet, CallbackInfoReturnable<Set<Particle>> cir) {
        cir.setReturnValue(AsyncParticleCompatibility.syncBatch(SYNC_PARTICLES, sheet, cir.getReturnValue()));
    }
}
