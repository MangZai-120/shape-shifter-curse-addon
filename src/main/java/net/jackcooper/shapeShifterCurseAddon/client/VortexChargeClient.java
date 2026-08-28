package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.jackcooper.shapeShifterCurseAddon.ability.VortexChargeManager;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

/**
 * SP 美西螈漩涡蓄力 - 客户端按键检测器。
 * 按 sp_primary：未蓄力 → 发「开始」包；蓄力中(vortex_state>0) → 发「释放」包。
 */
@Environment(EnvType.CLIENT)
public final class VortexChargeClient {
	private static boolean wasKeyPressed = false;

	private VortexChargeClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VortexChargeClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		KeyBinding key = SscAddonKeybindings.getPrimaryKey();
		if (player == null || client.world == null || key == null) {
			wasKeyPressed = false;
			VortexChargeManager.setClientLocalCharging(false);
			return;
		}
		if (!FormUtils.isAxolotlSP(player)) {
			wasKeyPressed = false;
			VortexChargeManager.setClientLocalCharging(false);
			return;
		}
		// 每 tick 缓存本地玩家蓄力标记，供碰撞推挤 mixin 快速读取（避免每次碰撞读 Apoli 资源）
		int vortexState = PowerUtils.getClientResourceValue(player, VortexChargeManager.VORTEX_STATE);
		VortexChargeManager.setClientLocalCharging(vortexState > 0);

		boolean pressed = key.isPressed();
		if (pressed && !wasKeyPressed) {
			if (vortexState > 0) {
				SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_VORTEX_RELEASE);
			} else {
				SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_VORTEX_START);
			}
		}
		wasKeyPressed = pressed;
	}

}
