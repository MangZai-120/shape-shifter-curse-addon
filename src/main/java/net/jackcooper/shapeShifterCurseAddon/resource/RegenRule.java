package net.jackcooper.shapeShifterCurseAddon.resource;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 回复规则（jackcooper）：描述一条资源「怎么回」的可插拔接口。
 *
 * <p>调度器（{@code ResourceBars} 的统一 tick）按 {@link #interval()} 周期在服务端回调
 * {@link #tickRegen}，返回本次回复量（0 = 本轮不回，负数 = 衰减，如蝙蝠脱战掉血）。
 * 复杂条件（脱战判定、地形、昼夜）在实现内自行组合谓词。
 */
public interface RegenRule {

	/** 回调周期（tick），默认每秒。 */
	default int interval() {
		return 20;
	}

	/**
	 * 计算本轮回复量（服务端调用）。
	 *
	 * @param player 持有者
	 * @param bar    所属资源条定义（可读其它规则状态）
	 * @return 回复量；负数表示衰减；0 表示本轮不变
	 */
	int tickRegen(ServerPlayerEntity player, ResourceBarDef bar);
}
