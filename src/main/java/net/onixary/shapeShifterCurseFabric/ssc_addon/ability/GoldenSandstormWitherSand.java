package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 凋零金沙（主动①，蓄力型）
 * <p>
 * 按下技能键开始蓄力（1秒），自身减速50%。
 * 蓄力完成后释放15格AoE金沙风暴：
 * - 对范围内非白名单生物施加致盲效果（3秒）
 * - 给范围内目标各叠加1层侵蚀烙印
 * - 大量沙尘粒子效果
 * <p>
 * 蓄力期间受到伤害则打断：取消技能，进入7秒CD。
 * 正常释放CD: 26秒（520tick）
 */
public class GoldenSandstormWitherSand {

	// ==================== 常量 ====================
	/** AoE半径 */
	private static final double RADIUS = 15.0;
	/** 致盲持续时间（tick） */
	private static final int BLIND_DURATION = 60; // 3秒
	/** 蓄力时间（tick） */
	private static final int CHARGE_TICKS = 20; // 1秒
	/** 正常CD时间（tick） */
	private static final int COOLDOWN_TICKS = 520; // 26秒
	/** 被打断CD时间（tick） */
	private static final int INTERRUPT_CD_TICKS = 140; // 7秒
	/** 蓄力减速修正器UUID */
	private static final UUID CHARGE_SLOW_UUID = UUID.fromString("b8c9d0e1-f2a3-4b5c-8d6e-7f8901234567");
	private static final String CHARGE_SLOW_NAME = "Wither Sand Charge Slow";

	// ==================== 状态追踪 ====================
	/** 正在蓄力的玩家：UUID -> 蓄力状态 */
	private static final ConcurrentHashMap<UUID, ChargeState> CHARGING_PLAYERS = new ConcurrentHashMap<>();

	private GoldenSandstormWitherSand() {
	}

	/**
	 * 玩家按下技能键 - 开始蓄力
	 */
	public static boolean execute(ServerPlayerEntity player) {
		// CD检查
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD);
		if (cd > 0) return false;

		// 已经在蓄力中则忽略
		if (CHARGING_PLAYERS.containsKey(player.getUuid())) return false;

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

		// 开始蓄力
		ChargeState state = new ChargeState();
		state.startTick = serverWorld.getTime();
		state.healthAtStart = player.getHealth();
		CHARGING_PLAYERS.put(player.getUuid(), state);

		// 施加减速50%
		applyChargeSlow(player);

		// 蓄力开始音效
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);

		return true;
	}

	/**
	 * 每tick更新蓄力状态
	 * 需要在 SscAddon 的 tick handler 中调用
	 */
	public static void tick(ServerPlayerEntity player) {
		ChargeState state = CHARGING_PLAYERS.get(player.getUuid());
		if (state == null) return;

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;

		// 形态检查
		if (!FormUtils.isGoldenSandstormSP(player)) {
			cancelCharge(player);
			return;
		}

		long currentTick = serverWorld.getTime();
		long elapsed = currentTick - state.startTick;

		// 检查是否被打断（生命值下降 = 受到伤害）
		if (player.getHealth() < state.healthAtStart) {
			// 被打断：进入7秒CD
			cancelCharge(player);
			PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, INTERRUPT_CD_TICKS);

			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_SAND_FALL, SoundCategory.PLAYERS, 1.0f, 0.3f);
			return;
		}

		// 蓄力期间粒子（自身周围旋转沙尘）
		if (elapsed % 4 == 0) {
			double angle = (elapsed * 0.3) % (Math.PI * 2);
			double px = player.getX() + Math.cos(angle) * 1.5;
			double pz = player.getZ() + Math.sin(angle) * 1.5;
			serverWorld.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SAND.getDefaultState()),
					px, player.getY() + 1.0, pz, 3, 0.1, 0.3, 0.1, 0);
		}

		// 蓄力完成
		if (elapsed >= CHARGE_TICKS) {
			removeChargeSlow(player);
			CHARGING_PLAYERS.remove(player.getUuid());
			releaseSkill(player, serverWorld);
		}
	}

	/**
	 * 释放技能 - 15格AoE
	 */
	private static void releaseSkill(ServerPlayerEntity player, ServerWorld serverWorld) {
		// 设置正常CD
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, COOLDOWN_TICKS);

		// 释放音效
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.5f, 0.5f);

		// 大量粒子效果：金色沙尘风暴
		for (int i = 0; i < 100; i++) {
			double angle = Math.random() * Math.PI * 2;
			double dist = Math.random() * RADIUS;
			double px = player.getX() + Math.cos(angle) * dist;
			double pz = player.getZ() + Math.sin(angle) * dist;
			double py = player.getY() + Math.random() * 2.5;
			serverWorld.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SAND.getDefaultState()),
					px, py, pz, 1, 0, 0, 0, 0);
		}
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL,
				player.getX(), player.getY() + 1.0, player.getZ(),
				40, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.02);

		// 获取范围内生物
		Box box = player.getBoundingBox().expand(RADIUS);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= RADIUS * RADIUS);

		for (LivingEntity target : targets) {
			// 白名单检查
			if (WhitelistUtils.isProtected(player, target)) continue;

			// 施加致盲效果（3秒）
			target.addStatusEffect(new StatusEffectInstance(SscAddon.SAND_BLIND, BLIND_DURATION, 0, false, true));

			// 叠加1层侵蚀烙印
			GoldenSandstormErosionBrand.onPlayerAttack(player, target);

			// 命中粒子
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					5, 0.3, 0.3, 0.3, 0.02);
		}
	}

	/**
	 * 取消蓄力
	 */
	private static void cancelCharge(ServerPlayerEntity player) {
		removeChargeSlow(player);
		CHARGING_PLAYERS.remove(player.getUuid());
	}

	/**
	 * 施加蓄力减速
	 */
	private static void applyChargeSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(CHARGE_SLOW_UUID);
			speedAttr.addTemporaryModifier(new EntityAttributeModifier(
					CHARGE_SLOW_UUID, CHARGE_SLOW_NAME, -0.50,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/**
	 * 移除蓄力减速
	 */
	private static void removeChargeSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(CHARGE_SLOW_UUID);
		}
	}

	/**
	 * 玩家断线/变形时清理
	 */
	public static void clearPlayer(ServerPlayerEntity player) {
		if (CHARGING_PLAYERS.containsKey(player.getUuid())) {
			removeChargeSlow(player);
			CHARGING_PLAYERS.remove(player.getUuid());
		}
	}

	/**
	 * 服务器重启/热重载时清理所有状态
	 */
	public static void clearAll() {
		CHARGING_PLAYERS.clear();
	}

	// ==================== 内部数据类 ====================
	private static class ChargeState {
		long startTick;
		float healthAtStart;
	}
}
