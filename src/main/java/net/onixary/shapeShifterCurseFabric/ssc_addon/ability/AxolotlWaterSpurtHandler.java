package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.AxolotlTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「水流冲刺」（water_spurt 节点）—— 服务端可靠实现，水陆两套触发彻底隔离。
 *
 * <p><b>水下冲刺</b>（同 SSC {@code form_axolotl_2_water_spurt}）：在水中按<b>疾跑键</b>（sprint 上升沿）→
 * 沿视线前冲，<b>不消耗湿润度</b>（与 SSC 一致），独立冷却 5 秒。</p>
 * <p><b>陆地冲刺</b>：在陆地上疾跑时按<b>潜行键</b>（sneak 上升沿且当前/上一 tick 处于疾跑）→
 * 同样的前冲位移，<b>消耗 {@link #LAND_MOISTURE_COST} 点湿润度(air)</b>，独立冷却 5 秒。</p>
 * <p>用 {@code isTouchingWater()} 区分水/陆，两分支互斥（水里只走疾跑冲刺、不响应潜行；陆地只走潜行冲刺、
 * 不响应疾跑），且各自独立冷却，互不干扰。已解锁 water_spurt 节点才生效。</p>
 */
public final class AxolotlWaterSpurtHandler {

	private static final int CD_TICKS = 100;   // 5 秒
	private static final double BURST = 1.6;    // 前冲力度（水陆一致）
	/** 陆地冲刺消耗的湿润度（air 值；参照 WaterSpearLeapManager 的 18=6%，冲刺更轻，取 12≈4%）。水下冲刺同 SSC 免费。 */
	private static final int LAND_MOISTURE_COST = 12;

	private static final Map<UUID, Boolean> WAS_SPRINTING = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> WAS_SNEAKING = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> WATER_CD = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LAND_CD = new ConcurrentHashMap<>();

	private AxolotlWaterSpurtHandler() {
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		int wcd = WATER_CD.getOrDefault(id, 0);
		if (wcd > 0) WATER_CD.put(id, wcd - 1);
		int lcd = LAND_CD.getOrDefault(id, 0);
		if (lcd > 0) LAND_CD.put(id, lcd - 1);

		if (!FormUtils.isUpgradeAxolotl(player)) {
			WAS_SPRINTING.remove(id);
			WAS_SNEAKING.remove(id);
			return;
		}

		boolean sprinting = player.isSprinting();
		boolean sneaking = player.isSneaking();
		boolean wasSprinting = WAS_SPRINTING.getOrDefault(id, false);
		boolean wasSneaking = WAS_SNEAKING.getOrDefault(id, false);
		WAS_SPRINTING.put(id, sprinting);
		WAS_SNEAKING.put(id, sneaking);

		// 未解锁 water_spurt 节点：只更新状态、不触发任何冲刺
		if (!RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_WATER_SPURT)) {
			return;
		}

		boolean inWater = player.isTouchingWater();

		if (inWater) {
			// ===== 水下冲刺：水里 + 按疾跑（sprint 上升沿）→ 前冲，免费（同 SSC）=====
			if (sprinting && !wasSprinting && WATER_CD.getOrDefault(id, 0) <= 0) {
				doDash(player);
				WATER_CD.put(id, CD_TICKS);
			}
			// 水里只响应疾跑分支，隔离陆地潜行逻辑
			return;
		}

		// ===== 陆地冲刺：陆地 + 疾跑时按潜行（sneak 上升沿且当前/上一 tick 疾跑）→ 前冲，消耗湿润度 =====
		if (sneaking && !wasSneaking && (sprinting || wasSprinting) && LAND_CD.getOrDefault(id, 0) <= 0) {
			if (player.getAir() < LAND_MOISTURE_COST) {
				return; // 湿润度不足，不触发
			}
			player.setAir(player.getAir() - LAND_MOISTURE_COST);
			doDash(player);
			LAND_CD.put(id, CD_TICKS);
		}
	}

	/** 沿视线前冲一次（水陆一致的位移与视听反馈）。 */
	private static void doDash(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVector();
		player.addVelocity(look.x * BURST, look.y * BURST, look.z * BURST);
		player.velocityModified = true;

		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 1.0f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.3f, 0.7f);
		sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6, player.getZ(),
				60, 0.6, 0.4, 0.6, 0.6);
		sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.5, player.getZ(),
				40, 0.5, 0.4, 0.5, 0.5);
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnWaterBurst(
				sw, player.getX(), player.getY() + 0.6, player.getZ(), 1.2);
	}

	public static void onPlayerDisconnect(UUID id) {
		WAS_SPRINTING.remove(id);
		WAS_SNEAKING.remove(id);
		WATER_CD.remove(id);
		LAND_CD.remove(id);
	}
}
