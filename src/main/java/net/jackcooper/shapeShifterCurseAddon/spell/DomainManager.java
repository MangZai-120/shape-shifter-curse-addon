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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DomainManager {
	public static final Identifier STATE = new Identifier("ssc_addon", "domain_state");
	private static final Map<UUID, Field> FIELDS = new LinkedHashMap<>();
	private static final ThreadLocal<Entity> EFFECT_SOURCE = new ThreadLocal<>();
	private DomainManager() {}
	private record Field(ServerPlayerEntity owner, ServerWorld world, Vec3d center,
	                     double headHeight, int startTick, boolean active) {
		int elapsed() { return world.getServer().getTicks() - startTick; }
	}

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
			double outer = inner + 1;
			boolean fromInside = field.center.squaredDistanceTo(from) <= outer * outer;
			boolean toInside = field.center.squaredDistanceTo(to) <= outer * outer;
			if (fromInside != toInside) return true;
		}
		return false;
	}
	public static boolean canStart(ServerPlayerEntity player) { return !FIELDS.containsKey(player.getUuid()); }
	public static void begin(ServerPlayerEntity player) {
		FIELDS.put(player.getUuid(), new Field(player, player.getServerWorld(), player.getPos(),
				player.getHeight() + 0.8, player.getServer().getTicks(), false));
		sync(player.getServer());
	}
	public static boolean canContinue(ServerPlayerEntity player) {
		Field field = FIELDS.get(player.getUuid());
		return field != null && !field.active && player.getWorld() == field.world
				&& player.getPos().squaredDistanceTo(field.center) <= DomainRules.MAX_CAST_DISPLACEMENT * DomainRules.MAX_CAST_DISPLACEMENT;
	}
	public static void activate(ServerPlayerEntity player) {
		Field field = FIELDS.get(player.getUuid());
		if (field == null || field.active) return;
		FIELDS.put(player.getUuid(), new Field(player, field.world, field.center, field.headHeight,
				player.getServer().getTicks(), true));
		field.world.playSound(null, field.center.x, field.center.y, field.center.z,
				SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.8f, 0.55f);
		sync(player.getServer());
	}
	public static void remove(ServerPlayerEntity player) {
		Field field = FIELDS.remove(player.getUuid());
		if (field == null) return;
		if (field.active) field.world.playSound(null, field.center.x, field.center.y, field.center.z,
				SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1, 0.6f);
		sync(player.getServer());
	}
	private static void tick(MinecraftServer server) {
		for (Field field : java.util.List.copyOf(FIELDS.values())) {
			if (!field.owner.isAlive() || field.owner.isRemoved() || field.owner.isSpectator()
					|| field.owner.getWorld() != field.world
					|| field.active && field.elapsed() >= DomainRules.DURATION_TICKS
					|| !field.active && !SpellChannelManager.isCasting(field.owner)) remove(field.owner);
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
		if (!(world instanceof ServerWorld) || from.squaredDistanceTo(to) < 1.0e-12) return false;
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
		// 2026-09-22 穿壳根因修复：锚点改用脚部（entity.getPos()），不再用碰撞箱中心——
		// 中心比脚高 0.9~1.8 格，地面贴墙走时脚还在 15.9（内）而中心已越过 16（外），
		// 落入壳间带「可向外退」分支 → 整人穿出。移包坐标本就是脚坐标，脚锚点两端对齐。
		// 脚是点，padding 只计半宽（身高偏移不再重复计入）。
		Vec3d feet = entity.getPos();
		double padding = entity.getWidth() * 0.5;
		if (!blocksPath(entity.getWorld(), feet, feet.add(movement), padding)) return movement;
		if (entity instanceof ProjectileEntity) { entity.discard(); return Vec3d.ZERO; }
		// 2026-09-22 反馈修正：整体二分缩放会把斜向移动的切向分量一并吃掉（贴墙卡死），
		// 改为球面滑行——起点在壳内时只压缩径向分量，保留切向滑动；否则（壳外/壳间）保留二分。
		Vec3d result = slideMovement(entity, feet, movement, padding);
		if (result != null) return result;
		double low = 0, high = 1;
		for (int iteration = 0; iteration < 18; iteration++) {
			double middle = (low + high) * 0.5;
			if (blocksPath(entity.getWorld(), feet, feet.add(movement.multiply(middle)), padding)) high = middle;
			else low = middle;
		}
		return movement.multiply(Math.max(0, low - 0.0001));
	}

	/** 对每个相关壳尝试滑行；起点不在壳内返回 null（退回二分）。 */
	private static Vec3d slideMovement(Entity entity, Vec3d center, Vec3d movement, double padding) {
		Vec3d combined = null;
		for (Field field : FIELDS.values()) {
			if (field.world != entity.getWorld()) continue;
			double inner = field.active ? DomainRules.INNER_RADIUS
					: DomainRules.expansionRadius(field.elapsed());
			if (inner <= 0.1) continue;
			double wall = Math.max(0.1, inner - padding);
			Vec3d startRel = center.subtract(field.center);
			if (startRel.lengthSquared() > wall * wall) continue; // 起点不在壳内→交给二分兑底
			Vec3d slid = DomainRules.slideInside(startRel, movement, wall);
			combined = combined == null ? slid : combined.add(slid).subtract(movement); // 多壳时近似取首壳
		}
		return combined;
	}

	/**
	 * 越界钳制（2026-09-22 反馈修正）：以服务器权威旧位置为起点。起点在壳内时用球面滑行
	 * （只压径向、保留切向，消除贴墙卡住）；否则（壳间/壳外）保留二分贴墙。
	 */
	public static Vec3d clampToBoundary(Entity entity, Vec3d target) {
		Vec3d from = entity.getPos();
		double padding = entity.getWidth() * 0.5; // 脚锚点：padding 只计半宽（见 limitMovement 注释）
		if (!blocksPath(entity.getWorld(), from, target, padding)) return target;
		Vec3d slid = slideMovement(entity, from, target.subtract(from), padding);
		if (slid != null) return from.add(slid);
		Vec3d delta = target.subtract(from);
		double low = 0, high = 1;
		for (int iteration = 0; iteration < 18; iteration++) {
			double middle = (low + high) * 0.5;
			if (blocksPath(entity.getWorld(), from, from.add(delta.multiply(middle)), padding)) high = middle;
			else low = middle;
		}
		return from.add(delta.multiply(Math.max(0, low - 0.0001)));
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
		if (!(world instanceof ServerWorld)) return false;
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
	/** @deprecated 语义拆分：出发侧用 {@link #enclosedTrapping}，目标侧用 {@link #enclosedComplete}。 */
	public static boolean enclosed(World world, Vec3d position) {
		return enclosedTrapping(world, position);
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
		return actual != null && blocksPath(target.getWorld(), actual.getPos(), target.getPos(), 0);
	}
	public static boolean blocksDamage(LivingEntity target, DamageSource source) {
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