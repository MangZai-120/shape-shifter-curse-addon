package net.jackcooper.shapeShifterCurseAddon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 进化美西螈水流冲刺 - 客户端「真正疾跑键」上报器。
 * 仅进化美西螈形态下，把物理疾跑键（{@code options.sprintKey}）的按住状态变化上报服务端，
 * 使服务端区分「真正按疾跑键」与「双击 W / 游泳自动疾跑」，避免后两者误触发冲刺。
 */
@Environment(EnvType.CLIENT)
public final class AxolotlSprintKeyClient {

	private static boolean lastSent = false;

	private AxolotlSprintKeyClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AxolotlSprintKeyClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || !FormUtils.isUpgradeAxolotl(player)) {
			if (lastSent) { // 离开形态：补发一次 held=false，防服务端残留 true
				send(false);
				lastSent = false;
			}
			return;
		}
		KeyBinding sprint = client.options.sprintKey;
		boolean held = sprint != null && sprint.isPressed();
		if (held != lastSent) {
			send(held);
			lastSent = held;
		}
	}

	private static void send(boolean held) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(held);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_AXOLOTL_SPRINT_KEY, buf);
	}
}
