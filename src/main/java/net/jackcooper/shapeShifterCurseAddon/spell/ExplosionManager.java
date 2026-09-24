package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Target-bound charge visuals. Casting/payment stays in SpellChannelManager.
 * Each cast has its own ID; interruption removes all pending visual state.
 * Clients receive elapsed ticks every 10 ticks and generate visuals/audio locally;
 * damage, ignition and knockback are settled only on the server. */
public final class ExplosionManager {
	public static final Identifier START = new Identifier("ssc_addon", "explosion_start");
	private static final Map<UUID, Sequence> SEQUENCES = new LinkedHashMap<>();

	private static final class Sequence {
		final UUID id = UUID.randomUUID();
		final ServerWorld world;
		final Vec3d center;
		final UUID owner;
		/** 蓄力锚点：施法开始时玩家位置，位移超 3 格打断（2026-09-22 用户定稿，同领域）。 */
		final Vec3d chargeAnchor;
		UUID refundCastId;
		final long startTime;
		float power;
		boolean exploded;
		/** 待结算目标队列（2026-09-24 伤害分批）：引爆时快照全部目标，每 tick 只结一批，
		 * 避免密集场景百余实体同帧过 damage/死亡掉落造成服务端尖峰（「造成伤害后卡一下」）。 */
		java.util.List<LivingEntity> pendingTargets = java.util.List.of();

		Sequence(ServerPlayerEntity owner, Vec3d center, float power, UUID refundCastId) {
			this.world = owner.getServerWorld();
			this.owner = owner.getUuid();
			this.center = center;
			this.chargeAnchor = owner.getPos();
			this.power = power;
			this.refundCastId = refundCastId;
			this.startTime = world.getTime();
		}

		int elapsed() { return (int) Math.max(0, world.getTime() - startTime); }

		/** 是否已进入红白球阶段（≥ BALL_START_TICKS）：此后施法锁定不可打断。 */
		/** 锁定阈值（2026-09-24 用户定稿）：主题音频起播（T-8s / 540t）即锁定不可打断——
		 * 音频响起 = 施法已不可挽回，伤害/主动取消/长按取消/位移全部失效直到释放。
		 * 原为红白球生成（682t / 34.1s），用户要求提前到音频起播。 */
		boolean ballCharging() { return elapsed() >= ExplosionRules.SOUND_START_TICKS; }

		/** 施法者是否仍在锚点 3 格内（同领域 MAX_CAST_DISPLACEMENT；离锚点即打断蓄力）。 */
		boolean ownerWithinAnchor(MinecraftServer server) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
			return player != null && player.getWorld() == world
					&& player.getPos().squaredDistanceTo(chargeAnchor) <= 3.0 * 3.0;
		}
	}

	private ExplosionManager() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ExplosionManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEQUENCES.clear());
	}

	public static void detonate(ServerPlayerEntity owner, Vec3d center, float power, UUID refundCastId) {
		Sequence sequence = SEQUENCES.values().stream().filter(value -> !value.exploded
				&& value.owner.equals(owner.getUuid()) && value.world == owner.getWorld()
				&& value.center.equals(center)).findFirst().orElse(null);
		if (sequence == null) return;
		sequence.power = power;
		sequence.refundCastId = refundCastId;
		sequence.exploded = true;
		// 伤害分批（2026-09-24 修卡顿）：引爆时只快照目标列表 + 标记已爆（视觉/音频立即生效），
		// 实际伤害由 tick() 每 tick 结一批，密集场景伤害/死亡掉落尖峰被摊平（总伤害不变）。
		double diameter = ExplosionRules.OUTER_RADIUS * 2;
		sequence.pendingTargets = sequence.world.getEntitiesByClass(LivingEntity.class,
				Box.of(center, diameter, diameter, diameter),
				entity -> entity.isAlive() && !entity.isSpectator());
		syncNow(owner.getServer());
	}

	public static void beginCharge(ServerPlayerEntity owner, Vec3d target) {
		cancelCharge(owner);
		Sequence sequence = new Sequence(owner, target, 0, null);
		SEQUENCES.put(sequence.id, sequence);
		syncNow(owner.getServer());
	}

	public static void cancelCharge(ServerPlayerEntity owner) {
		if (SEQUENCES.values().removeIf(value -> !value.exploded && value.owner.equals(owner.getUuid())))
			syncNow(owner.getServer());
	}

