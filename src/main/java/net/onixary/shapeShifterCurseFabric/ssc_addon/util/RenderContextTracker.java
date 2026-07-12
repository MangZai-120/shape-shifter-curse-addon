package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

/**
 * 跟踪当前物品渲染上下文（GUI/GROUND vs 手持）。
 * 由 WaterSpearInventoryMixin 在 renderItem HEAD 设置，
 * 由 held predicate 读取——GUI 上下文时强制返回 0（不触发 3D override）。
 * 使用 ThreadLocal 保证渲染线程隔离，避免嵌套渲染串扰。
 */
public class RenderContextTracker {
	private static final ThreadLocal<Boolean> GUI_CONTEXT = ThreadLocal.withInitial(() -> false);

	public static boolean isGuiContext() {
		return GUI_CONTEXT.get();
	}

	public static void setGuiContext(boolean value) {
		GUI_CONTEXT.set(value);
	}

	public static void clear() {
		GUI_CONTEXT.set(false);
	}
}
