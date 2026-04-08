package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 侵蚀烙印（被动标记系统）
 *
 * 近战攻击非白名单目标时自动叠加侵蚀烙印，每秒最多叠1层，每个目标最多3层。
 * 烙印持续10秒（每次叠加刷新计时器）。
 *
 * 层数颜色对应（用于entity_glow）：
 * - 1层 → 黄色
 * - 2层 → 橙色
 * - 3层 → 红色
 * - 绿色状态 → 已触发被动爆发，10秒内不可再次引爆
 *
 * 第4次攻击（3层满后继续攻击）触发被动爆发：
 * - 造成目标当前生命值10%的伤害（上限30）
 * - 自身回复10%最大生命值
 * - 清除所有层数，进入绿色状态（10秒）
 * - 绿色状态期间该目标不再叠层
 */
public class GoldenSandstormErosionBrand {

	// ==================== 常量 ====================
	/** 烙印持续时间（tick），每次叠加刷新 */
	private static final int BRAND_DURATION = 200; // 10秒
	/** 绿色状态持续时间（tick） */
	private static final int GREEN_DURATION = 200; // 10秒
	/** 最大叠加层数 */
	private static final int MAX_STACKS = 3;
	/** 被动爆发生命百分比伤害 */
	private static final float BURST_HP_PERCENT = 0.10f;
	/** 被动爆发伤害上限 */
	private static final float BURST_DAMAGE_CAP = 30.0f;
	/** 被动爆发自愈百分比 */
	private static final float BURST_HEAL_PERCENT = 0.10f;
	/** 叠层冷却（tick），同一目标1秒内最多叠1层 */
	private static final long STACK_COOLDOWN = 20L;

	// ==================== 网络同步 ====================
	/** S2C 包ID：同步烙印颜色数据到客户端 */
	public static final Identifier PACKET_BRAND_SYNC = new Identifier("ssc_addon", "erosion_brand_sync");
	/** 需要同步的玩家标记集合 */
	private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

	// ==================== 状态追踪 ====================
	/** 玩家UUID -> { 目标UUID -> 烙印状态 } */
	private static final ConcurrentHashMap<UUID, Map<UUID, BrandState>> ACTIVE_BRANDS = new ConcurrentHashMap<>();

	private GoldenSandstormErosionBrand() {
	}

