package net.jackcooper.shapeShifterCurseAddon.util;

/**
 * 跟踪水矛的 GUI 绘制上下文（地面/展示框/手持不经过 DrawContext）。
 * 由 WaterSpearInventoryMixin 在统一 drawItem 调用期间设置并在 finally 恢复，
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
