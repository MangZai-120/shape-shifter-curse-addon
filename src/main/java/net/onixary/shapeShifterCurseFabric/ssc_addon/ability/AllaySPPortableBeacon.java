package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

public class AllaySPPortableBeacon {

    private static final Identifier BEACON_ACTIVE_ID = new Identifier("my_addon", "form_allay_sp_beacon_active");

	private AllaySPPortableBeacon() {
		// Utility class
	}

	public static void init() {
		UseItemCallback.EVENT.register(AllaySPPortableBeacon::onUseItem);
	}

	private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			ItemStack stack = player.getStackInHand(hand);
			// Toggle logic:
			// If sneaking -> Do nothing (let vanilla behavior happen, i.e., place block)
			// If not sneaking -> Toggle activation (and consume item use)
			if (stack.isOf(Items.BEACON) && !player.isSneaking() && isSpAllay(serverPlayer)) {
				toggleBeacon(serverPlayer);
				return TypedActionResult.success(stack); // Consume the action so block is not placed
			}
		}
		// Pass to allow vanilla behavior (or other mods)
		return TypedActionResult.pass(player.getStackInHand(hand));
	}

	private static boolean isSpAllay(ServerPlayerEntity player) {
		// Check for any power containing "form_allay_sp" in its ID
		// This relies on the power structure being consistent with "form_allay_sp" naming convention
		return PowerUtils.hasResource(player, BEACON_ACTIVE_ID, 1);
	}

	private static boolean isBeaconActive(ServerPlayerEntity player) {
		return PowerUtils.getResourceValue(player, BEACON_ACTIVE_ID) == 1;
	}

	public static void toggleBeacon(ServerPlayerEntity player) {
		if (isBeaconActive(player)) {
			deactivateBeacon(player);
		} else {
			activateBeacon(player);
		}
	}

	private static void activateBeacon(ServerPlayerEntity player) {
		PowerUtils.setResourceValueAndSync(player, BEACON_ACTIVE_ID, 1);

		player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.ssc_addon.beacon.activated"), true);
	}

	public static void deactivateBeacon(ServerPlayerEntity player) {
		PowerUtils.setResourceValueAndSync(player, BEACON_ACTIVE_ID, 0);

		player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.ssc_addon.beacon.deactivated"), true);
	}
}
