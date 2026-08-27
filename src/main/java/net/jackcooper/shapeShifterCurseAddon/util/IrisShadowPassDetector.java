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
    private static final ThreadLocal<Boolean> SUPPRESS_POWER_QUERY = ThreadLocal.withInitial(() -> Boolean.FALSE);

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

    public static void enterSuppressScope() {
        SUPPRESS_POWER_QUERY.set(Boolean.TRUE);
    }

    public static void exitSuppressScope() {
        SUPPRESS_POWER_QUERY.set(Boolean.FALSE);
    }

    /** 供 PowerHolderComponent 查询 mixin 使用：当前是否应返回空 power 列表以绕开崩溃路径。 */
    public static boolean shouldSuppressPowerQuery() {
        return SUPPRESS_POWER_QUERY.get();
    }
}
