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
 * 进化美西螈「水流爆破」（water_spurt 节点）—— 服务端可靠实现。
 *
 * <p>触发：<b>疾跑时按 shift（下蹲键）</b>。检测 sneak 上升沿且当前/上一 tick 处于疾跑，
 * 沿视线方向施加一次前冲爆发（{@code addVelocity + velocityModified}，同击退机制，对玩家可靠）。
 * 已解锁 water_spurt 节点、不在内部冷却时才触发。冷却 5 秒。</p>
 */
public final class AxolotlWaterSpurtHandler {

	private static final int CD_TICKS = 100;   // 5 秒
	private static final double BURST = 1.6;    // 前冲力度

	private static final Map<UUID, Boolean> WAS_SPRINTING = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> WAS_SNEAKING = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

	private AxolotlWaterSpurtHandler() {
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		int cd = COOLDOWN.getOrDefault(id, 0);
		if (cd > 0) COOLDOWN.put(id, cd - 1);

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

		// 疾跑时按 shift：sneak 上升沿 + 本/上一 tick 处于疾跑
		if (sneaking && !wasSneaking && (sprinting || wasSprinting)) {
			boolean unlocked = RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_WATER_SPURT);
			if (unlocked && COOLDOWN.getOrDefault(id, 0) <= 0) {
				// 与原版美西螈水流爆破一致：add_velocity z 1.6 space local（沿视线前方冲量）
				Vec3d look = player.getRotationVector();
				player.addVelocity(look.x * BURST, look.y * BURST, look.z * BURST);
				player.velocityModified = true;
				COOLDOWN.put(id, CD_TICKS);

				ServerWorld sw = (ServerWorld) player.getWorld();
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 1.0f);
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.3f, 0.7f);
				// 水花爆开（爆破视觉）
				sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6, player.getZ(),
						60, 0.6, 0.4, 0.6, 0.6);
				sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.5, player.getZ(),
						40, 0.5, 0.4, 0.5, 0.5);
				net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnWaterBurst(
						sw, player.getX(), player.getY() + 0.6, player.getZ(), 1.2);
			}
		}
	}

	public static void onPlayerDisconnect(UUID id) {
		WAS_SPRINTING.remove(id);
		WAS_SNEAKING.remove(id);
		COOLDOWN.remove(id);
	}
}
