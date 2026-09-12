package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 空间归途读条结算（jackcooper，服务端权威）。读条期间移动/受伤打断
 * （打断：CD 已扣不返还法力——由施法方正常结算，这里只负责传送或失败提示）。
 * 传送目标 = 施法时刻的重生点快照（打断重施会重新取）。
 */
public final class SpaceRecallManager {
	private static final List<Recall> RECALLS = new ArrayList<>();
	/** 打断判定的移动阈值（格²）。 */
	private static final double MOVE_INTERRUPT_SQ = 0.04;

	private static final class Recall {
		final UUID playerId;
		final ServerPlayerEntity player;
		final Vec3d startPos;
		final BlockPos spawnPos;
		int ticksRemaining;

		Recall(ServerPlayerEntity player, int channelTicks) {
			this.playerId = player.getUuid();
			this.player = player;
			this.startPos = player.getPos();
			this.spawnPos = player.getSpawnPointPosition();
			this.ticksRemaining = channelTicks;
		}
	}

	private SpaceRecallManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Recall> it = RECALLS.iterator();
			while (it.hasNext()) {
				Recall recall = it.next();
				if (recall.player.isRemoved()) {
					it.remove();
					continue;
				}
				// 打断判定：位移超阈值
				if (recall.player.getPos().squaredDistanceTo(recall.startPos) > MOVE_INTERRUPT_SQ) {
					interrupt(recall, "moved");
					it.remove();
					continue;
				}
				// 读条粒子（每 5t 一圈，收紧感）
				if (recall.player.getWorld() instanceof ServerWorld serverWorld && recall.ticksRemaining % 5 == 0) {
					serverWorld.spawnParticles(ParticleTypes.PORTAL,
							recall.player.getX(), recall.player.getBodyY(0.8), recall.player.getZ(),
							3, 0.4, 0.6, 0.4, 0.05);
				}
				if (--recall.ticksRemaining <= 0) {
					complete(recall);
					it.remove();
				}
			}
		});
		// 受伤打断：钩在伤害事件（比 mixin 轻）
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity sp) {
				RECALLS.removeIf(recall -> {
					if (recall.playerId.equals(sp.getUuid())) {
						interrupt(recall, "hurt");
						return true;
					}
					return false;
				});
			}
			return true; // 不否决伤害本身
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				RECALLS.removeIf(recall -> recall.playerId.equals(handler.player.getUuid())));
	}

	/** 开始读条（同玩家旧读条被覆盖）。 */
	public static void start(ServerPlayerEntity player, int channelTicks) {
		RECALLS.removeIf(recall -> recall.playerId.equals(player.getUuid()));
		RECALLS.add(new Recall(player, channelTicks));
	}

	/** 该玩家是否正在读条（供其它系统避让）。 */
	public static boolean isChanneling(ServerPlayerEntity player) {
		for (Recall recall : RECALLS) {
			if (recall.playerId.equals(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	private static void complete(Recall recall) {
		ServerWorld world = (ServerWorld) recall.player.getWorld();
		BlockPos dest = recall.spawnPos;
		if (dest == null) {
			interrupt(recall, "no_spawn");
			return;
		}
		// 起点消散
		world.spawnParticles(ParticleTypes.PORTAL,
				recall.player.getX(), recall.player.getBodyY(0.5), recall.player.getZ(), 24, 0.3, 0.5, 0.3, 0.1);
		// 传送到重生点（找不到安全点时走原版 respawn 语义安全落地）
		recall.player.teleport(dest.getX() + 0.5, dest.getY() + 0.2, dest.getZ() + 0.5);
		world.spawnParticles(ParticleTypes.END_ROD,
				dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.05);
		world.playSound(null, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		world.playSound(null, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.4f);
	}

	private static void interrupt(Recall recall, String reasonKey) {
		if (!(recall.player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		recall.player.sendMessage(net.minecraft.text.Text.translatable(
						"message.ssc_addon.spell.recall_interrupted")
				.formatted(net.minecraft.util.Formatting.RED), true);
		world.playSound(null, recall.player.getX(), recall.player.getY(), recall.player.getZ(),
				SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
	}
}
