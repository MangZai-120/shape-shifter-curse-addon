package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔「惊吓」客户端 —— 仅目标本人的客户端持有幽灵显形表。
 *
 * <p>服务端 spawn 的幽灵苦力怕/幽灵野猫都是<b>真实体</b>（NoAI/无敌/对他人隐身），
 * 本类收 {@code PACKET_SPOOK_GHOST} 登记 UUID 后由 {@code SpookGhostVisibleMixin}
 * 对这些实体局部取消 invisible → 只有目标看得见它们（原版模型/动画/朝向，正立）。
 * 到期自动出表 = 恢复隐身；断线全清。声/粒全部由服务端 S2C 直发，客户端零额外渲染。</p>
 */
@Environment(EnvType.CLIENT)
public final class NightmareSpookClient {

	/** 幽灵实体表：实体 UUID -> 到期世界时间（到期自动出表=恢复隐身）。 */
	private static final Map<UUID, Long> GHOSTS = new ConcurrentHashMap<>();

	private NightmareSpookClient() {
	}

	public static void register() {
		// 幽灵标记：记 UUID（SpookGhostVisibleMixin 据此局部显形）
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_SPOOK_GHOST,
				(client, handler, buf, responseSender) -> {
					UUID ghostUuid = buf.readUuid();
					int life = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						GHOSTS.put(ghostUuid, client.world.getTime() + life);
					});
				});
		// 断线清理
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GHOSTS.clear());
	}

	/** {@code SpookGhostVisibleMixin} 每帧查询：该「隐形」实体是否应在本客户端可见。 */
	public static boolean isGhostVisible(UUID entityUuid) {
		Long until = GHOSTS.get(entityUuid);
		if (until == null) return false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return false;
		if (client.world.getTime() >= until) {
			GHOSTS.remove(entityUuid);
			return false;
		}
		return true;
	}
}
