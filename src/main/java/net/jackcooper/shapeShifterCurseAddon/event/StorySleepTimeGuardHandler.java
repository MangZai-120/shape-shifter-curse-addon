package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.story.TideSpiritStoryManager;

import java.util.UUID;

/**
 * 「月痕之力」/「潮汐之灵」剧情真睡期间，阻止该玩家参与原版跳夜判定
 * （由 SscPlayerMixin 的 canResetTimeBySleeping 注入迁移到官方 {@link EntitySleepEvents#ALLOW_RESETTING_TIME}）。
 * <p>返回 false = 该玩家真睡不推进时间 / 不跳夜，语义精确等价于原 {@code canResetTimeBySleeping} 返回 false；
 * 客户端 STORY_SLEEPING 为空 → 返回 true 不介入，与原 mixin 行为一致。
 */
public final class StorySleepTimeGuardHandler {

	private StorySleepTimeGuardHandler() {
	}

	public static void register() {
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> {
			UUID uuid = player.getUuid();
			if (MoonScarStoryManager.isStorySleeping(uuid) || TideSpiritStoryManager.isStorySleeping(uuid)) {
				return false; // 剧情真睡：不重置时间 / 不跳夜
			}
			return true; // 默认允许
		});
	}
}
