package net.jackcooper.shapeShifterCurseAddon.resource;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 分段效果（jackcooper）：按资源值所在区段持续的每 tick 效果（如蝙蝠血条三段）。
 *
 * <p>统一调度器对每条资源的持有者每 tick 轮询 {@link #isInSegment}，
 * 命中的段调用 {@link #applyTick}。效果本体（属性修饰/伤害增减）在各自实现里
 * 走既有 mixin 查询点或直接施加，与框架解耦。
 */
public interface ThresholdEffect {

	/** 当前值是否落在本段（如 current &lt; max*0.25）。 */
	boolean isInSegment(int current, int max);

	/** 段内每 tick 效果（服务端）。 */
	void applyTick(ServerPlayerEntity player, int current, int max);
}
