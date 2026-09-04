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
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.compat.ssc192.Compat1_9_2;

/**
 * 契灵 - 主要技能客户端按键监听。
 * 单击主键即触发；服务端根据当前标记状态自行分流（标记 / 升级 / 引导真伤）。
 */
@Environment(EnvType.CLIENT)
public final class MancianimaPrimaryClient {

	private static boolean wasKeyPressed = false;

	private MancianimaPrimaryClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MancianimaPrimaryClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) { wasKeyPressed = false; return; }
		if (!isMancianima(player)) { wasKeyPressed = false; return; }
		KeyBinding key = SscAddonKeybindings.getPrimaryKey();
		if (key == null) return;
		boolean pressed = key.isPressed();
		if (pressed && !wasKeyPressed) {
			PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
			ClientPlayNetworking.send(SscAddonNetworking.PACKET_MANCIANIMA_PRIMARY, buf);
		}
		wasKeyPressed = pressed;
	}

	private static boolean isMancianima(ClientPlayerEntity player) {
		try {
			PlayerFormBase form = Compat1_9_2.nowForm(player);
			if (form == null) return false;
			Identifier id = form.FormID;
			return id != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(id);
		} catch (Exception e) {
			return false;
		}
	}
}
