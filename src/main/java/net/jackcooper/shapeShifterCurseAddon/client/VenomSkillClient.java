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
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.SscAddonKeybindings;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * 跳蛛「毒液」- 客户端次键检测器。
 * 仅跳蛛形态生效，次键（sp_secondary）按下沿发包；基础/丝线强化形态判定全在服务端。
 */
@Environment(EnvType.CLIENT)
public final class VenomSkillClient {

	private static boolean wasPressed = false;

	private VenomSkillClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VenomSkillClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		KeyBinding key = SscAddonKeybindings.getSecondaryKey();
		if (player == null || client.world == null || key == null
				|| !FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE)) {
			wasPressed = false;
			return;
		}
		boolean pressed = key.isPressed();
		if (pressed && !wasPressed) {
			send(SscAddonNetworking.PACKET_VENOM_SKILL_PRESS);
		}
		wasPressed = pressed;
	}

	private static void send(Identifier packet) {
		ClientPlayNetworking.send(packet, new PacketByteBuf(Unpooled.buffer()));
	}
}
