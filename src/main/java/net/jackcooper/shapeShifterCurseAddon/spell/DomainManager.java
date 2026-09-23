package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DomainManager {
	public static final Identifier STATE = new Identifier("ssc_addon", "domain_state");
	public static final Identifier SOUND = new Identifier("ssc_addon", "domain_sound");
	private static final Map<UUID, Field> FIELDS = new LinkedHashMap<>();
	private static final ThreadLocal<Entity> EFFECT_SOURCE = new ThreadLocal<>();
	private static final ThreadLocal<AttachedEffect> ATTACHED_EFFECT = new ThreadLocal<>();
	private record AttachedEffect(Entity source, LivingEntity target) {}
	private DomainManager() {}
	private record Field(ServerPlayerEntity owner, ServerWorld world, Vec3d center,
	                     double headHeight, int startTick, boolean active) {
		int elapsed() { return world.getServer().getTicks() - startTick; }
	}

	/** 蓄力期锚点（2026-09-22 用户定稿：领域随玩家走）：锚在施法开始时的位置，
	 * 蓄力期间中心跟随玩家当前位置（掉落中施放不会把领域留在天上），
	 * 位移超过 MAX_CAST_DISPLACEMENT（3 格）仍会被 canContinue 打断——防止跳跃蹭施放。 */
	private static final Map<UUID, Vec3d> CHARGE_ANCHORS = new LinkedHashMap<>();

	/** 扩张期当前壳半径（active 恒 0）：蓄力第 10s 起 0.75 格三次缓出生长，15s 达 16 格。 */
	private static double chargingRadius(Field field) {
		return field.active ? 0 : DomainRules.expansionRadius(field.elapsed());
	}
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DomainManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIELDS.clear());
		// 方块跨界隔离（2026-09-21 需求）：领域内无法干涉外界方块（破坏/放置/右键/攻击），
		// 外界同样无法干涉领域内方块。玩家眼睛坐标 ↔ 目标方块中心分属壳内外即拒绝。
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer
					&& blocksCrossBoundary(serverPlayer.getServerWorld(), serverPlayer.getEyePos(),
							Vec3d.ofCenter(hitResult.getBlockPos()))) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player instanceof ServerPlayerEntity serverPlayer
					&& blocksCrossBoundary(serverPlayer.getServerWorld(), serverPlayer.getEyePos(), Vec3d.ofCenter(pos))) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
	}

	/**
	 * 方块跨界判定：眼睛位置与目标位置分属任一领域壳的内外两侧 → 跨界，应拒绝。
	 * 壳间（内层~外层）算内，与 enclosedComplete 语义一致；扩张期壳同样适用。
	 */
	public static boolean blocksCrossBoundary(ServerWorld world, Vec3d from, Vec3d to) {
		for (Field field : FIELDS.values()) {
			if (field.world != world) continue;
			double inner = field.active ? DomainRules.INNER_RADIUS : chargingRadius(field);
			if (inner <= 0.1) continue;
			if (DomainRules.separates(from.subtract(field.center), to.subtract(field.center), inner + 1)) return true;
		}
		return false;
	}

	/** 客户端领域边界判定钩子（服务端 common 定义、客户端注册）：供双端共用的几何（如法术瞄准）
	 * 在客户端镜像壳数据上做同款跨界判定。专用服务器上钩子为 null，判定自动退化为不拦。 */
	public static volatile java.util.function.BiPredicate<net.minecraft.util.math.Vec3d, net.minecraft.util.math.Vec3d> clientBoundaryCheck;

	/** 双端通用：施法者与落点是否被任一领域壳隔开（服务端查权威表；客户端查同步镜像）。 */
	public static boolean blocksAimBoundary(net.minecraft.world.World world, Vec3d from, Vec3d to) {
		if (world instanceof ServerWorld serverWorld) {
			return blocksCrossBoundary(serverWorld, from, to);
		}
		// 客户端：镜像判定（钩子未注册时不拦，服务端权威拒兕底）
		java.util.function.BiPredicate<Vec3d, Vec3d> check = clientBoundaryCheck;
		return check != null && check.test(from, to);
	}
	public static boolean blocksTargeting(Entity source, Entity target) {
		if (source == null || target == null || source == target || !(source.getWorld() instanceof ServerWorld world)) return false;
		if (target.getWorld() == world) return blocksCrossBoundary(world, source.getPos(), target.getPos());
		return enclosedForTargeting(world, source.getPos()) || enclosedForTargeting(target.getWorld(), target.getPos());
	}
	private static boolean enclosedForTargeting(World world, Vec3d position) {
		for (Field field : FIELDS.values()) {
			if (field.world != world) continue;
			double inner = field.active ? DomainRules.INNER_RADIUS : chargingRadius(field);
			if (inner > 0.1 && field.center.squaredDistanceTo(position) <= (inner + 1) * (inner + 1)) return true;
		}
		return false;
	}
	public static boolean canStart(ServerPlayerEntity player) {
		if (FIELDS.containsKey(player.getUuid())) return false;
		// 2026-09-22 需求①：已被任一领域困住（完全体或扩张壳内）不能再施放领域，防止叠加。
		return !enclosedTrapping(player.getServerWorld(), player.getPos());
	}
	public static void begin(ServerPlayerEntity player) {
		CHARGE_ANCHORS.put(player.getUuid(), player.getPos());
		Field field = new Field(player, player.getServerWorld(), player.getPos(),
				player.getHeight() + 0.8, player.getServer().getTicks(), false);
		FIELDS.put(player.getUuid(), field);
		broadcast(field, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.65f, 1.0f);
		sync(player.getServer());
	}
	public static boolean canContinue(ServerPlayerEntity player) {
		Field field = FIELDS.get(player.getUuid());
		Vec3d anchor = CHARGE_ANCHORS.get(player.getUuid());
		// 位移判定改用蓄力锚点（不是跟随中的 center）：随玩家走 + 超 3 格仍打断。
		// 壳开始扩张后进入锁定态（2026-09-23）：位移不再打断，必须释放。
		if (isExpanding(player)) return true;
		return field != null && anchor != null && !field.active && player.getWorld() == field.world
				&& player.getPos().squaredDistanceTo(anchor) <= DomainRules.MAX_CAST_DISPLACEMENT * DomainRules.MAX_CAST_DISPLACEMENT;
	}

	/** 壳是否已开始扩张（蓄力 ≥200t，含完全体）：扩张后施法锁定不可打断（2026-09-23 用户定稿）。 */
	public static boolean isExpanding(ServerPlayerEntity player) {
		Field field = FIELDS.get(player.getUuid());
		return field != null && field.elapsed() >= DomainRules.EXPAND_START_TICK;
	}
	public static void activate(ServerPlayerEntity player) {
		Field field = FIELDS.get(player.getUuid());
		if (field == null || field.active) return;
		CHARGE_ANCHORS.remove(player.getUuid()); // 蓄力结束：完全体锁定在跟随后的当前位置
		FIELDS.put(player.getUuid(), new Field(player, field.world, field.owner.getPos(), field.headHeight,
				player.getServer().getTicks(), true));
		// 2026-09-23 用户两轮反馈：完全展开的轰鸣仍偏响，音量倍率 0.7 → 0.5（其余领域音效维持 1.0）
		broadcast(field, SoundEvents.ENTITY_WITHER_SPAWN, 0.55f, 0.5f);
		sync(player.getServer());
	}
	public static void remove(ServerPlayerEntity player) {
		CHARGE_ANCHORS.remove(player.getUuid());
		Field field = FIELDS.remove(player.getUuid());
		if (field == null) return;
		if (field.active) broadcast(field, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.0f);
		sync(player.getServer());
	}

	/** 仅向同维度 64 格内发送音效事件；客户端按施法者距离更新音量，避免原版二次衰减与音量钳制。
	 * volumeScale：该音效的整体音量倍率（2026-09-23 新增，随包下发，客户端乘在距离曲线上）。 */
	private static void broadcast(Field field, net.minecraft.sound.SoundEvent sound, float pitch, float volumeScale) {
		Vec3d position = field.owner.getWorld() == field.world ? field.owner.getPos() : field.center;
		long seed = field.world.getRandom().nextLong();
		for (ServerPlayerEntity listener : field.world.getPlayers()) {
			if (listener.getPos().squaredDistanceTo(position) >= DomainRules.SOUND_RANGE * DomainRules.SOUND_RANGE
					|| !ServerPlayNetworking.canSend(listener, SOUND)) continue;
			var buf = PacketByteBufs.create();
			buf.writeIdentifier(field.world.getRegistryKey().getValue());
			buf.writeUuid(field.owner.getUuid());
			buf.writeIdentifier(sound.getId());
			buf.writeDouble(position.x);
			buf.writeDouble(position.y);
			buf.writeDouble(position.z);
			buf.writeFloat(pitch);
			buf.writeFloat(volumeScale);
			buf.writeLong(seed);
			ServerPlayNetworking.send(listener, SOUND, buf);
		}
	}
	private static void tick(MinecraftServer server) {
		if (FIELDS.isEmpty()) return; // 常态（无领域施放）零分配早退
		for (Field field : java.util.List.copyOf(FIELDS.values())) {
			if (!field.owner.isAlive() || field.owner.isRemoved() || field.owner.isSpectator()
					|| field.owner.getWorld() != field.world
					|| field.active && field.elapsed() >= DomainRules.DURATION_TICKS
					|| !field.active && !SpellChannelManager.isCasting(field.owner)) {
				CHARGE_ANCHORS.remove(field.owner.getUuid());
				remove(field.owner);
			}
		}
		// 蓄力跟随（2026-09-22）：非激活领域中心每 tick 跟随玩家（掉落/破推开都跟），
		// 位移越界由 canContinue（统一读条 valid 校验）打断；完全体中心锁定不动。
		for (var entry : java.util.List.copyOf(FIELDS.entrySet())) {
			Field field = entry.getValue();
			if (field.active) continue;
			Vec3d current = field.owner.getPos();
			if (field.owner.getWorld() == field.world && !field.center.equals(current)) {
				FIELDS.put(entry.getKey(), new Field(field.owner, field.world, current,
						field.headHeight, field.startTick, false));
			}
		}
		for (Field field : FIELDS.values()) {
			if (field.active || field.elapsed() <= 0 || field.elapsed() % 40 != 0) continue;
			float pitch = 0.65f + Math.min(1f, (float) field.elapsed() / DomainRules.CHARGE_TICKS) * 0.25f;
			broadcast(field, SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, pitch, 1.0f);
		}
		if (!FIELDS.isEmpty() && server.getTicks() % 10 == 0) sync(server);
	}
	private static void sync(MinecraftServer server) {
		if (server == null) return;
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (!ServerPlayNetworking.canSend(viewer, STATE)) continue;
			var visible = FIELDS.values().stream().filter(field -> field.world == viewer.getWorld()
					&& viewer.getPos().squaredDistanceTo(field.center) < 256 * 256).toList();
			var buf = PacketByteBufs.create();
			buf.writeIdentifier(viewer.getWorld().getRegistryKey().getValue());
			buf.writeVarInt(visible.size());
			for (Field field : visible) {
				buf.writeUuid(field.owner.getUuid());
				buf.writeDouble(field.center.x);
				buf.writeDouble(field.center.y);
				buf.writeDouble(field.center.z);
				buf.writeDouble(field.headHeight);
				buf.writeBoolean(field.active);
				buf.writeVarInt(field.elapsed());
			}
			ServerPlayNetworking.send(viewer, STATE, buf);
		}
	}
	public static boolean blocksPath(World world, Vec3d from, Vec3d to, double padding) {
		if (FIELDS.isEmpty() || !(world instanceof ServerWorld) || from.equals(to)) return false;
		for (Field field : FIELDS.values()) {
			if (field.world != world) continue;
			Vec3d start = from.subtract(field.center), end = to.subtract(field.center);
			if (field.active) {
				if (DomainRules.crosses(start.x, start.y, start.z, end.x, end.y, end.z, padding)) return true;
			} else {
				// 扩张期单向阀（2006-09-21）：壳内向外=拦（含投射物）；壳外向内=放行
				if (DomainRules.crossesOutward(start.x, start.y, start.z, end.x, end.y, end.z,
						chargingRadius(field))) return true;
			}
		}
		return false;
	}
	public static Vec3d limitMovement(Entity entity, Vec3d movement) {
		Vec3d feet = entity.getPos();
		double padding = entity.getWidth() * 0.5;
		if (!blocksPath(entity.getWorld(), feet, feet.add(movement), padding)) return movement;
		if (entity instanceof ProjectileEntity) { entity.discard(); return Vec3d.ZERO; }
		return DomainRules.limitMovement(feet, movement, padding, movementShells(entity.getWorld()));
	}

	public static Vec3d finishMovement(Entity entity, Vec3d movement) {
		if (!blocksPath(entity.getWorld(), entity.getPos(), entity.getPos().add(movement), entity.getWidth() * 0.5)) return movement;
		if (entity instanceof ProjectileEntity) { entity.discard(); return Vec3d.ZERO; }
		return DomainCollision.finishMovement(entity, movement, movementShells(entity.getWorld()));
	}

	private static java.util.List<DomainRules.Shell> movementShells(World world) {
		java.util.List<DomainRules.Shell> shells = new java.util.ArrayList<>(FIELDS.size());
		for (Field field : FIELDS.values()) {
			if (field.world != world) continue;
			double radius = field.active ? DomainRules.INNER_RADIUS : chargingRadius(field);
			if (radius > 0) shells.add(new DomainRules.Shell(field.center, radius, field.active));
		}
		return shells;
	}

	public static Vec3d clampToBoundary(Entity entity, Vec3d target) {
		Vec3d from = entity.getPos();
		return from.add(limitMovement(entity, target.subtract(from)));
	}

	/**
	 * 传送点方块安全化（2026-09-22 反馈：贴界蹭墙陷进地里）：服务端钳制结果直接
	 * requestTeleport 会绕过方块碰撞——球面滑行的切向在弯曲处带竖直向下分量，贴界蹭到
	 * 「只剩一角」的方块时钳制点可能落在其顶面之下，玩家被传进方块/地下。
	 * 对每个候选点同时检查方块和领域边界；没有安全位置则返回原位，避免纠正传送自身
	 * 被跨界保护取消、客户端继续在墙外移动。使用当前碰撞箱，兼容潜行、游泳和缩放形态。
	 */
	public static Vec3d liftOutOfBlocks(Entity entity, Vec3d target) {
		Vec3d from = entity.getPos();
		return DomainRules.safeCorrection(from, target, candidate ->
				!blocksTeleport(entity, entity.getWorld(), candidate)
				&& entity.getWorld().isSpaceEmpty(entity, entity.getBoundingBox().offset(candidate.subtract(from))));
	}
	public static boolean blocksTeleport(Entity entity, World destination, Vec3d position) {
		if (!(entity.getWorld() instanceof ServerWorld)) return false;
		// 2026-09-22 穿壳根因修复：同维度移动判定改用脚部锚点（position 本就是目标脚坐标），
		// 不再用碰撞箱中心（中心垂直偏移使贴墙玩家被误判为壳外 → 可自由走出）。
		if (destination == entity.getWorld()) return blocksPath(destination, entity.getPos(), position,
				entity.getWidth() * 0.5);
		// 跨维度：出发侧被任一阶段领域困住=拒；目标侧仅"完全体"阻止进入（扩张中的可进，单向阀）。
		if (enclosedTrapping(entity.getWorld(), entity.getPos())) return true;
		return enclosedComplete(destination, position);
	}

	/** 出发侧拦截（含扩张期）：扩张中壳内实体也不许跨维度逃离。 */
	public static boolean enclosedTrapping(World world, Vec3d position) {
		if (FIELDS.isEmpty() || !(world instanceof ServerWorld)) return false;
		for (Field field : FIELDS.values()) {
			if (field.world != world) continue;
			if (field.active) {
				if (field.center.squaredDistanceTo(position) <= DomainRules.OUTER_RADIUS * DomainRules.OUTER_RADIUS) return true;
			} else {
				double radius = chargingRadius(field);
				if (radius > 0 && field.center.squaredDistanceTo(position) <= radius * radius) return true;
			}
		}
		return false;
	}

	/** 完全体判定：仅 active 的领域算完全展开（目标侧阻止进入用）。 */
	public static boolean enclosedComplete(World world, Vec3d position) {
		if (!(world instanceof ServerWorld)) return false;
		for (Field field : FIELDS.values()) {
			if (field.active && field.world == world && field.center.squaredDistanceTo(position)
					<= DomainRules.OUTER_RADIUS * DomainRules.OUTER_RADIUS) return true;
		}
		return false;
	}
	public static boolean hasActive(World world) {
		return world instanceof ServerWorld && FIELDS.values().stream().anyMatch(field -> field.active && field.world == world);
	}
	public static Entity setEffectSource(Entity source) {
		Entity previous = EFFECT_SOURCE.get();
		if (source == null) EFFECT_SOURCE.remove(); else EFFECT_SOURCE.set(source);
		return previous;
	}
	public static boolean blocksEffect(LivingEntity target, Entity source) {
		Entity actual = source == null ? EFFECT_SOURCE.get() : source;
		if (isAttachedEffect(actual, target)) return false;
		return actual != null && blocksPath(target.getWorld(), actual.getPos(), target.getPos(), 0);
	}
	public static void runAttachedEffect(Entity source, LivingEntity target, Runnable action) {
		AttachedEffect previous = ATTACHED_EFFECT.get();
		ATTACHED_EFFECT.set(new AttachedEffect(source, target));
		try {
			action.run();
		} finally {
			if (previous == null) ATTACHED_EFFECT.remove(); else ATTACHED_EFFECT.set(previous);
		}
	}
	private static boolean isAttachedEffect(Entity source, LivingEntity target) {
		AttachedEffect effect = ATTACHED_EFFECT.get();
		return effect != null && effect.source == source && effect.target == target;
	}
	public static boolean blocksDamage(LivingEntity target, DamageSource source) {
		if (isAttachedEffect(source.getAttacker(), target)) return false;
		if (blocksEffect(target, source.getAttacker())) return true;
		Vec3d position = source.getPosition();
		return position != null && blocksPath(target.getWorld(), position, target.getPos(), 0);
	}
	public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
		if (!(target.getWorld() instanceof ServerWorld)) return amount;
		Field received = fieldAt(target);
		Field dealt = source.getAttacker() == null ? null : fieldAt(source.getAttacker());
		if (received != null) amount *= DomainRules.receivedMultiplier(friendly(received, target));
		if (dealt != null) amount *= DomainRules.dealtMultiplier(friendly(dealt, source.getAttacker()));
		return amount;
	}
	private static Field fieldAt(Entity entity) {
		for (Field field : FIELDS.values()) {
			if (field.active && field.world == entity.getWorld()
					&& field.center.squaredDistanceTo(entity.getPos()) <= DomainRules.INNER_RADIUS * DomainRules.INNER_RADIUS) return field;
		}
		return null;
	}
	private static boolean friendly(Field field, Entity target) {
		return field.owner == target || target instanceof LivingEntity living && WhitelistUtils.isProtected(field.owner, living);
	}
}
