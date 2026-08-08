package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录每块减速蛛网（web_membrane）的施法者 UUID，供碰撞时按施法者白名单豁免队友。
 * <p>网块寿命 60~90s、踩烂即毁，条目随 scheduledTick / 踩烂各自清理；服务器停止时整体清空。
 * 键为 {@link BlockPos#asLong()}。施法者离线 / 重启后条目丢失，碰撞逻辑安全回退为默认白名单。
 */
public final class WebMembraneOwners {

	private static final Map<Long, UUID> OWNERS = new ConcurrentHashMap<>();

	private WebMembraneOwners() {}

	public static void set(BlockPos pos, UUID owner) {
		if (owner != null) {
			OWNERS.put(pos.asLong(), owner);
		}
	}

	public static UUID get(BlockPos pos) {
		return OWNERS.get(pos.asLong());
	}

	public static void remove(BlockPos pos) {
		OWNERS.remove(pos.asLong());
	}

	public static void clear() {
		OWNERS.clear();
	}
}
