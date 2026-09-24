package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class KillEmpowerManager {

	private KillEmpowerManager() {}

	/** 赋能持续时间（10s）。 */
	public static final int EMPOWER_TICKS = KillEmpowerState.WINDOW_TICKS;
	/** SP 赋能火环基础时长，实际时长以施加后的状态效果为准。 */
	public static final int EMPOWER_RING_TICKS_SP = 232;
	/** Red 赋能火环基础时长，实际时长以施加后的状态效果为准。 */
	public static final int EMPOWER_RING_TICKS_RED = 280;

	public static final int STATE_NONE = 0;
	public static final int STATE_READY = 1;   // 赋能待释放（击杀触发）
	public static final int STATE_RING = 2;    // 赋能火环激活中

	private record BurnOrigin(UUID owner, int expiresAtAge, boolean normal) {}
	private static final Map<LivingEntity, BurnOrigin> BURNS = new WeakHashMap<>();
	private static final java.util.Set<UUID> ACTIVE_RINGS = new java.util.HashSet<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(KillEmpowerManager::onTick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (ACTIVE_RINGS.remove(handler.player.getUuid())) handler.player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
			if (isEmpowerForm(handler.player)) writeState(handler.player, new KillEmpowerState(0, 0));
			BURNS.values().removeIf(burn -> burn.owner().equals(handler.player.getUuid()));
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BURNS.clear();
			ACTIVE_RINGS.clear();
		});
	}

	public static KillEmpowerState readState(ServerPlayerEntity player) {
		int flags = PowerUtils.getResourceValue(player, FormIdentifiers.EMPOWER_STATE);
		int ticks = PowerUtils.getResourceValue(player, FormIdentifiers.EMPOWER_TICKS);
		int ringTicks = PowerUtils.getResourceValue(player, FormIdentifiers.EMPOWER_RING_TICKS);
		return new KillEmpowerState((flags & STATE_READY) != 0 ? ticks : 0,
				(flags & STATE_RING) != 0 ? ringTicks : 0);
	}

	public static void writeState(ServerPlayerEntity player, KillEmpowerState state) {
		if (state.hasRing()) ACTIVE_RINGS.add(player.getUuid());
		else ACTIVE_RINGS.remove(player.getUuid());
		// ticks/ringTicks 是每 tick 恰好 -1 的单调倒计时（tickFromRingEffect 里 Math.max(0, x-1)）：
		// 走批量预测通道；state 标志/ring_duration 是状态型任意跳变，保持立即同步（2026-09-24）。
		PowerUtils.countDownAndSync(player, FormIdentifiers.EMPOWER_TICKS, state.readyTicks());
		PowerUtils.countDownAndSync(player, FormIdentifiers.EMPOWER_RING_TICKS, state.ringTicks());
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.EMPOWER_STATE, state.flags());
		if (!state.hasRing()) PowerUtils.setResourceValueAndSync(player, FormIdentifiers.EMPOWER_RING_DURATION, 0);
	}

	public static boolean damage(LivingEntity target, DamageSource source, float amount, boolean normalSkill) {
		boolean wasAlive = target.isAlive();
		boolean damaged = target.damage(source, amount);
		if (normalSkill && wasAlive && damaged && !target.isAlive()
				&& source.getAttacker() instanceof ServerPlayerEntity player
				&& isEmpowerForm(player) && !WhitelistUtils.isProtected(player, target)) {
			writeState(player, readState(player).grant());
		}
		return damaged;
	}

	public static void trackBurn(ServerPlayerEntity player, LivingEntity target, int duration, boolean normalSkill) {
		if (!target.isAlive()) return;
		BURNS.put(target, new BurnOrigin(player.getUuid(), target.age + duration, normalSkill));
		if (target instanceof net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor accessor) {
			accessor.sscAddon$setIgniterUuid(player.getUuid());
		}
	}

	public static boolean damageBurn(LivingEntity target, DamageSource source, float amount) {
		BurnOrigin burn = BURNS.get(target);
		boolean normalSkill = burn != null && burn.normal() && target.age < burn.expiresAtAge()
				&& source.getAttacker() != null && burn.owner().equals(source.getAttacker().getUuid());
		return damage(target, source, amount, normalSkill);
	}

	/** 是否 SP使魔 / 红堕落使魔（赋能被动的两个宿主形态）。 */
	public static boolean isEmpowerForm(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_SP)
				|| FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED);
	}

	private static void onTick(net.minecraft.server.MinecraftServer server) {
		BURNS.entrySet().removeIf(entry -> !entry.getKey().isAlive()
				|| entry.getKey().age >= entry.getValue().expiresAtAge()
				|| !entry.getKey().hasStatusEffect(SscAddon.FOX_FIRE_BURN));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isEmpowerForm(player)) {
				if (ACTIVE_RINGS.remove(player.getUuid())) {
					player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
					playExtinguish(player);	// 切出形态中断
				}
				continue;
			}
			// 空闲态低频守卫：稳态（flags==0）只付 1 次资源读，免掉 readState 剩余 2 读 + writeState
			// 4 次写比对；每 100t 做一次完整读写自愈（外部写脏 EMPOWER_STATE 时 5s 内拉回 0 态）。
			if (PowerUtils.getResourceValue(player, FormIdentifiers.EMPOWER_STATE) == STATE_NONE
					&& player.age % 100 != 0) {
				continue;
			}
			KillEmpowerState state = readState(player);
			if (state.flags() == STATE_NONE) {
				writeState(player, state);
				continue;
			}
			if (!player.isAlive()) {
				if (state.hasRing()) {
					player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
					playExtinguish(player);	// 死亡中断
				}
				writeState(player, new KillEmpowerState(0, 0));
				continue;
			}
			var ringEffect = player.getStatusEffect(SscAddon.BLUE_FIRE_RING);
			if (state.hasRing() && (ringEffect == null
					|| player.hasStatusEffect(SscAddon.PURIFIED))) {
				player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
				playExtinguish(player);	// 效果被外力清除 / 被净化中断
				state = state.stopRing();
			}
			KillEmpowerState next = state.tickFromRingEffect(ringEffect == null ? 0 : ringEffect.getDuration());
			if (next.hasRing() && next.ringTicks() % 16 == 0) {
				KillEmpowerCast.tickEmpowerRing(player);
			}
			if (state.hasRing() && !next.hasRing()) {
				player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
				playExtinguish(player);	// 自然到期结束
			}
			writeState(player, next);
		}
	}

	/** 赋能火环结束/中断的火焰熄灭声（与手动关环、正常环关环音效一致）。 */
	private static void playExtinguish(ServerPlayerEntity player) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
	}
}
