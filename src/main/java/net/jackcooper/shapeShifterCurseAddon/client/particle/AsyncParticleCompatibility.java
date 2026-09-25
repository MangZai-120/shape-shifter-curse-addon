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
    private static Method tickSync, renderSync;
    private static Camera camera;
    private static float tickDelta;
    private static boolean warned;

    private AsyncParticleCompatibility() {}

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("asyncparticles")) return;
        try {
            tickSync = Particle.class.getMethod("asyncparticles$setTickSync");
            renderSync = Particle.class.getMethod("asyncparticles$setRenderSync");
        } catch (ReflectiveOperationException | LinkageError failure) { warn(failure); }
    }

    public static void prepare(Particle particle) {
        if (tickSync == null || renderSync == null) return;
        try {
            // AsyncParticles excludes tick-synchronous particles from its separate GPU queue.
            // Render synchronously too, so temporary alpha changes never race with worker render/tick jobs.
            tickSync.invoke(particle);
            renderSync.invoke(particle);
        } catch (ReflectiveOperationException | LinkageError failure) {
            warn(failure);
        }
    }

    public static void begin(Camera currentCamera, float delta) { camera = currentCamera; tickDelta = delta; }
    public static void end() { camera = null; }

    public static Set<Particle> syncBatch(Map<ParticleTextureSheet, Set<Particle>> queues,
                                          ParticleTextureSheet sheet, Set<Particle> original) {
        if (camera == null) return original;
        var result = ParticleRenderRouting.syncBatch(queues, sheet, original,
                ParticleTextureSheet.PARTICLE_SHEET_OPAQUE, ParticleTextureSheet.PARTICLE_SHEET_LIT,
                ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT,
                p -> p instanceof OwnedDecoration owned && owned.ssca$cameraVisibility(camera, tickDelta) < 1);
        return result;
    }

    private static void warn(Throwable failure) {
        if (warned) return;
        warned = true;
        LoggerFactory.getLogger("SSCA_ParticleDiag").warn("[SSCA_ParticleDiag] AsyncParticles per-particle sync API unavailable", failure);
    }
}
