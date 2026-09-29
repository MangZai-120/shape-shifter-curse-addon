package net.jackcooper.shapeShifterCurseAddon.client.particle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** Uses AsyncParticles' per-particle sync API; unrelated particles keep their GPU/async acceleration. */
public final class AsyncParticleCompatibility {
    private static volatile Method renderSync;
    private static volatile boolean resolved;
    // volatile：getSync 回调可能来自 AsyncParticles 工作线程，避免读到上一帧相机或 end() 后的 null
    private static volatile Camera camera;
    private static volatile float tickDelta;
    private static boolean warned;

    private AsyncParticleCompatibility() {}

    public static void prepare(Particle particle) {
        if (!resolved) resolve();
        Method sync = renderSync;
        if (sync == null) return;
        try {
            // Only rendering needs the CPU path. Keep ticking asynchronous, including bursts.
            sync.invoke(particle);
        } catch (ReflectiveOperationException | LinkageError failure) {
            warn(failure);
        }
    }

    private static synchronized void resolve() {
        if (resolved) return;
        if (FabricLoader.getInstance().isModLoaded("asyncparticles")) {
            try {
                // Resolve after Particle's mixins. 20.1.4.0 removed setTickSync, not setRenderSync.
                renderSync = Particle.class.getMethod("asyncparticles$setRenderSync");
            } catch (ReflectiveOperationException | LinkageError failure) {
                warn(failure);
            }
        }
        // Publish the method before other particle-spawning threads can skip initialization.
        resolved = true;
    }

    public static void begin(Camera currentCamera, float delta) { camera = currentCamera; tickDelta = delta; }

    /** 帧结束/新帧开始：相机与强度快照一并失效（OFF 配置路径不快照，残留旧值会让规避在关档后继续生效）。 */
    public static void end() { camera = null; frameStrength = null; }

    // 帧级规避强度缓存（2026-09-29 性能优化）：cameraVisibility 逐粒子调用时
    // AutoConfig.getConfigHolder(...).getConfig() 的读取链在 spark 档案里占 self 时间近半；
    // 一帧内配置不可能变（改配置要进 GUI，必然跨帧），begin() 时读一次、帧内直读静态字段。
    // volatile：与 camera 同理，防 AsyncParticles 工作线程读到撕裂/过期值。
    private static volatile ParticleAvoidance.Strength frameStrength;

    /** 当前帧的规避强度（begin 窗口外或配置缺失返回 null，调用方回落直读）。 */
    public static ParticleAvoidance.Strength frameStrength() {
        return frameStrength;
    }

    /** 渲染帧开始时快照一次规避强度（null = 规避关闭/不可用，本帧不再查配置）。 */
    public static void snapshotStrength(ParticleAvoidance.Strength strength) {
        frameStrength = strength;
    }

    public static Set<Particle> syncBatch(Map<ParticleTextureSheet, Set<Particle>> queues,
                                          ParticleTextureSheet sheet, Set<Particle> original) {
        Camera frameCamera = camera;
        float frameDelta = tickDelta;
        if (frameCamera == null) return original;
        var result = ParticleRenderRouting.syncBatch(queues, sheet, original,
                ParticleTextureSheet.PARTICLE_SHEET_OPAQUE, ParticleTextureSheet.PARTICLE_SHEET_LIT,
                ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT,
                p -> p instanceof OwnedDecoration owned && owned.ssca$cameraVisibility(frameCamera, frameDelta) < 1);
        return result;
    }

    private static synchronized void warn(Throwable failure) {
        if (warned) return;
        warned = true;
        LoggerFactory.getLogger("SSCA_ParticleDiag").warn("[SSCA_ParticleDiag] AsyncParticles per-particle sync API unavailable", failure);
    }
}
