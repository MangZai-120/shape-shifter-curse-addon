package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 工具方法：在服务端给玩家发放自定义成就。
 * 用于 minecraft:impossible 触发的成就（由 Java 主动 grant criterion）。
 */
public final class AdvancementUtils {

	private AdvancementUtils() {}

	/**
	 * 给指定玩家发放成就（grant 所有未完成的 criteria）。
	 * 仅服务端可用；客户端调用会静默忽略。
	 *
	 * @param player    目标玩家
	 * @param advId     成就 Identifier，如 new Identifier("ssc_addon", "ssc_addon/tonight_moon_beautiful")
	 */
	public static void grant(PlayerEntity player, Identifier advId) {
		if (player == null || player.getWorld().isClient) return;
		if (!(player instanceof ServerPlayerEntity sp)) return;
		MinecraftServer server = sp.getServer();
		if (server == null) return;
		Advancement adv = server.getAdvancementLoader().get(advId);
		if (adv == null) return;
		AdvancementProgress progress = sp.getAdvancementTracker().getProgress(adv);
		if (progress.isDone()) return;
		for (String c : progress.getUnobtainedCriteria()) {
			sp.getAdvancementTracker().grantCriterion(adv, c);
		}
	}
}
