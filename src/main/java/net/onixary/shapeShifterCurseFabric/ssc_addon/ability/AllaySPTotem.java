package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AllaySPTotem {

	private static final String ACTIVE_TAG = "ssc_totem_active";
	private static final double RANGE = 20.0;
	// 使用UUID追踪持有激活图腾的玩家，避免存储实体引用导致跨维度/重连后引用过期
	private static final Set<UUID> playersWithActiveTotems = ConcurrentHashMap.newKeySet();

	private AllaySPTotem() {
		// Utility class
	}

	public static void init() {
		UseItemCallback.EVENT.register(AllaySPTotem::onUseItem);
		// Register to server tick event but only check players with active totems
		ServerTickEvents.END_SERVER_TICK.register(AllaySPTotem::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		// 每40tick（2秒）检查一次，平衡响应性和性能
		long currentTick = server.getOverworld().getTime();
		if (currentTick % 40 != 0) {
			return;
		}

		// 使用Iterator安全遍历并移除，避免ConcurrentModificationException
		Iterator<UUID> it = playersWithActiveTotems.iterator();
		while (it.hasNext()) {
			UUID uuid = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

			// 玩家离线或不存在，移除追踪
			if (player == null) {
				it.remove();
				continue;
			}

			// 检查是否仍持有激活的图腾
			boolean stillHasActiveTotem = false;

			for (ItemStack stack : player.getInventory().main) {
				if (isActiveTotem(stack)) {
					stillHasActiveTotem = true;
					break;
				}
			}

			if (!stillHasActiveTotem) {
				for (ItemStack stack : player.getInventory().offHand) {
					if (isActiveTotem(stack)) {
						stillHasActiveTotem = true;
						break;
					}
				}
			}

			if (!stillHasActiveTotem) {
				it.remove();
				continue;
			}

			// 不再是SP悦灵时：关闭图腾并移除追踪
			if (!isSpAllay(player)) {
				deactivateAllTotems(player);
				it.remove();
			}
		}
	}

	private static void deactivateAllTotems(ServerPlayerEntity player) {
		// Check main inventory and offhand
		boolean deactivatedAny = false;

		// Check main inventory
		for (ItemStack stack : player.getInventory().main) {
			if (isActiveTotem(stack)) {
				deactivateTotem(stack);
				deactivatedAny = true;
			}
		}

		// Check offhand
		for (ItemStack stack : player.getInventory().offHand) {
			if (isActiveTotem(stack)) {
				deactivateTotem(stack);
				deactivatedAny = true;
			}
		}

		if (deactivatedAny) {
			player.sendMessage(Text.translatable("message.ssc_addon.totem.deactivated"), true);
			player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
		}
	}

	private static void deactivateTotem(ItemStack stack) {
		NbtCompound nbt = stack.getOrCreateNbt();
		nbt.remove(ACTIVE_TAG);
		if (nbt.contains("Enchantments")) {
			nbt.remove("Enchantments");
		}
		if (nbt.contains("HideFlags")) {
			nbt.remove("HideFlags");
		}
	}

	private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, net.minecraft.world.World world, Hand hand) {
		if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

		ItemStack stack = player.getStackInHand(hand);

		// Only function for Totem of Undying
		if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
			return TypedActionResult.pass(stack);
		}

		// Must be SP Allay locally checked
		if (!isSpAllay(player)) {
			return TypedActionResult.pass(stack);
		}

		// Toggle Active State
		NbtCompound nbt = stack.getOrCreateNbt();
		boolean isActive = nbt.getBoolean(ACTIVE_TAG);

		if (isActive) {
			// Deactivate
			nbt.remove(ACTIVE_TAG);

			// Remove glint
			if (nbt.contains("Enchantments")) {
				nbt.remove("Enchantments");
			}
			if (nbt.contains("HideFlags")) {
				nbt.remove("HideFlags");
			}

			player.sendMessage(Text.translatable("message.ssc_addon.totem.deactivated"), true);
			player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);

			if (player instanceof ServerPlayerEntity serverPlayer) {
				updateActiveTotemTracking(serverPlayer, false);
			}
		} else {
			// Activate
			nbt.putBoolean(ACTIVE_TAG, true);

			// Add Glint
			if (!nbt.contains("Enchantments")) {
				NbtList enchantments = new NbtList();
				NbtCompound unbreaking = new NbtCompound();
				unbreaking.putString("id", "minecraft:unbreaking");
				unbreaking.putShort("lvl", (short) 1);
				enchantments.add(unbreaking);
				nbt.put("Enchantments", enchantments);
				nbt.putInt("HideFlags", 1); // Hide enchantments
			}

			player.sendMessage(Text.translatable("message.ssc_addon.totem.activated"), true);
			player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 2.0f);

			if (player instanceof ServerPlayerEntity serverPlayer) {
				updateActiveTotemTracking(serverPlayer, true);
			}
		}

		return TypedActionResult.success(stack);
	}

	/**
	 * Called by Mixin when an entity would die or use a totem.
	 *
	 * @param entity The entity attempting to use a totem.
	 * @return true if an Allay SP totem was used and prevented death.
	 */
	public static boolean tryUseAllayTotem(LivingEntity entity) {
		if (entity.getWorld().isClient) return false;

		// Get nearby players within range
		Box box = entity.getBoundingBox().expand(RANGE);
		List<PlayerEntity> nearbyPlayers = entity.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p instanceof ServerPlayerEntity);

		for (PlayerEntity player : nearbyPlayers) {
			if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

			// a. Check if they are SP Allay
			if (!isSpAllay(serverPlayer)) continue;

			// b. Check whitelist：自我救援总是允许；救他人统一走 isBuffTarget
			//   - whitelistEnabled = false：仅作用于非怪物/非敌对生物
			//   - whitelistEnabled = true：白名单空时玩家/驯服宠物/owner-tag；非空时仅白名单成员
			if (entity != serverPlayer) {
				if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isBuffTarget(serverPlayer, entity)) {
					continue;
				}
			}

			// c. Check inventory for Active Totem
			ItemStack activeTotem = findActiveTotem(serverPlayer);

			if (!activeTotem.isEmpty()) {
				// d. Consume totem
				activeTotem.decrement(1);

				// e. Trigger Effect on DYING ENTITY
				entity.setHealth(1.0F);
				entity.clearStatusEffects();
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1)); // 5 seconds
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1)); // 45 seconds
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0)); // 40 seconds

				// Visuals: Totem of Undying particle/sound
				entity.getWorld().sendEntityStatus(entity, (byte) 35);

				// Notify SP Allay
				serverPlayer.sendMessage(Text.translatable("message.ssc_addon.totem.triggered", entity.getDisplayName()), true);

				return true; // Prevent death
			}
		}

		return false; // Did not prevent death
	}

	private static ItemStack findActiveTotem(ServerPlayerEntity player) {
		// Check hands
		if (isActiveTotem(player.getMainHandStack())) return player.getMainHandStack();
		if (isActiveTotem(player.getOffHandStack())) return player.getOffHandStack();

		// Check inventory main
		for (ItemStack stack : player.getInventory().main) {
			if (isActiveTotem(stack)) return stack;
		}

		return ItemStack.EMPTY;
	}

	private static boolean isActiveTotem(ItemStack stack) {
		// Check if item is Totem and has active tag
		if (stack.getNbt() != null) {
			return !stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING) && stack.hasNbt() && stack.getNbt().getBoolean(ACTIVE_TAG);
		}
		return false;
	}

	private static boolean isSpAllay(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			try {
				return PowerHolderComponent.KEY.get(serverPlayer).getPowers().stream()
						.anyMatch(p -> p.getType().getIdentifier().getNamespace().equals("my_addon") && p.getType().getIdentifier().getPath().contains("form_allay_sp"));
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}

	private static void updateActiveTotemTracking(ServerPlayerEntity player, boolean hasActiveTotem) {
		if (hasActiveTotem) {
			playersWithActiveTotems.add(player.getUuid());
		} else {
			playersWithActiveTotems.remove(player.getUuid());
		}
	}

	public static void clearPlayer(ServerPlayerEntity player) {
		playersWithActiveTotems.remove(player.getUuid());
	}

	public static void clearAll() {
		playersWithActiveTotems.clear();
	}
}
