package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 荧光幼灵按键检测器（客户端边沿触发）。
 * 主要键(sp_primary)：荧光幼灵形态下按下 → 发法阵激光包。
 * 次要键(sp_secondary)：荧光幼灵形态下按下 → 发潮汐波动包。
 * 采用边沿触发（pressed && !wasPressed），避免长按连发。
 */
@Environment(EnvType.CLIENT)
public final class FluorescentKeyClient {
	private static boolean wasPrimary = false;
	private static boolean wasSecondary = false;

	private FluorescentKeyClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FluorescentKeyClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			wasPrimary = wasSecondary = false;
			return;
		}
		// 仅荧光幼灵形态响应
		if (!FormUtils.isAxolotlFluorescent(player)) {
			wasPrimary = wasSecondary = false;
			return;
		}
		KeyBinding primary = SscAddonKeybindings.getPrimaryKey();
		KeyBinding secondary = SscAddonKeybindings.getSecondaryKey();

		boolean pNow = primary != null && primary.isPressed();
		if (pNow && !wasPrimary) {
			SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_FLUO_LASER);
		}
		wasPrimary = pNow;

		boolean sNow = secondary != null && secondary.isPressed();
		if (sNow && !wasSecondary) {
			SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_FLUO_TIDAL);
		}
		wasSecondary = sNow;
	}

}