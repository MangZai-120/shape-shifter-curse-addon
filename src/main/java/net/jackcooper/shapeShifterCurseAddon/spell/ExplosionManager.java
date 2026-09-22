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
		boolean ballCharging() { return elapsed() >= ExplosionRules.BALL_START_TICKS; }

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
		damageArea(sequence);
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
			// Only the paid channel's successful completion may trigger damage.
			if ((!sequence.exploded && !isChargingOwner(server, sequence))
					|| (sequence.exploded && elapsed > ExplosionRules.EXPLODE_TICKS + ExplosionRules.AFTER_GLOW_TICKS)) {
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

	private static void damageArea(Sequence sequence) {
		ServerWorld world = sequence.world;
		Vec3d center = sequence.center;
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(sequence.owner);
		if (owner != null && owner.getWorld() != world) owner = null;
		var source = owner == null ? SpellDamageSource.of(world.getDamageSources())
				: SpellDamageSource.of(world.getDamageSources(), owner);
		double diameter = ExplosionRules.OUTER_RADIUS * 2;
		var targets = world.getEntitiesByClass(LivingEntity.class, Box.of(center, diameter, diameter, diameter),
				entity -> entity.isAlive() && !entity.isSpectator());
		LivingEntity lastHit = null, killed = null;
		boolean hitBurning = false;
		for (LivingEntity target : targets) {
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