	/**
	 * 近战攻击时调用 - 自动叠加烙印
	 * 该方法由 SscAddonActions 的 erosion_brand_hit biEntity action 触发
	 */
	public static void onPlayerAttack(ServerPlayerEntity player, LivingEntity target) {
		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;
		// 白名单检查
		if (WhitelistUtils.isProtected(player, target)) return;

		UUID playerUuid = player.getUuid();
		UUID targetUuid = target.getUuid();
		long currentTick = serverWorld.getTime();

		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.computeIfAbsent(playerUuid, k -> new HashMap<>());
		BrandState state = playerBrands.get(targetUuid);

		if (state != null && state.greenState) {
			// 绿色状态期间：不叠层，不触发被动爆发
			return;
		}
		// 标记需要同步
		markDirty(playerUuid);

		if (state != null && state.stacks >= MAX_STACKS) {
			// 已满3层，第4次攻击触发被动爆发
			triggerPassiveBurst(player, target, serverWorld);

			// 清除层数，进入绿色状态
			state.stacks = 0;
			state.greenState = true;
			state.greenExpiryTick = currentTick + GREEN_DURATION;
			state.expiryTick = currentTick + GREEN_DURATION; // 绿色状态期间保持跟踪

			// 同步标记效果到客户端（放在粒子/音效前确保即时生效）
			updateTargetMarker(target, state, currentTick);

			// 爆发音效和粒子
			serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					30, 0.5, 0.5, 0.5, 0.1);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					1, 0, 0, 0, 0);
			return;
		}

		// 正常叠层
		if (state == null) {
			state = new BrandState();
			playerBrands.put(targetUuid, state);
		}

		// 检查叠层冷却（1秒内最多叠1层）
		if (currentTick - state.lastStackTick < STACK_COOLDOWN) {
			// 冷却中，刷新持续时间但不叠层
			state.expiryTick = currentTick + BRAND_DURATION;
			updateTargetMarker(target, state, currentTick); // 刷新标记持续时间
			return;
		}

		state.stacks = Math.min(state.stacks + 1, MAX_STACKS);
		state.lastStackTick = currentTick;
		state.expiryTick = currentTick + BRAND_DURATION;

		// 同步标记效果到客户端
		updateTargetMarker(target, state, currentTick);

		// 叠加粒子提示
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
				target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
				5 * state.stacks, 0.2, 0.2, 0.2, 0.05);

		// 叠加音效
		serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 0.8f + state.stacks * 0.2f);
	}

	/**
	 * 触发被动爆发 - 造成10%当前生命伤害（上限30），自我回复10%最大生命
	 */
	private static void triggerPassiveBurst(ServerPlayerEntity player, LivingEntity target, ServerWorld serverWorld) {
		// 计算伤害：目标当前生命值的10%，上限30
		float damage = Math.min(target.getHealth() * BURST_HP_PERCENT, BURST_DAMAGE_CAP);
		if (damage < 1.0f) damage = 1.0f;

		// 造成魔法伤害
		RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
		Vec3d oldVelocity = target.getVelocity();
		if (target.damage(target.getDamageSources().create(magicKey, player, player), damage)) {
			target.setVelocity(oldVelocity); // 不击退
		}

		// 自我回复10%最大生命值
		float healAmount = player.getMaxHealth() * BURST_HEAL_PERCENT;
		player.heal(healAmount);
	}

	/**
	 * 次要技能引爆 - 引爆所有非绿色标记的目标
	 * @param player 施法者
	 * @return 引爆信息：[引爆目标数, 总层数]
	 */
	public static int[] detonateAll(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		if (playerBrands == null || playerBrands.isEmpty()) return new int[]{0, 0};

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return new int[]{0, 0};

		int totalTargets = 0;
		int totalStacks = 0;

		Iterator<Map.Entry<UUID, BrandState>> it = playerBrands.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, BrandState> entry = it.next();
			BrandState state = entry.getValue();

			// 绿色状态的目标不参与引爆
			if (state.greenState || state.stacks <= 0) continue;

			LivingEntity target = findEntity(serverWorld, entry.getKey());
			if (target == null || !target.isAlive()) {
				it.remove();
				continue;
			}

			// 如果目标有3层，先触发被动爆发（不消耗层数）
			if (state.stacks >= MAX_STACKS) {
				triggerPassiveBurst(player, target, serverWorld);
				// 被动爆发的额外粒子
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
						target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
						1, 0, 0, 0, 0);
			}

			totalTargets++;
			totalStacks += state.stacks;

			// 发出引爆伤害 = 每层5点物理伤害
			float detonateDamage = state.stacks * 5.0f;
			Vec3d oldVelocity = target.getVelocity();
			if (target.damage(target.getDamageSources().playerAttack(player), detonateDamage)) {
				target.setVelocity(oldVelocity);
			}

			// 引爆粒子
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					20 * state.stacks, 0.5, 0.5, 0.5, 0.1);

			// 进入绿色状态
			long currentTick = serverWorld.getTime();
			state.stacks = 0;
			state.greenState = true;
			state.greenExpiryTick = currentTick + GREEN_DURATION;
			state.expiryTick = currentTick + GREEN_DURATION;

			// 同步标记效果到客户端
			updateTargetMarker(target, state, currentTick);
		}

		// 自我回复：10%最大生命 × 总层数
		if (totalStacks > 0) {
			float healAmount = player.getMaxHealth() * BURST_HEAL_PERCENT * totalStacks;
			player.heal(healAmount);
			markDirty(player.getUuid());
		}

		return new int[]{totalTargets, totalStacks};
	}

	/**
	 * 获取指定玩家对指定目标的烙印颜色
	 * @return "yellow"/"orange"/"red"/"green"/null
	 */
	public static String getBrandColor(UUID playerUuid, UUID targetUuid) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(playerUuid);
		if (playerBrands == null) return null;
		return getColorFromState(playerBrands.get(targetUuid));
	}

	/**
	 * 检查指定玩家对指定目标是否有某颜色的烙印
	 * 提供给 BiEntity 条件使用
	 */
	public static boolean hasColor(UUID playerUuid, UUID targetUuid, String color) {
		String currentColor = getBrandColor(playerUuid, targetUuid);
		return color.equals(currentColor);
	}

	/**
	 * 每tick更新 - 清理过期烙印
	 */
	public static void tick(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		if (playerBrands == null) return;

		// 形态检查
		if (!FormUtils.isGoldenSandstormSP(player)) {
			// 脱离形态，清理所有目标的标记效果
			if (player.getWorld() instanceof ServerWorld serverWorld) {
				for (UUID targetUuid : playerBrands.keySet()) {
					LivingEntity target = findEntity(serverWorld, targetUuid);
					if (target != null) removeAllBrandMarkers(target);
				}
			}
			ACTIVE_BRANDS.remove(player.getUuid());
			return;
		}

		long currentTick = player.getServerWorld().getTime();
		ServerWorld serverWorld = player.getServerWorld();
		boolean[] hadExpiry = {false};
		playerBrands.entrySet().removeIf(entry -> {
			BrandState state = entry.getValue();
			boolean expired = false;
			// 绿色状态过期检查
			if (state.greenState && currentTick >= state.greenExpiryTick) {
				expired = true;
			}
			// 普通烙印过期检查
			if (!state.greenState && currentTick >= state.expiryTick) {
				expired = true;
			}
			if (expired) {
				hadExpiry[0] = true;
				// 移除目标的标记效果
				LivingEntity target = findEntity(serverWorld, entry.getKey());
				if (target != null) removeAllBrandMarkers(target);
			}
			return expired;
		});
		if (hadExpiry[0]) {
			markDirty(player.getUuid());
		}

		// 如果该玩家没有任何活跃烙印了，清除整个map
		if (playerBrands.isEmpty()) {
			ACTIVE_BRANDS.remove(player.getUuid());
		}

		// 同步烙印状态到客户端（仅在父变时发送）
		syncIfDirty(player);
	}

	/**
	 * 玩家断线/变形时清理，同时移除目标上的标记效果并发送空同步包
	 */
	public static void clearPlayer(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.remove(player.getUuid());
		if (playerBrands != null && player.getWorld() instanceof ServerWorld serverWorld) {
			for (UUID targetUuid : playerBrands.keySet()) {
				LivingEntity target = findEntity(serverWorld, targetUuid);
				if (target != null) removeAllBrandMarkers(target);
			}
		}
		DIRTY_PLAYERS.remove(player.getUuid());
		// 发送空同步包到客户端
		try {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeInt(0);
			ServerPlayNetworking.send(player, PACKET_BRAND_SYNC, buf);
		} catch (Exception ignored) {
			// 玩家可能已断线，忽略发送失败
		}
	}

	/**
	 * 服务器重启/热重载时清理所有状态
	 */
	public static void clearAll() {
		ACTIVE_BRANDS.clear();
		DIRTY_PLAYERS.clear();
	}

	// ==================== 网络同步方法 ====================

	/**
	 * 标记玩家的烙印数据需要同步到客户端
	 */
	private static void markDirty(UUID playerUuid) {
		DIRTY_PLAYERS.add(playerUuid);
	}

	/**
	 * 如果玩家的烙印数据已变化，同步到其客户端
	 * 在 tick 结束时调用，避免每次变化都发包
	 */
	private static void syncIfDirty(ServerPlayerEntity player) {
		if (!DIRTY_PLAYERS.remove(player.getUuid())) return;

		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		PacketByteBuf buf = PacketByteBufs.create();

		if (playerBrands == null || playerBrands.isEmpty()) {
			buf.writeInt(0);
		} else {
			// 收集有效颜色条目
			List<Map.Entry<UUID, String>> entries = new ArrayList<>();
			for (Map.Entry<UUID, BrandState> entry : playerBrands.entrySet()) {
				String color = getColorFromState(entry.getValue());
				if (color != null) {
					entries.add(Map.entry(entry.getKey(), color));
				}
			}
			buf.writeInt(entries.size());
			for (Map.Entry<UUID, String> entry : entries) {
				buf.writeUuid(entry.getKey());
				buf.writeString(entry.getValue());
			}
		}

		ServerPlayNetworking.send(player, PACKET_BRAND_SYNC, buf);
	}

	/**
	 * 从 BrandState 获取颜色字符串
	 */
	private static String getColorFromState(BrandState state) {
		if (state == null) return null;
		if (state.greenState) return "green";
		return switch (state.stacks) {
			case 1 -> "yellow";
			case 2 -> "orange";
			case 3 -> "red";
			default -> null;
		};
	}

	/**
	 * 同步烙印状态到客户端 - 通过状态效果实现
	 * 拆分为3个独立效果以支持不同层数显示不同图标：
	 * erosion_brand_marker_1: 1层(黄色), erosion_brand_marker_2: 2层(橙色), erosion_brand_marker_3: 3层(红色)
	 * 绿色冷却状态不显示图标。
	 */
	private static void updateTargetMarker(LivingEntity target, BrandState state, long currentTick) {
		if (state == null || (state.stacks <= 0 && !state.greenState)) {
			removeAllBrandMarkers(target);
			return;
		}
		int duration;
		if (state.greenState) {
			// 绿色冷却状态：不显示图标，移除所有标记
			removeAllBrandMarkers(target);
			return;
		} else {
			duration = (int) (state.expiryTick - currentTick);
		}
		if (duration <= 0) {
			removeAllBrandMarkers(target);
			return;
		}
		// 先移除所有旧标记，再施加当前层数对应的标记
		removeAllBrandMarkers(target);
		net.minecraft.entity.effect.StatusEffect markerEffect = switch (state.stacks) {
			case 1 -> SscAddon.EROSION_BRAND_MARKER_1;
			case 2 -> SscAddon.EROSION_BRAND_MARKER_2;
			case 3 -> SscAddon.EROSION_BRAND_MARKER_3;
			default -> null;
		};
		if (markerEffect != null) {
			// ambient=false, showParticles=false, showIcon=true（显示对应层数图标）
			target.addStatusEffect(new StatusEffectInstance(
					markerEffect, duration, 0, false, false, true
			));
		}
	}

	/**
	 * 移除目标上的所有烙印标记效果
	 */
	private static void removeAllBrandMarkers(LivingEntity target) {
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_1);
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_2);
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_3);
	}

	/**
	 * 在世界中查找目标实体
	 */
	private static LivingEntity findEntity(ServerWorld world, UUID uuid) {
		var entity = world.getEntity(uuid);
		return entity instanceof LivingEntity living ? living : null;
	}

	// ==================== 内部数据类 ====================
	private static class BrandState {
		int stacks = 0;
		boolean greenState = false;
		long lastStackTick = -100; // 上次叠层时间
		long expiryTick = 0; // 烙印过期时间
		long greenExpiryTick = 0; // 绿色状态过期时间
	}
}
