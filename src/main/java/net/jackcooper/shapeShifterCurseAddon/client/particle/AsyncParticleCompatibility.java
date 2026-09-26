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
    // volatile：getSync 回调可能来自 AsyncParticles 工作线程，避免读到上一帧相机或 end() 后的 null
    private static volatile Camera camera;
    private static volatile float tickDelta;
    private static boolean warned;

    private AsyncParticleCompatibility() {}

    public static void init() {
        // 2026-09-26 修复：此前把反射方法解析误删导致 tickSync 恒 null、prepare 永远早退——
        // 全部粒子避让失效。改回惰性解析：首个粒子打标时解析一次（AsyncParticles 未装则直接放弃）。
        resolved.set(false);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean resolved = new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void prepare(Particle particle) {
        if (!resolved.getAndSet(true)) {
            if (!FabricLoader.getInstance().isModLoaded("asyncparticles")) return;
            try {
                // AsyncParticles 通过 mixin 给 Particle 注入这两个同步标记方法；钉回 CPU 队列后
                // 我们的绘制边界透明度控制才能作用于 GPU 资格粒子（火焰/魂火等主力演出）。
                tickSync = Particle.class.getMethod("asyncparticles$setTickSync");
                renderSync = Particle.class.getMethod("asyncparticles$setRenderSync");
            } catch (ReflectiveOperationException | LinkageError failure) {
                warn(failure);
                return;
            }
        }
        if (tickSync == null || renderSync == null) return;
        // 2026-09-26 爆发熔断：火球爆炸单帧 600+ 粒子全部钉回同步队列，与 AsyncParticles
        // 工作线程在光影管线下竞争导致渲染线程挂起（0xCFFFFFFF 三次复现）。限制每 100ms
        // 窗口内最多钉定 96 个——爆发增量留在 GPU 异步队列（不控透明度但显示正常），
        // 平时拖尾/法术每帧仅个位数粒子完全不受影响。
        long now = System.currentTimeMillis();
        if (now - windowStart >= 100L) {
            windowStart = now;
            pinnedInWindow = 0;
        }
        if (pinnedInWindow >= PIN_BURST_LIMIT) return;
        pinnedInWindow++;
        try {
            tickSync.invoke(particle);
            renderSync.invoke(particle);
        } catch (ReflectiveOperationException | LinkageError failure) {
            warn(failure);
        }
    }

    private static final int PIN_BURST_LIMIT = 96;
    private static long windowStart;
    private static int pinnedInWindow;

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
