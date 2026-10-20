package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
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
	// 激活图腾归属标记（2026-10-20：离身失效规则 R3；离开 SP 悦灵本人背包即清除激活）
	private static final String OWNER_TAG = "ssc_totem_owner";
	// 激活冷却（R1）：默认 20 秒；balance 可覆盖（abilities.allay_sp_totem.activate_cooldown_ticks）
	private static final int ACTIVATE_COOLDOWN_TICKS = 400;
	// 图腾救援范围：默认 20 格；运行时从 balance 快照读取（abilities.allay_sp_totem）
	private static final double RANGE = 20.0;
	// 使用UUID追踪持有激活图腾的玩家，避免存储实体引用导致跨维度/重连后引用过期
	private static final Set<UUID> playersWithActiveTotems = ConcurrentHashMap.newKeySet();
	// 激活冷却记录（R1：uuid -> 最近一次激活的世界时间；会话级，断线/重启清零仅损失最长20秒冷却）
	private static final java.util.Map<UUID, Long> ACTIVATE_COOLDOWNS = new ConcurrentHashMap<>();

	// 阶段 5：服务端权威快照读取（快照未初始化回退默认常量）
	private static final net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader BAL =
			new net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader("abilities.allay_sp_totem");

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

			// 检查是否仍持有激活的图腾（R3：必须仍在本人的背包/手上）
			boolean stillHasActiveTotem = false;

			for (ItemStack stack : player.getInventory().main) {
				if (isOwnedActiveTotem(stack, player)) {
					stillHasActiveTotem = true;
					break;
				}
			}

			if (!stillHasActiveTotem) {
				for (ItemStack stack : player.getInventory().offHand) {
					if (isOwnedActiveTotem(stack, player)) {
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

	/**
	 * R2：玩家整个背包（主背包+副手）内是否已有任一激活图腾（不看归属——别人的激活图腾也会在本 tick 被清）。
	 */
	private static boolean hasAnyActiveTotem(PlayerEntity player) {
		for (ItemStack stack : player.getInventory().main) {
			if (isActiveTotem(stack)) return true;
		}
		for (ItemStack stack : player.getInventory().offHand) {
			if (isActiveTotem(stack)) return true;
		}
		return false;
	}

	/** 旧档兼容：清掉背包内「有 ACTIVE 无 OWNER」的旧格式标记（改动前激活的图腾自然降级为普通图腾）。 */
	private static void purgeLegacyMarks(PlayerEntity player) {
		for (ItemStack stack : player.getInventory().main) {
			if (isActiveTotem(stack) && !stack.getNbt().containsUuid(OWNER_TAG)) deactivateTotem(stack);
		}
		for (ItemStack stack : player.getInventory().offHand) {
			if (isActiveTotem(stack) && !stack.getNbt().containsUuid(OWNER_TAG)) deactivateTotem(stack);
		}
	}

	private static void deactivateTotem(ItemStack stack) {
		NbtCompound nbt = stack.getOrCreateNbt();
		nbt.remove(ACTIVE_TAG);
		nbt.remove(OWNER_TAG);
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

		// 旧档兼容：清除背包内遗留的旧格式激活标记（无 OWNER_TAG 的 ACTIVE 标记不再有效）
		purgeLegacyMarks(player);

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
			// 激活：三条新规则（2026-10-20）
			//   R1 激活冷却 20 秒（会话级静态 Map，按服务器世界时间计时）
			//   R2 背包全局唯一：已持有任一激活图腾则拒绝激活第二个
			//   R3 离身失效：激活时打 owner 标记；掉落物/存容器经 mixin 清除；tick 校验归属
			long now = player.getWorld().getTime();
			long cooldownTicks = BAL.i("activate_cooldown_ticks", ACTIVATE_COOLDOWN_TICKS);
			Long lastActivate = ACTIVATE_COOLDOWNS.get(player.getUuid());
			if (lastActivate != null && now - lastActivate < cooldownTicks) {
				player.sendMessage(Text.translatable("message.ssc_addon.totem.cooldown"), true);
				player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
				return TypedActionResult.fail(stack);
			}

			// R2 背包全局唯一：已持有任一激活图腾则拒绝激活第二个
			if (hasAnyActiveTotem(player)) {
				player.sendMessage(Text.translatable("message.ssc_addon.totem.already_active"), true);
				player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
				return TypedActionResult.fail(stack);
			}

			// 激活
			nbt.putBoolean(ACTIVE_TAG, true);
			nbt.putUuid(OWNER_TAG, player.getUuid());

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

			// R1 冷却写入
			ACTIVATE_COOLDOWNS.put(player.getUuid(), now);

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

		// Get nearby players within range（balance 快照可覆盖救援范围）
		Box box = entity.getBoundingBox().expand(BAL.d("range", RANGE));
		List<PlayerEntity> nearbyPlayers = entity.getWorld().getEntitiesByClass(PlayerEntity.class, box, p -> p instanceof ServerPlayerEntity);

		for (PlayerEntity player : nearbyPlayers) {
			if (!(player instanceof ServerPlayerEntity serverPlayer)) continue;

			// a. Check if they are SP Allay
			if (!isSpAllay(serverPlayer)) continue;

			// b. Check whitelist：自我救援总是允许；救他人统一走 isBuffTarget
			//   - whitelistEnabled = false：仅作用于非怪物/非敌对生物
			//   - whitelistEnabled = true：白名单空时玩家/驯服宠物/owner-tag；非空时仅白名单成员
			if (entity != serverPlayer) {
				if (!net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.isBuffTarget(serverPlayer, entity)) {
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
		// R3：只认「本人激活」的图腾（捡到/转来的别人激活图腾不会给救援效果）
		if (isOwnedActiveTotem(player.getMainHandStack(), player)) return player.getMainHandStack();
		if (isOwnedActiveTotem(player.getOffHandStack(), player)) return player.getOffHandStack();

		// Check inventory main
		for (ItemStack stack : player.getInventory().main) {
			if (isOwnedActiveTotem(stack, player)) return stack;
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

	/** R3：激活图腾 + 归属校验（owner 标记必须等于该玩家 uuid）。 */
	private static boolean isOwnedActiveTotem(ItemStack stack, PlayerEntity player) {
		return isActiveTotem(stack)
				&& stack.getNbt().containsUuid(OWNER_TAG)
				&& stack.getNbt().getUuid(OWNER_TAG).equals(player.getUuid());
	}

	// ---- R3 静态清除入口（mixin 调用：掉落物 tick / 容器 setStack）----

	/** 是否为激活图腾（mixin 用；只看标记，不看归属——离身场景一律清除）。 */
	public static boolean isMarkedActiveTotem(ItemStack stack) {
		return isActiveTotem(stack);
	}

	/** 清除图腾激活标记（mixin 用；同步清 owner 与光效 NBT）。 */
	public static void clearTotemMark(ItemStack stack) {
		if (stack.isOf(Items.TOTEM_OF_UNDYING) && stack.hasNbt()) {
			deactivateTotem(stack);
		}
	}

	private static boolean isSpAllay(PlayerEntity player) {
		return player instanceof ServerPlayerEntity sp && PowerUtils.isSpAllay(sp);
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
		ACTIVATE_COOLDOWNS.remove(player.getUuid());
	}

	public static void clearAll() {
		playersWithActiveTotems.clear();
		ACTIVATE_COOLDOWNS.clear();
	}
}
