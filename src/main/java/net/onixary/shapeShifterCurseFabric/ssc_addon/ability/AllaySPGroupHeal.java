package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SkillBlocker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SP悦灵群体治疗 - 白名单治疗系统
 * <p>
 * 当JSON蓄力完成后设置 heal_execute 资源为1，
 * 此类的tick检测到后执行白名单过滤治疗，然后重置为0。
 * <p>
 * 白名单存储在SP悦灵玩家的 command tags 中，
 * 格式: "ssc_allay_wl:<目标玩家UUID>"
 */
public class AllaySPGroupHeal {

	public static final String WHITELIST_TAG_PREFIX = "ssc_allay_wl:";
	private static final Identifier HEAL_EXECUTE_ID = new Identifier("my_addon", "form_allay_sp_group_heal_heal_execute");
	private static final Identifier SOLO_DAMAGE_TIMER_ID = FormIdentifiers.ALLAY_GROUP_HEAL_SOLO_DAMAGE_TIMER;
	private static final double HEAL_RADIUS = 20.0;
	private static final int RESISTANCE_TICKS = 200;
	private static final int SOLO_BLESSING_TICKS = 400;
	// 群体治疗：每个目标回血 = 最大生命×75%，额外给 最大生命×50% 的黄心，持续30秒
	private static final float HEAL_PERCENT = 0.75f;
	private static final float ABSORPTION_PERCENT = 0.5f;
	private static final int HEAL_ABSORPTION_TICKS = 600;

	private AllaySPGroupHeal() {
		throw new UnsupportedOperationException("This class cannot be instantiated.");
	}

	/**
	 * 每tick检查是否需要执行治疗
	 */
	public static void tick(ServerPlayerEntity player) {
		if (SkillBlocker.isSkillBlocked(player, "allay", "group_heal")) {
			return;
		}
		int healExecute = getResourceValue(player, HEAL_EXECUTE_ID);
		if (healExecute != 1) return;

		// 立即重置触发器（不同步，避免重置飘浮计时器）
		setResourceValueNoSync(player, HEAL_EXECUTE_ID, 0);

		// 执行白名单过滤治疗
		executeWhitelistHeal(player);
	}

