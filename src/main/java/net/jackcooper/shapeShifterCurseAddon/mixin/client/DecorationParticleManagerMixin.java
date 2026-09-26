package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.shedaniel.autoconfig.AutoConfig;
import net.jackcooper.shapeShifterCurseAddon.client.particle.FirstPersonParticles;
import net.jackcooper.shapeShifterCurseAddon.client.particle.OwnedDecoration;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleAvoidance;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleOpacity;
import net.jackcooper.shapeShifterCurseAddon.client.particle.AsyncParticleCompatibility;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleRenderRouting;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleManager.class)
public abstract class DecorationParticleManagerMixin {
    @Shadow @Final private Map<ParticleTextureSheet, Queue<Particle>> particles;
    @Unique private Map<ParticleTextureSheet, Queue<Particle>> ssca$frameParticles;

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private void ssca$tag(ParticleEffect effect, double x, double y, double z,
                          double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        FirstPersonParticles.tag(cir.getReturnValue());
    }

    @WrapOperation(method = "tickParticle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void ssca$inheritDecoration(Particle particle, Operation<Void> original) {
        var owner = particle instanceof net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleOwnership owned
                ? owned.ssca$getDecorationOwner() : null;
        if (owner == null) original.call(particle);
        else if (particle instanceof net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleOwnership own
                && own.ssca$isProjectileDecoration())
            // 子粒子继承弹道标记（火球拖尾的扩散子粒子同样豁免锥压制）
            net.jackcooper.shapeShifterCurseAddon.client.particle.FirstPersonParticles.emitProjectile(owner, () -> original.call(particle));
        else FirstPersonParticles.emit(owner, () -> original.call(particle));
    }

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void ssca$prepareTranslucentBatch(MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                              LightmapTextureManager lightmap, Camera camera, float tickDelta,
                                              CallbackInfo ci) {
        ssca$frameParticles = null;
        AsyncParticleCompatibility.end();
        var client = MinecraftClient.getInstance();
        if (client.player == null || camera.getFocusedEntity() != client.player || camera.isThirdPerson()
                || !client.options.getPerspective().isFirstPerson()
                || AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig().firstPersonParticleAvoidance
                    == ParticleAvoidance.Strength.OFF) return;
        AsyncParticleCompatibility.begin(camera, tickDelta);
        ssca$frameParticles = ParticleRenderRouting.forFrame(particles,
                ParticleTextureSheet.PARTICLE_SHEET_OPAQUE, ParticleTextureSheet.PARTICLE_SHEET_LIT,
                ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT,
                p -> p instanceof OwnedDecoration owned && owned.ssca$cameraVisibility(camera, tickDelta) < 1);
    }

    @ModifyExpressionValue(method = "renderParticles", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/particle/ParticleManager;particles:Ljava/util/Map;"))
    private Map<ParticleTextureSheet, Queue<Particle>> ssca$renderQueues(Map<ParticleTextureSheet, Queue<Particle>> original) {
        return ssca$frameParticles == null ? original : ssca$frameParticles;
    }

    // Remains outside BillboardParticle even when Sodium overwrites its entire renderer.
    // Vanilla's VertexConsumer and Sodium's packed colour path both read Particle.alpha here.
    @WrapOperation(method = "renderParticles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"))
    private void ssca$drawWithOpacity(Particle particle, VertexConsumer vertices, Camera camera,
                                       float tickDelta, Operation<Void> original) {
        if (!(particle instanceof OwnedDecoration decoration)) {
            original.call(particle, vertices, camera, tickDelta);
            return;
        }
        float visibility = decoration.ssca$cameraVisibility(camera, tickDelta);
        if (visibility >= 1) {
            original.call(particle, vertices, camera, tickDelta);
            return;
        }
        ParticleOpacity.draw(decoration, visibility, () -> original.call(particle, vertices, camera, tickDelta));
    }

    @Inject(method = "renderParticles", at = @At("RETURN"))
    private void ssca$clearRenderQueues(MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                        LightmapTextureManager lightmap, Camera camera, float tickDelta,
                                        CallbackInfo ci) {
        ssca$frameParticles = null;
        AsyncParticleCompatibility.end();
    }
}
