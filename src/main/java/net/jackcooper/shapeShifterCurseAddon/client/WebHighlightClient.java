package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 月织蛛减速网「踩网蓝色高亮」- 客户端专属发光表。
 * 服务端仅向施法者发 {@link SscAddonNetworking#PACKET_WEB_HIGHLIGHT}，只有施法者本机把受害者
 * 的 entityId 记进本表；{@code EntityWebGlowMixin} 据此对这些实体本地描蓝边——实现「仅施法者可见」。
 */
@Environment(EnvType.CLIENT)
public final class WebHighlightClient {

	/** entityId -> 高亮到期的世界时间（tick）。 */
	private static final Map<Integer, Long> HIGHLIGHT = new ConcurrentHashMap<>();

	private WebHighlightClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_WEB_HIGHLIGHT,
				(client, handler, buf, responseSender) -> {
					int entityId = buf.readVarInt();
					int duration = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						HIGHLIGHT.put(entityId, client.world.getTime() + duration);
					});
				});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world == null) {
				HIGHLIGHT.clear();
				return;
			}
			long now = client.world.getTime();
			HIGHLIGHT.entrySet().removeIf(e -> e.getValue() <= now);
		});
	}

	/** {@code EntityWebGlowMixin} 每帧查询：该实体当前是否处于踩网蓝色高亮。 */
	public static boolean isHighlighted(int entityId) {
		return HIGHLIGHT.containsKey(entityId);
	}
}
