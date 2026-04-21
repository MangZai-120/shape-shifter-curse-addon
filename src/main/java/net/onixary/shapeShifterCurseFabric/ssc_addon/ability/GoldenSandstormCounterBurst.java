package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

/**
 * 金沙岚SP - 反噬冲击（被动）
 * <p>
 * 被攻击时自动触发：
 * - 对周围4格内的非白名单生物造成小幅击退
 * - 附加凋零I效果5秒
 * - 冷却15秒
 * <p>
 * 白名单机制：默认白名单（白名单为空时玩家及其宠物/召唤物不受影响，非空时白名单内不受影响）
 */
public class GoldenSandstormCounterBurst {

	/** 反噬冲击范围（格） */
	private static final double BURST_RANGE = 4.0;
	/** 击退力度 */
	private static final double KNOCKBACK_STRENGTH = 0.5;
	/** 凋零效果持续时间（tick） */
	private static final int WITHER_DURATION = 100; // 5秒
	/** 凋零效果等级（0 = I级） */
	private static final int WITHER_AMPLIFIER = 0;
	/** 冷却时间（tick） */
	private static final int COOLDOWN_TICKS = 300; // 15秒

	private GoldenSandstormCounterBurst() {
	}

	/**
	 * 执行反噬冲击
	 * @param player 金沙岚SP玩家
	 * @return 是否成功触发
	 */
	public static boolean execute(ServerPlayerEntity player) {
		// CD检查
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.GOLDEN_SANDSTORM_COUNTER_BURST_CD);
		if (cd > 0) return false;

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

		// 搜索范围内的实体
		Box searchBox = player.getBoundingBox().expand(BURST_RANGE);
		java.util.List<Entity> allEntities = serverWorld.getOtherEntities(player, searchBox);

        for (Entity entity : allEntities) {
			if (!(entity instanceof LivingEntity living)) continue;
			if (!living.isAlive()) continue;
			if (living.getUuid().equals(player.getUuid())) continue;
			// 白名单检查
			if (WhitelistUtils.isProtected(player, living)) continue;

			double distSq = living.squaredDistanceTo(player);
			if (distSq > BURST_RANGE * BURST_RANGE) continue;

			// 计算击退方向：从玩家指向目标
			Vec3d direction = living.getPos().subtract(player.getPos());
			double dist = direction.length();
			if (dist > 0.01) {
				direction = direction.normalize();
				// 施加击退（水平+轻微上抛）
				living.setVelocity(living.getVelocity().add(
						direction.x * KNOCKBACK_STRENGTH,
						0.15,
						direction.z * KNOCKBACK_STRENGTH
				));
				living.velocityModified = true;
			}

			// 施加凋零效果
			living.addStatusEffect(new StatusEffectInstance(
					StatusEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER, false, true, true
			));

        }

		// 即使没有命中也进入CD（防止频繁触发检测）
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.GOLDEN_SANDSTORM_COUNTER_BURST_CD, COOLDOWN_TICKS);

		// 音效和粒子（无论是否命中都播放，提示玩家触发了反噬）
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.5f, 1.2f);
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
				player.getX(), player.getY() + 1.0, player.getZ(),
				25, 1.5, 0.5, 1.5, 0.05);
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SMOKE,
				player.getX(), player.getY() + 0.5, player.getZ(),
				15, 1.0, 0.3, 1.0, 0.05);

		return true;
	}
}
