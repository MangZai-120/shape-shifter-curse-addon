package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

public class AllaySPPortableBeacon {

	private static final String ACTIVE_TAG = "ssc_beacon_active";
	private static final double RANGE = 20.0;
	private static final int COST_INTERVAL = 60; // 3 seconds (60 ticks)
	private static final Identifier MANA_RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");
	private static final Identifier MANA_COOLDOWN_ID = new Identifier("my_addon", "form_allay_sp_mana_cooldown_resource");

	private AllaySPPortableBeacon() {
		// Utilitary class
	}

	public static void init() {
		UseItemCallback.EVENT.register(AllaySPPortableBeacon::onUseItem);
	}

	private static boolean isActiveBeacon(ItemStack stack) {
		return false;
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
		try {
			return PowerHolderComponent.KEY.get(player).getPowers().stream()
					.anyMatch(p -> p.getType().getIdentifier().getNamespace().equals("my_addon") && p.getType().getIdentifier().getPath().contains("form_allay_sp"));
		} catch (Exception e) {
			return false;
		}
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

	private static int getManaValue(ServerPlayerEntity player) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			// Resource not found
		}
		return 0;
	}

	private static void setManaValue(ServerPlayerEntity player, int value) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(Math.max(0, value));
				PowerHolderComponent.syncPower(player, powerType);
			}
		} catch (Exception e) {
			// Resource not found
		}
	}

	private static void triggerManaCooldown(ServerPlayerEntity player, int ticks) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_COOLDOWN_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(ticks);
				PowerHolderComponent.syncPower(player, powerType);
			}
		} catch (Exception e) {
			// Resource not found
		}
	}
}