/** 施法者是否仍在蓄力锚点 3 格内（无进行中序列时规约为 true，不阻断其它校验）。
	 * 红白球开始生成后进入锁定态（2026-09-23 用户定稿）：位移不再打断，必须释放。 */
	public static boolean isWithinChargeAnchor(ServerPlayerEntity owner) {
		return SEQUENCES.values().stream()
				.filter(value -> !value.exploded && value.owner.equals(owner.getUuid()))
				.allMatch(value -> value.ballCharging() || value.ownerWithinAnchor(owner.getServer()));
	}

	/** 施法者的进行中序列是否已进入红白球阶段（≥682t / 第 34.1 秒）：
	 * 锁定不可打断（同领域壳扩张锁定，2026-09-23 用户定稿）。 */
	public static boolean isBallCharging(ServerPlayerEntity owner) {
		return SEQUENCES.values().stream()
				.anyMatch(value -> !value.exploded && value.owner.equals(owner.getUuid())
						&& value.ballCharging());
	}

	private static void tick(MinecraftServer server) {
		if (SEQUENCES.isEmpty()) return;
		boolean removed = false;
		var iterator = SEQUENCES.values().iterator();
		while (iterator.hasNext()) {
			Sequence sequence = iterator.next();
			int elapsed = sequence.elapsed();
			// 分批伤害推进：每 tick ≤ DAMAGE_BATCH 个目标（每目标结算时验 isAlive，跳过批间死亡/卸载）。
			if (!sequence.pendingTargets.isEmpty()) damageBatch(sequence);
			// Only the paid channel's successful completion may trigger damage.
			if ((!sequence.exploded && !isChargingOwner(server, sequence))
					|| (sequence.exploded && sequence.pendingTargets.isEmpty()
					&& elapsed > ExplosionRules.EXPLODE_TICKS + ExplosionRules.AFTER_GLOW_TICKS)) {
				iterator.remove();
				removed = true;
			}
		}
		// Also send an empty snapshot when the final sequence expires.
		if (removed || server.getTicks() % 10 == 0) syncNow(server);
	}

	private static boolean isChargingOwner(MinecraftServer server, Sequence sequence) {
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(sequence.owner);
		return owner != null && owner.isAlive() && owner.getWorld() == sequence.world && SpellChannelManager.isCasting(owner);
	}

	/** 分批伤害每 tick 结算目标数上限：40 个/批，100+ 实体场景 2-3 tick 摊完（≤150ms 玩家不可感知）。 */
	private static final int DAMAGE_BATCH = 40;

	/** 分批结算一批伤害（2026-09-24 修卡顿）：从 pendingTargets 头部取 ≤DAMAGE_BATCH 个逐个结算，
	 * 逻辑与原 damageArea 单目标完全一致（距离衰减/无差别伤害/点燃/击退）；批间已死亡/卸载的目标
	 * 由 isAlive 跳过。金沙岚连击统计（lastHit/killed/hitBurning）跨批累计，最后一批完成时统一
	 * 调 onSpellHit——语义与原「全场一次结算」一致。 */
	private static void damageBatch(Sequence sequence) {
		ServerWorld world = sequence.world;
		Vec3d center = sequence.center;
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(sequence.owner);
		if (owner != null && owner.getWorld() != world) owner = null;
		var source = owner == null ? SpellDamageSource.of(world.getDamageSources())
				: SpellDamageSource.of(world.getDamageSources(), owner);
		LivingEntity lastHit = null, killed = null;
		boolean hitBurning = false;
		int settle = Math.min(DAMAGE_BATCH, sequence.pendingTargets.size());
		var batch = sequence.pendingTargets.subList(0, settle);
		for (LivingEntity target : batch) {
			if (!target.isAlive() || target.isRemoved()) continue; // 批间死亡/卸载跳过
			double distance = target.getPos().distanceTo(center);
			double factor = ExplosionRules.damageFactor(distance);
			if (factor <= 0) continue;
			boolean burning = target.getFireTicks() > 0;
			// Deliberately no ally whitelist or caster exclusion (confirmed friendly fire).
			if (!target.damage(source, (float) (sequence.power * factor))) continue;
			if (target != owner && !(target instanceof net.minecraft.entity.decoration.ArmorStandEntity)) {
				lastHit = target;
				hitBurning |= burning;
				if (!target.isAlive()) killed = target;
			}
			int fire = ExplosionRules.fireTicks(distance);
			if (fire > 0) target.setFireTicks(Math.max(target.getFireTicks(), fire));
			Vec3d direction = target.getPos().subtract(center).multiply(1, 0, 1).normalize();
			double strength = ExplosionRules.knockbackStrength(distance);
			target.addVelocity(direction.x * strength, 0.35 + strength * 0.25, direction.z * strength);
			target.velocityModified = true;
		}
		batch.clear(); // 已结算目标出队（subList.clear 原位移除）
		if (owner != null && lastHit != null) {
			FormCastingStyle.onSpellHit(owner, killed != null ? killed : lastHit,
					FormationElement.FIRE, sequence.refundCastId, hitBurning);
		}
	}

	private static void syncNow(MinecraftServer server) {
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (!ServerPlayNetworking.canSend(viewer, START)) continue;
			var visible = SEQUENCES.values().stream().filter(sequence -> sequence.world == viewer.getWorld()
					&& viewer.squaredDistanceTo(sequence.center) < ExplosionRules.VIEW_RANGE * ExplosionRules.VIEW_RANGE).toList();
			var buf = PacketByteBufs.create();
			buf.writeIdentifier(viewer.getWorld().getRegistryKey().getValue());
			buf.writeVarInt(visible.size());
			for (Sequence sequence : visible) {
				buf.writeUuid(sequence.id);
				buf.writeDouble(sequence.center.x);
				buf.writeDouble(sequence.center.y);
				buf.writeDouble(sequence.center.z);
				// Duration, never server uptime: old saves and late joins use the same timeline.
				buf.writeVarInt(sequence.elapsed());
				buf.writeBoolean(sequence.exploded);
			}
			ServerPlayNetworking.send(viewer, START, buf);
		}
	}
}
