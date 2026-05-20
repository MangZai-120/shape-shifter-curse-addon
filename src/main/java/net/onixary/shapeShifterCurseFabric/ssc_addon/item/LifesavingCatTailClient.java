package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

/**
 * 客户端独占工具，专门承载所有 {@link MinecraftClient} 相关的冷却 UI 计算。
 * 单独抽离的目的：让 {@link LifesavingCatTailItem} 这个通用类（服务端也会加载）
 * 不直接 import {@code net.minecraft.client.*}，避免在专用服务端因 HotSpot
 * 链接 {@code MinecraftClient} 而触发 {@link NoClassDefFoundError}。
 * <p>
 * 调用方必须先用 {@code FabricLoader#getEnvironmentType() == EnvType.CLIENT}
 * 守卫后再调用本类，方能维持惰性链接语义。
 */
@Environment(EnvType.CLIENT)
public final class LifesavingCatTailClient {

	private LifesavingCatTailClient() {
	}

	/** @return 当前本地玩家持有的该物品剩余冷却 tick；本地玩家不存在或未冷却时返回 0。 */
	public static int getCooldownRemaining(Item item, int maxCooldown) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) return 0;
		float progress = client.player.getItemCooldownManager().getCooldownProgress(item, 0.0f);
		// progress：1.0 = 刚开始冷却，0.0 = 已冷却完成
		return (int) (progress * maxCooldown);
	}

	/** @return 当前本地玩家是否处于该物品冷却中。 */
	public static boolean isOnCooldown(Item item) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) return false;
		return client.player.getItemCooldownManager().isCoolingDown(item);
	}
}
