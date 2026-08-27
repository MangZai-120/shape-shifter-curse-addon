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
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 跳蛛「跳杀」- 客户端按键检测器。
 * 仅跳蛛形态生效，单主键（sp_primary）边沿检测：按下沿 → 开始蓄力；松开沿 → 跳杀。
 * 所有判定与结算在服务端（{@code JumpKillManager}）。
 */
@Environment(EnvType.CLIENT)
public final class JumpKillClient {

	private static boolean wasPressed = false;

	private JumpKillClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(JumpKillClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		KeyBinding key = SscAddonKeybindings.getPrimaryKey();
		if (player == null || client.world == null || key == null
				|| !FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE)) {
			wasPressed = false;
			return;
		}
		boolean pressed = key.isPressed();
		if (pressed && !wasPressed) {
			send(SscAddonNetworking.PACKET_JUMP_KILL_CHARGE_START);
		} else if (!pressed && wasPressed) {
			send(SscAddonNetworking.PACKET_JUMP_KILL_CHARGE_RELEASE);
		}
		wasPressed = pressed;
	}

	private static void send(Identifier packet) {
		ClientPlayNetworking.send(packet, new PacketByteBuf(Unpooled.buffer()));
	}
}
