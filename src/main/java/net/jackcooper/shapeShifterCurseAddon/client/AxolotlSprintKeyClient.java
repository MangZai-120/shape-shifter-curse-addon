package net.jackcooper.shapeShifterCurseAddon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 进化美西螈水流冲刺 - 客户端「真正疾跑键」上报器。
 * 仅进化美西螈形态下，上报绑定疾跑键的物理按下/松开边沿，服务端识别双击。
 * 直接读取物理键，兼容切换式疾跑；双击 W 和游泳自动疾跑不算按键操作。
 */
@Environment(EnvType.CLIENT)
public final class AxolotlSprintKeyClient {

	private static boolean lastSent = false;
	private static InputUtil.Key lastScancodeKey;
	private static int scancodeKeyCode = GLFW.GLFW_KEY_UNKNOWN;

	private AxolotlSprintKeyClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AxolotlSprintKeyClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| !client.isWindowFocused() || !FormUtils.isUpgradeAxolotl(player)) {
			if (lastSent) { // 离开形态：补发一次 held=false，防服务端残留 true
				send(false);
				lastSent = false;
			}
			return;
		}
		KeyBinding sprint = client.options.sprintKey;
		boolean held = sprint != null && isPhysicallyHeld(client, sprint);
		if (held != lastSent) {
			if (send(held)) lastSent = held;
		}
	}

	/** Read the bound physical key, including when vanilla sprint uses toggle mode. */
	private static boolean isPhysicallyHeld(MinecraftClient client, KeyBinding sprint) {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(sprint);
		if (key.getCode() < 0) return false;
		long window = client.getWindow().getHandle();
		if (key.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
		}
		if (key.getCategory() == InputUtil.Type.KEYSYM) {
			return InputUtil.isKeyPressed(window, key.getCode());
		}
		if (!key.equals(lastScancodeKey)) {
			lastScancodeKey = key;
			scancodeKeyCode = GLFW.GLFW_KEY_UNKNOWN;
			for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
				if (GLFW.glfwGetKeyScancode(code) == key.getCode()) { scancodeKeyCode = code; break; }
			}
		}
		return scancodeKeyCode != GLFW.GLFW_KEY_UNKNOWN && InputUtil.isKeyPressed(window, scancodeKeyCode);
	}

	private static boolean send(boolean held) {
		if (MinecraftClient.getInstance().getNetworkHandler() == null
				|| !ClientPlayNetworking.canSend(SscAddonNetworking.PACKET_AXOLOTL_SPRINT_KEY)) return false;
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(held);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_AXOLOTL_SPRINT_KEY, buf);
		return true;
	}
}
