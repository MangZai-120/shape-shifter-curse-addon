package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

/**
 * SSCA 美西螈装死 - 客户端提前结束检测器。
 * 装死期间（玩家持有 PLAYING_DEAD 效果）按 sp_secondary → 发包请求提前结束装死。
 *
 * 关键：非装死时也持续跟踪按键状态（不重置），这样"触发装死的那一次按键"
 * 的按下状态会延续到装死开始后，避免被边沿检测误判为"提前结束"而瞬间取消装死。
 */
@Environment(EnvType.CLIENT)
public final class PlayDeadEndClient {
	private static boolean wasKeyPressed = false;
	private static boolean wasPlayingDead = false;
	private static long playDeadStartTime = 0L;
	/**
	 * 装死开始后的宽限期（tick）：此期间不响应“再按副技能键提前结束”。
	 * 原因：单机集成服务端下，“触发装死的那一次按键”与 PLAYING_DEAD 生效可能落在同一 tick，
	 * 边沿检测会把这次触发误判为“提前结束”导致刚装死就被立即打断。加 10t 宽限彻底规避该竞态。
	 */
	private static final int END_GRACE_TICKS = 10;

	private PlayDeadEndClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PlayDeadEndClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			wasKeyPressed = false;
			wasPlayingDead = false;
			return;
		}
		boolean isPlayingDead = player.hasStatusEffect(SscAddon.PLAYING_DEAD);
		// 记录装死开始时刻（用于宽限期判定）
		if (isPlayingDead && !wasPlayingDead) {
			playDeadStartTime = client.world.getTime();
		}
		// 用裸 GLFW 物理检测（绕过 StunnedKeyBindingMixin 在装死期对 sp_secondary 的屏蔽）
		boolean pressed = SscAddonKeybindings.isSecondaryRawPressed();
		// 仅在装死已持续超过宽限期后，才允许“再按副技能键提前结束”，避免触发那一下被误判
		if (isPlayingDead && pressed && !wasKeyPressed
				&& (client.world.getTime() - playDeadStartTime) >= END_GRACE_TICKS) {
			PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
			ClientPlayNetworking.send(SscAddonNetworking.PACKET_PLAY_DEAD_END, buf);
		}
		wasKeyPressed = pressed;
		wasPlayingDead = isPlayingDead;
	}
}
