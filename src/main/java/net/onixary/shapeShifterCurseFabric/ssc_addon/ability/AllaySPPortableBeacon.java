package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

public class AllaySPPortableBeacon {

    private AllaySPPortableBeacon() {
		// Utilitary class
	}


	private static boolean isBeaconActive(ServerPlayerEntity player) {
		return PowerUtils.getResourceValue(player, new Identifier("my_addon", "form_allay_sp_beacon_active")) == 1;
	}

	public static void toggleBeacon(ServerPlayerEntity player) {
		if (isBeaconActive(player)) {
			deactivateBeacon(player);
		} else {
			activateBeacon(player);
		}
	}

	private static void activateBeacon(ServerPlayerEntity player) {
		PowerUtils.setResourceValue(player, new Identifier("my_addon", "form_allay_sp_beacon_active"), 1);

		player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.ssc_addon.beacon.activated"), true);
	}

	public static void deactivateBeacon(ServerPlayerEntity player) {
		PowerUtils.setResourceValue(player, new Identifier("my_addon", "form_allay_sp_beacon_active"), 0);

		player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.ssc_addon.beacon.deactivated"), true);
	}

	// ===== Mana resource read/write =====

}
