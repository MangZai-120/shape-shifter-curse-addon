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
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleManager.class)
public abstract class DecorationParticleManagerMixin {
    @Shadow @Final private Map<ParticleTextureSheet, Queue<Particle>> particles;
    @Unique private Map<ParticleTextureSheet, Queue<Particle>> ssca$frameParticles;

    // Iris 延迟管线下 renderParticles 每帧被调两次（不透明/半透明 pass），且两次间相机与粒子
    // 队列都不变（渲染帧内不 tick）。帧级复用：相机值 + tick + tickDelta 全等时直接复用上一次
    // 路由结果，把第二遍全粒子扫描 + cameraVisibility 计算全省掉（spark 实测双 pass 各占 ~1.1%）。
    // 必须比对相机「值」而非引用——vanilla 每帧复用同一 Camera 实例原地 update，identity 恒等。
    @Unique private Camera ssca$routingCamera;
    @Unique private Vec3d ssca$routingEye;
    @Unique private float ssca$routingYaw, ssca$routingPitch, ssca$routingDelta;
    @Unique private long ssca$routingTick = Long.MIN_VALUE;
    @Unique private Map<ParticleTextureSheet, Queue<Particle>> ssca$routingResult;

    /** 该次调用的相机状态与上次路由完全一致（同一渲染帧的重复 pass）时返回 true。 */
    @Unique
    private boolean ssca$sameFrameState(Camera camera, float tickDelta) {
        return ssca$routingResult != null
                && ssca$routingTick == client().world.getTime()
                && ssca$routingDelta == tickDelta
                && camera == ssca$routingCamera
                && camera.getYaw() == ssca$routingYaw && camera.getPitch() == ssca$routingPitch
                && camera.getPos().equals(ssca$routingEye);
    }

    @Unique
    private static MinecraftClient client() { return MinecraftClient.getInstance(); }

    // Tag before AsyncParticles chooses a CPU/GPU queue; also covers directly created particles.
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void ssca$tag(Particle particle, CallbackInfo ci) {
        FirstPersonParticles.tag(particle);
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
        var client = client();
        if (client.player == null || camera.getFocusedEntity() != client.player || camera.isThirdPerson()
                || !client.options.getPerspective().isFirstPerson()
                || AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig().firstPersonParticleAvoidance
                    == ParticleAvoidance.Strength.OFF) return;
        AsyncParticleCompatibility.begin(camera, tickDelta);
        // OFF 检查已过：本帧快照一次强度，cameraVisibility 帧内直读（end() 会连同失效）
        AsyncParticleCompatibility.snapshotStrength(
                AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig().firstPersonParticleAvoidance);
        // 同一渲染帧的重复 pass：相机值/队列都没变，直接复用上一 pass 的路由结果
        if (ssca$sameFrameState(camera, tickDelta)) {
            ssca$frameParticles = ssca$routingResult;
            return;
        }
        ssca$routingResult = ParticleRenderRouting.forFrame(particles,
                ParticleTextureSheet.PARTICLE_SHEET_OPAQUE, ParticleTextureSheet.PARTICLE_SHEET_LIT,
                ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT,
                p -> p instanceof OwnedDecoration owned && owned.ssca$cameraVisibility(camera, tickDelta) < 1);
        ssca$frameParticles = ssca$routingResult;
        ssca$routingCamera = camera;
        ssca$routingEye = camera.getPos();
        ssca$routingYaw = camera.getYaw();
        ssca$routingPitch = camera.getPitch();
        ssca$routingDelta = tickDelta;
        ssca$routingTick = client.world.getTime();
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
        // 复用缓存保留到下一次 HEAD：第二 pass 直接命中；下一次真正的渲染帧因相机值变化自然失效
        AsyncParticleCompatibility.end();
    }
}
