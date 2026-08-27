package net.jackcooper.shapeShifterCurseAddon.resource;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 统一资源条 tick 调度注册（jackcooper）：挂 ServerTickEvents.END_SERVER_TICK，
 * 每 tick 转 {@link ResourceBars#serverTick}（regen/衰减/分段效果统一在此驱动）。
 *
 * <p>由 {@code SscAddon.onInitialize} 调用 {@link #register()}。
 */
public final class ResourceBarsTicker {

	private ResourceBarsTicker() {}

	private static boolean registered = false;

	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(ResourceBars::serverTick);
	}
}
