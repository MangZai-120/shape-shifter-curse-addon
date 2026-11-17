package net.jackcooper.shapeShifterCurseAddon.util;

import java.lang.reflect.Method;

/**
 * Iris 光影阴影 pass 检测工具（软依赖，反射调用，未安装 Iris 时安全降级）。
 *
 * 用于修复 Apoli 的 LivingEntityRendererMixin.preventFeatureRendering
 * 在 Iris 阴影渲染重入路径下触发 sourceStage==null 的 NPE 崩溃。
 */
public final class IrisShadowPassDetector {

    private static Boolean irisPresent = null;
    private static Method getInstanceMethod = null;
    private static Method isRenderingShadowPassMethod = null;

    /** 当前线程是否处于「需要跳过 Apoli feature 判断」的渲染上下文。 */
    private static final ThreadLocal<SuppressionScope> SUPPRESS_POWER_QUERY = new ThreadLocal<>();

    private IrisShadowPassDetector() {}

    /** 初始化 Iris 反射句柄，找不到则标记为不可用。 */
    private static void init() {
        if (irisPresent != null) return;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstanceMethod = irisApiClass.getMethod("getInstance");
            isRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
            irisPresent = Boolean.TRUE;
        } catch (Throwable t) {
            // 未安装 Iris 或 API 不匹配，安全降级为「永不抑制」
            irisPresent = Boolean.FALSE;
        }
    }

    /** 是否真正处于 Iris 阴影 pass（Iris 未安装时恒为 false）。 */
    public static boolean isRenderingShadowPass() {
        init();
        if (irisPresent != Boolean.TRUE) return false;
        try {
            Object instance = getInstanceMethod.invoke(null);
            Object result = isRenderingShadowPassMethod.invoke(instance);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- ThreadLocal 标志：供 mixin 在 LivingEntityRenderer.render 内设置 ----

    public static SuppressionScope suppressFor(Object owner) {
        var scope = new SuppressionScope(owner, SUPPRESS_POWER_QUERY.get());
        SUPPRESS_POWER_QUERY.set(scope);
        return scope;
    }

    public static boolean hasSuppressScope() {
        return SUPPRESS_POWER_QUERY.get() != null;
    }

    /** 供 PowerHolderComponent 查询 mixin 使用：当前是否应返回空 power 列表以绕开崩溃路径。 */
    public static boolean shouldSuppressPowerQuery(Object owner) {
        var scope = SUPPRESS_POWER_QUERY.get();
        return owner != null && scope != null && scope.owner == owner;
    }

    public static final class SuppressionScope implements AutoCloseable {
        private final Object owner;
        private final SuppressionScope previous;
        private boolean closed;

        private SuppressionScope(Object owner, SuppressionScope previous) {
            this.owner = owner;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            if (previous == null) SUPPRESS_POWER_QUERY.remove();
            else SUPPRESS_POWER_QUERY.set(previous);
            closed = true;
        }
    }
}
