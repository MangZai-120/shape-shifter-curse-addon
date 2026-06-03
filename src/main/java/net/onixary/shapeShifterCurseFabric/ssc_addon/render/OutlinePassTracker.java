package net.onixary.shapeShifterCurseFabric.ssc_addon.render;

/**
 * 标记当前线程是否处于发光描边（outline）渲染 pass。
 * Bug 12 fix 与 Bug 2 fix 协同：
 *   Bug 2 fix 让 outline pass 也跑 form 完整 before/process/render/after 流程，
 *   会导致 DefaultModelAnimationSystem 的 tailDrag 状态在同一帧被累计两次。
 *   故在 outline pass 期间不向 WeakHashMap 写回状态，仅消费 normal pass 写的快照。
 */
public final class OutlinePassTracker {
    private static final ThreadLocal<Boolean> IN_OUTLINE_PASS = ThreadLocal.withInitial(() -> false);

    private OutlinePassTracker() {}

    public static boolean isInOutlinePass() {
        return IN_OUTLINE_PASS.get();
    }

    public static void enter() {
        IN_OUTLINE_PASS.set(true);
    }

    public static void exit() {
        IN_OUTLINE_PASS.set(false);
    }
}
