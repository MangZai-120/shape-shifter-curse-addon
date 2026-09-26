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
    public static void end() { camera = null; }

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
