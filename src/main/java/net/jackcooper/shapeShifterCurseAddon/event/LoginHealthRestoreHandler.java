package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录血量恢复：修复「带 max_health 修饰符的形态（如 SP 美西螈）重进存档后血量被裸 20 上限钳掉」的 bug。
 *
 * <p><b>根因</b>：Apoli 的 {@code apoli:attribute}（generic.max_health）是运行时 temporary 修饰符，
 * 不写入玩家 NBT。玩家登录时先按裸 20 上限执行 {@code LivingEntity.readCustomDataFromNbt} 里的
 * {@code setHealth(存档血量)}，存档血量 > 20 时被 clamp 到 20；随后 Apoli 才把最大生命值抬到 34，
 * 但此刻 health 已停在 20 —— 表现为「满血 34 退出、重进只剩 20」。
 *
 * <p><b>修复</b>：{@code SscAddonLivingEntityMixin} 在 readCustomDataFromNbt 末尾记录存档里的原始血量
 * （快照），之后每个服务端 tick 观察，一旦 Apoli 把最大生命值抬到 ≥ 快照值（修饰符挂载完成），
 * 就把当前血量补回快照绝对值。期间玩家受击 / 死亡 / 断线则作废，避免覆盖真实战斗血量。
 * 对普通玩家、减血形态、半血退出等场景均为无害 no-op。判定全在服务端，主客机一致。
 */
public final class LoginHealthRestoreHandler {

	private LoginHealthRestoreHandler() {}

	/** 等待 Apoli 挂载 max_health 修饰符的最长 tick 数，超时放弃（不强改血量）。 */
	private static final int MAX_WAIT_TICKS = 200; // 10 秒宽松兜底

	private static final class Pending {
		final float snapshot; // 存档里的原始血量绝对值
		int waited;           // 已等待 tick
		Pending(float snapshot) { this.snapshot = snapshot; }
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	/** 由 {@code SscAddonLivingEntityMixin} 在读取存档 NBT 时调用，记录登录血量快照。 */
	public static void recordSnapshot(UUID uuid, float savedHealth) {
		if (savedHealth > 0.0f) {
			PENDING.put(uuid, new Pending(savedHealth));
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) return;
			Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Pending> entry = it.next();
				Pending p = entry.getValue();
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player == null) {
					// 玩家尚未真正加入列表 / 已离开：仅累计等待，超时清理
					if (++p.waited > MAX_WAIT_TICKS) it.remove();
					continue;
				}
				// 作废：已受击 / 已死亡 —— 玩家已进入正常游戏，不能用旧快照覆盖真实血量
				if (player.hurtTime > 0 || !player.isAlive()) {
					it.remove();
					continue;
				}
				if (player.getMaxHealth() >= p.snapshot) {
					// 上限已抬到足以容纳快照（Apoli 修饰符挂载完成）：把被钳掉的血量补回
					if (player.getHealth() < p.snapshot) {
						player.setHealth(p.snapshot);
					}
					it.remove();
				} else if (++p.waited > MAX_WAIT_TICKS) {
					// 超时仍未挂载到位（形态可能已不存在）：放弃，不强改血量
					it.remove();
				}
			}
		});

		// 断线清理，避免 UUID 残留
		ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) ->
				PENDING.remove(netHandler.player.getUuid()));
	}
}
