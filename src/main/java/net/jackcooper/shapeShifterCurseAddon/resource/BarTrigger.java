package net.jackcooper.shapeShifterCurseAddon.resource;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 变更回调（jackcooper）：资源值变化时触发（满/空/跨段判断在实现内自便）。
 *
 * <p>由 {@code ResourceBars} 门面在每次写值后同步触发，保证所有写路径
 *（门面 gain/consume/set）都能收到通知——替代散落各 Manager 里的手写判断。
 */
public interface BarTrigger {

	/**
	 * 资源值变化回调（服务端，写值后触发）。
	 *
	 * @param player 持有者
	 * @param oldV   变更前值
	 * @param newV   变更后值
	 * @param max    上限
	 */
	void onChange(ServerPlayerEntity player, int oldV, int newV, int max);
}