	/**
	 * 执行白名单过滤的群体治疗
	 * 白名单为空时治疗所有活体（原版行为）；有白名单时只治疗白名单内的玩家及其召唤物
	 */
	private static void executeWhitelistHeal(ServerPlayerEntity allayPlayer) {
		ServerWorld world = (ServerWorld) allayPlayer.getWorld();
		boolean soloHeal = !hasOtherHealablePlayer(allayPlayer, world);

		// 治疗自身
		if (soloHeal) {
			applySoloBlessing(allayPlayer);
		} else {
			healTarget(allayPlayer);
			allayPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, RESISTANCE_TICKS, 0, false, true, true));
		}
		spawnHealParticles(world, allayPlayer);
		// 只有SP悦灵自己能听见
		allayPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);

		// 获取范围内所有活体
		Box box = allayPlayer.getBoundingBox().expand(HEAL_RADIUS);
		List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != allayPlayer && e.isAlive() && e.squaredDistanceTo(allayPlayer) <= HEAL_RADIUS * HEAL_RADIUS);

		for (LivingEntity entity : entities) {
			// 统一走 WhitelistUtils.isBuffTarget，同时遵从服务端 whitelistEnabled 总开关
			if (WhitelistUtils.isBuffTarget(allayPlayer, entity)) {
				healTarget(entity);
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, RESISTANCE_TICKS, 0, false, true, true));
				spawnHealParticles(world, entity);
				// 播放声音：治疗者听见私有声音，其他人听见空间声音
				allayPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
				world.playSound(allayPlayer, entity.getX(), entity.getY(), entity.getZ(),
						SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
			}
		}
	}

	private static boolean hasOtherHealablePlayer(ServerPlayerEntity allayPlayer, ServerWorld world) {
		Box box = allayPlayer.getBoundingBox().expand(HEAL_RADIUS);
		List<ServerPlayerEntity> players = world.getEntitiesByClass(ServerPlayerEntity.class, box,
				player -> player != allayPlayer
						&& player.isAlive()
						&& !player.isSpectator()
						&& player.squaredDistanceTo(allayPlayer) <= HEAL_RADIUS * HEAL_RADIUS
						&& WhitelistUtils.isBuffTarget(allayPlayer, player));
		return !players.isEmpty();
	}

	private static void applySoloBlessing(ServerPlayerEntity allayPlayer) {
		healTarget(allayPlayer);
		allayPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, SOLO_BLESSING_TICKS, 1, false, true, true));
		allayPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, SOLO_BLESSING_TICKS, 1, false, true, true));
		setResourceValue(allayPlayer, SOLO_DAMAGE_TIMER_ID, SOLO_BLESSING_TICKS);
	}

	/** 治疗单个目标：回血=最大生命×75%，并给予 最大生命×50% 的黄心（持续30秒）。 */
	private static void healTarget(LivingEntity entity) {
		float maxHp = entity.getMaxHealth();
		entity.heal(maxHp * HEAL_PERCENT);
		int amplifier = Math.max(0, Math.round(maxHp * ABSORPTION_PERCENT / 2.0f) - 1);
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, HEAL_ABSORPTION_TICKS, amplifier, false, true, true));
	}

	/**
	 * 判断实体是否应被治疗
	 */
	public static boolean shouldHeal(LivingEntity entity, Set<String> allayTags) {
		// 如果是玩家，检查白名单
		if (entity instanceof ServerPlayerEntity targetPlayer) {
			return isInWhitelist(allayTags, targetPlayer.getUuid());
		}

		// 如果是可驯服的实体（狼、猫等），检查主人是否在白名单
		if (entity instanceof TameableEntity tameable) {
			UUID ownerUuid = tameable.getOwnerUuid();
			if (ownerUuid != null) {
				return isInWhitelist(allayTags, ownerUuid);
			}
			return false; // 无主的可驯服生物不治疗
		}

		// 检查 ssc_owner: command tag（通过 mark_owner 命令标记的实体）
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith("ssc_owner:")) {
				try {
					UUID ownerUuid = UUID.fromString(tag.substring("ssc_owner:".length()));
					if (isInWhitelist(allayTags, ownerUuid)) return true;
				} catch (IllegalArgumentException e) {
					// 无效UUID，跳过
				}
			}
		}

		// 最后检查实体本身的UUID是否直接在白名单中
		return isInWhitelist(allayTags, entity.getUuid());
	}

	/**
	 * 将目标实体的所有者（如果是宠物）或实体本身（如果是普通生物/玩家）加入白名单
	 */
	public static void addToWhitelist(ServerPlayerEntity allayPlayer, LivingEntity target) {
		if (target instanceof net.minecraft.entity.player.PlayerEntity) {
			// 玩家：只写 player 前缀
			allayPlayer.getCommandTags().add(WHITELIST_TAG_PREFIX + target.getUuid().toString());
		} else {
			// 任何非玩家生物（含宠物）：双写 player 前缀（兼容 isInWhitelist 判定）+ mob 前缀（GUI 用，带物种信息）
			// 注意：白名单玩家的所有宠物已通过"主人 UUID 在 player 白名单"路径默认受保护，
			// 这里写宠物自身 UUID 仅在该宠物被显式标记时生效，不影响主人的其它宠物。
			String uuidStr = target.getUuid().toString();
			allayPlayer.getCommandTags().add(WHITELIST_TAG_PREFIX + uuidStr);
			net.minecraft.util.Identifier typeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(target.getType());
			String mobTag = net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.WHITELIST_MOB_TAG_PREFIX
					+ uuidStr + ":" + typeId.getNamespace() + ":" + typeId.getPath();
			allayPlayer.getCommandTags().add(mobTag);
		}
	}

	/**
	 * 将目标实体的所有者（如果是宠物）或实体本身（如果是普通生物/玩家）从白名单移除
	 */
	public static void removeFromWhitelist(ServerPlayerEntity allayPlayer, LivingEntity target) {
		// 始终按 target 自身 UUID 移除（与 addToWhitelist 对称）
		allayPlayer.getCommandTags().remove(WHITELIST_TAG_PREFIX + target.getUuid().toString());
		// 同时清除生物白名单上的 mob 前缀 tag（包含剩余的 :ns:path 变体）
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.removeMobFromWhitelist(allayPlayer, target.getUuid());
	}

	/**
	 * 检查UUID是否在白名单中
	 */
	public static boolean isInWhitelist(Set<String> allayTags, UUID targetUuid) {
		return allayTags.contains(WHITELIST_TAG_PREFIX + targetUuid.toString());
	}

	/**
	 * 便捷方法：检查一个实体是否在玩家的白名单中（供唱片机等外部系统使用）
	 * 白名单为空时返回true（允许所有玩家和友好生物）
	 */
	public static boolean isInWhitelist(ServerPlayerEntity allayPlayer, LivingEntity entity) {
		// 统一走 WhitelistUtils.isBuffTarget，遵从 whitelistEnabled 总开关
		return WhitelistUtils.isBuffTarget(allayPlayer, entity);
	}

	/**
	 * 在实体位置生成爱心粒子
	 */
	private static void spawnHealParticles(ServerWorld world, LivingEntity entity) {
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, ParticleTypes.HEART,
				entity.getX(), entity.getY() + entity.getHeight() + 0.5, entity.getZ(),
				5, 0.3, 0.3, 0.3, 0.01);
	}

	// ===== 白名单管理方法（供 GUI / 网络层调用） =====
	// 旧的 SP-only 玩家重载（add/remove/clear）已随 /ssc_addon whitelist 指令一并删除。

	/**
	 * 从白名单按UUID移除
	 */
	public static boolean removeFromWhitelistByUuid(ServerPlayerEntity allayPlayer, UUID targetUuid) {
		String tag = WHITELIST_TAG_PREFIX + targetUuid.toString();
		return allayPlayer.getCommandTags().remove(tag);
	}

	/**
	 * 获取白名单中的所有UUID
	 */
	public static java.util.List<UUID> getWhitelistUuids(ServerPlayerEntity allayPlayer) {
		java.util.List<UUID> uuids = new java.util.ArrayList<>();
		for (String tag : allayPlayer.getCommandTags()) {
			if (tag.startsWith(WHITELIST_TAG_PREFIX)) {
				try {
					uuids.add(UUID.fromString(tag.substring(WHITELIST_TAG_PREFIX.length())));
				} catch (IllegalArgumentException e) {
					// 无效UUID，跳过
				}
			}
		}
		return uuids;
	}

		// ===== Apoli资源读写工具 =====

	private static int getResourceValue(ServerPlayerEntity player, Identifier resourceId) {
		return PowerUtils.getResourceValue(player, resourceId);
	}

	private static void setResourceValue(ServerPlayerEntity player, Identifier resourceId, int value) {
		PowerUtils.setResourceValueAndSync(player, resourceId, value);
	}

		/**
	 * 设置资源值但不同步到客户端，避免重置飘浮等power的内部计时器
	 * 仅用于不需要客户端感知的内部触发器（如 heal_execute）
	 */
	private static void setResourceValueNoSync(ServerPlayerEntity player, Identifier resourceId, int value) {
		PowerUtils.setResourceValue(player, resourceId, value);
	}
}
