package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荧光幼灵主要技能「法阵激光」管理器。
 *
 * <p><b>普通</b>（未装备海晶荧光坠）：按 sp_primary → 生成 {@link LaserBeamEntity}（原蓄力/释放/消退单发，全程定身）。
 *
 * <p><b>海晶荧光坠增强</b>：按 sp_primary 展开斜后方三法阵进入 combo；之后窗口内每按一次从一个法阵朝准星发射一道
 * 缩型激光（8t、范围缩到原 30%、每目标 12 点物理只结算 1 次），消耗 1 法阵 + 累加 6s CD + 重置 5s 窗口；
 * 3 法阵用尽或 5s 未再发射则结束，按累积 CD 起算。整段移速 -50%（视角不受限）。
 */
public final class FluorescentLaserManager {

	// ===== 普通单发 =====
	private static final Map<UUID, LaserBeamEntity> ACTIVE = new ConcurrentHashMap<>();

	// ===== 增强 combo 常量 =====
	private static final int ARRAY_COUNT = 3;              // 三法阵
	private static final int WINDOW_TICKS = 100;           // 5 秒发射窗口
	private static final int SHOT_TICKS = 8;               // 每道激光 8t
	private static final int SHOT_DAMAGE_INTERVAL = 2;     // 每 2t 判定一次（共 4 次）
	private static final float SHOT_DAMAGE = 12.0f;        // 每道 12 点物理（每目标每道只 1 次）
	private static final int CD_PER_SHOT = 120;            // 每发累加 6 秒 CD
	private static final double SPEED_PENALTY = -0.5;      // 整段移速 -50%
	// 缩型激光几何（原 32/2.5 的 30%）
	private static final double ENH_BEAM_LENGTH = 24.0;        // 攻击距离最大 24 格（与渲染光柱一致）
	private static final double ENH_BEAM_RADIUS = 0.75;        // 判定半径（与渲染光柱一致）
	private static final double AIM_CONE_DEG = 10.0;           // 微自瞄：准星 10 度锥内锁定最近非白名单生物
	// 三法阵相对玩家的偏移（斜后方 左/右/上，与渲染器一致）
	private static final double ARRAY_BACK = 1.6;
	private static final double ARRAY_SIDE = 1.3;
	private static final double ARRAY_UP = 1.7;

	private static final UUID LASER_SPEED_UUID = UUID.fromString("b7e1c2d3-4f50-6172-8394-a5b6c7d8e9f0");

	private static final class ComboSession {
		boolean active = false;
		boolean isAling = false;        // 阿澪：伤害/射程/判定半径 ×1.2
		int arraysLeft = ARRAY_COUNT;
		int windowTicks = 0;
		int accumulatedCd = 0;
		int shotTicks = 0;
		LaserBeamEntity laser = null;   // 持久待机法阵实体
		Vec3d fireLock = null;          // 发射瞬间定格的世界锁定点（激光落点固定，不随法阵移动改变）
		int firingIdx = 0;              // 当前发射的法阵索引（0左/1右/2上）
		final Set<UUID> damagedThisShot = new HashSet<>();
	}

	private static final Map<UUID, ComboSession> COMBOS = new ConcurrentHashMap<>();

	private FluorescentLaserManager() {
	}

	/** 客户端「按下主要技能键」时调用。 */
	public static void onKeyPress(ServerPlayerEntity player) {
		if (!FormUtils.isAxolotlFluorescent(player)) return;
		// 阿澪天生使用三连发（无需饰品）；荧光幼灵需装备海晶荧光坠才进三连发
		boolean useEnhanced = FormUtils.isForm(player, FormIdentifiers.AXOLOTL_ALING)
				|| TrinketUtils.isWearing(player, SscAddon.SEA_CRYSTAL_PENDANT);
		if (useEnhanced) {
			onKeyPressEnhanced(player);
			return;
		}
		// 普通单发：CD 中 / 已有活跃激光 → 忽略
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return;
		LaserBeamEntity existing = ACTIVE.get(player.getUuid());
		if (existing != null && existing.isAlive()) return;
		if (!(player.getWorld() instanceof ServerWorld sw)) return;
		LaserBeamEntity laser = new LaserBeamEntity(sw, player);
		sw.spawnEntity(laser);
		ACTIVE.put(player.getUuid(), laser);
	}

	// ==================== 增强 combo ====================
	private static void onKeyPressEnhanced(ServerPlayerEntity player) {
		ComboSession s = COMBOS.computeIfAbsent(player.getUuid(), k -> new ComboSession());
		if (!(player.getWorld() instanceof ServerWorld sw)) return;
		if (!s.active) {
			// 首次：CD 中不可用
			if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return;
			s.active = true;
			s.isAling = FormUtils.isForm(player, FormIdentifiers.AXOLOTL_ALING);
			s.arraysLeft = ARRAY_COUNT;
			s.windowTicks = WINDOW_TICKS;
			s.accumulatedCd = 0;
			s.shotTicks = 0;
			applyLaserSpeed(player, true);
			// spawn 持久待机法阵实体（前方旋转法阵，跟随玩家，combo 结束 discard）
			LaserBeamEntity e = new LaserBeamEntity(sw, player, true);
			e.setArraysLeft(ARRAY_COUNT);
			sw.spawnEntity(e);
			s.laser = e;
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.3f);
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
		} else if (s.arraysLeft > 0 && s.shotTicks <= 0) {
			// 窗口内再按：发射一道
			fireShot(player, s, sw);
		}
	}

	private static void fireShot(ServerPlayerEntity player, ComboSession s, ServerWorld sw) {
		int idx = ARRAY_COUNT - s.arraysLeft;       // 0=左 1=右 2=上
		double beamLen = s.isAling ? ENH_BEAM_LENGTH * 1.2 : ENH_BEAM_LENGTH;   // 阿澪射程 ×1.2
		// 发射瞬间准星落点（方块 / 微自瞄生物）= 定格世界锁定点；激光落点固定于此，法阵随玩家移动时激光始终指向它（追踪锁定）
		Vec3d fireLock = resolveHitPoint(player, sw, beamLen);
		s.fireLock = fireLock;
		s.firingIdx = idx;
		s.shotTicks = SHOT_TICKS;
		s.damagedThisShot.clear();
		s.arraysLeft--;
		s.accumulatedCd += CD_PER_SHOT;
		s.windowTicks = WINDOW_TICKS;               // 重置窗口
		// 持久待机法阵实体进入发射态（存固定锁定点），并同步剩余法阵数
		if (s.laser != null && s.laser.isAlive()) {
			s.laser.startFiring(idx, fireLock);
			s.laser.setArraysLeft(s.arraysLeft);
		}
		Vec3d origin = arrayPos(player, idx);
		sw.playSound(null, origin.x, origin.y, origin.z,
				SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 1.5f);
		sw.playSound(null, origin.x, origin.y, origin.z,
				SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
	}

	/** 三法阵位置：玩家斜后方 左(0)/右(1)/上(2)。 */
	private static Vec3d arrayPos(ServerPlayerEntity player, int idx) {
		Vec3d eye = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
		Vec3d look = player.getRotationVec(1.0f).normalize();
		Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		Vec3d base = eye.subtract(look.multiply(ARRAY_BACK));
		return switch (idx) {
			case 0 -> base.add(right.multiply(-ARRAY_SIDE));
			case 1 -> base.add(right.multiply(ARRAY_SIDE));
			default -> base.add(0, ARRAY_UP, 0);
		};
	}

	/** 落点解析：准星射线命中方块点；若准星 10 度锥内有非白名单生物则优先取最近生物（微自瞄）。 */
	private static Vec3d resolveHitPoint(ServerPlayerEntity player, ServerWorld sw, double maxDist) {
		Vec3d eye = player.getEyePos();
		Vec3d aim = player.getRotationVec(1.0f).normalize();
		Vec3d end = eye.add(aim.multiply(maxDist));
		net.minecraft.util.hit.BlockHitResult bh = sw.raycast(new net.minecraft.world.RaycastContext(
				eye, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		Vec3d blockPoint = (bh.getType() != net.minecraft.util.hit.HitResult.Type.MISS) ? bh.getPos() : end;
		double reach = eye.distanceTo(blockPoint);
		LivingEntity target = findAimTarget(player, sw, eye, aim, reach);
		if (target != null) {
			return target.getPos().add(0, target.getHeight() * 0.5, 0);
		}
		return blockPoint;
	}

	/** 微自瞄：准星 AIM_CONE_DEG 锥内、maxDist 内、最近的非白名单存活生物。 */
	private static LivingEntity findAimTarget(ServerPlayerEntity player, ServerWorld sw, Vec3d eye, Vec3d aim, double maxDist) {
		if (maxDist < 0.5) return null;
		double cosCone = Math.cos(Math.toRadians(AIM_CONE_DEG));
		Vec3d end = eye.add(aim.multiply(maxDist));
		double expand = maxDist * Math.tan(Math.toRadians(AIM_CONE_DEG)) + 1.0;
		Box search = new Box(eye, end).expand(expand);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : sw.getOtherEntities(player, search, EntityPredicates.EXCEPT_SPECTATOR)) {
			if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
			if (WhitelistUtils.isProtected(player, le)) continue;
			Vec3d center = le.getPos().add(0, le.getHeight() * 0.5, 0);
			Vec3d toE = center.subtract(eye);
			double dist = toE.length();
			if (dist > maxDist || dist < 1.0e-4) continue;
			if (aim.dotProduct(toE.multiply(1.0 / dist)) < cosCone) continue;
			if (dist < bestDist) { bestDist = dist; best = le; }
		}
		return best;
	}

	/** 每服务端 tick 对每个在线玩家调用（推进增强 combo）。 */
	public static void tick(ServerPlayerEntity player) {
		ComboSession s = COMBOS.get(player.getUuid());
		if (s == null || !s.active) return;
		// 被 SP 悦灵净化打断：立即结束 combo（与死亡/卸饰品同路径，含失活音效 + CD 结算）
		if (player.hasStatusEffect(SscAddon.PURIFIED)) {
			endCombo(player, s);
			return;
		}
		if (player.isDead() || !FormUtils.isAxolotlFluorescent(player)
				|| !(s.isAling || TrinketUtils.isWearing(player, SscAddon.SEA_CRYSTAL_PENDANT))
				|| !(player.getWorld() instanceof ServerWorld sw)) {
			endCombo(player, s);
			return;
		}
		// 实时锁定点（准星落点）同步给渲染器，剩余待发射法阵据此预瞑转向
		if (s.laser != null && s.laser.isAlive()) {
			double preLen = s.isAling ? ENH_BEAM_LENGTH * 1.2 : ENH_BEAM_LENGTH;
			s.laser.setLockPoint(resolveHitPoint(player, sw, preLen));
		}
		// 活跃发射推进（8t，其间每 2t 判定，每目标只 1 次）
		if (s.shotTicks > 0) {
			int elapsed = SHOT_TICKS - s.shotTicks;   // 0..7
			if (elapsed % SHOT_DAMAGE_INTERVAL == 0) {
				shotDamage(sw, player, s);
			}
			s.shotTicks--;
		}
		// 窗口计时（仅在没有活跃发射时倒数）
		if (s.shotTicks <= 0) {
			s.windowTicks--;
		}
		// 结束：法阵用尽 或 窗口超时（且当前发射已完）
		if (s.shotTicks <= 0 && (s.arraysLeft <= 0 || s.windowTicks <= 0)) {
			endCombo(player, s);
		}
	}

	private static void shotDamage(ServerWorld sw, ServerPlayerEntity player, ComboSession s) {
		if (s.fireLock == null) return;
		Vec3d origin = arrayPos(player, s.firingIdx);   // 当前法阵位置（随玩家移动）
		Vec3d end = s.fireLock;                          // 固定世界锁定点
		Vec3d diff = end.subtract(origin);
		double len = diff.length();
		if (len < 1.0e-4) return;
		Vec3d dir = diff.multiply(1.0 / len);
		// 阿澪：判定半径 ×1.2
		double radius = s.isAling ? ENH_BEAM_RADIUS * 1.2 : ENH_BEAM_RADIUS;
		float dmg = s.isAling ? SHOT_DAMAGE * 1.2f : SHOT_DAMAGE;   // 阿澪伤害 ×1.2（12→14.4）
		Box box = new Box(origin, end).expand(radius);
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(player.getUuid()));
		for (LivingEntity t : targets) {
			if (s.damagedThisShot.contains(t.getUuid())) continue;   // 每道每目标只 1 次
			if (WhitelistUtils.isProtected(player, t)) continue;      // 默认白名单
			Vec3d center = t.getPos().add(0, t.getHeight() * 0.5, 0);
			double proj = center.subtract(origin).dotProduct(dir);
			if (proj < 0 || proj > len) continue;
			Vec3d closest = origin.add(dir.multiply(proj));
			if (center.squaredDistanceTo(closest) > radius * radius) continue;
			if (t.damage(t.getDamageSources().playerAttack(player), dmg)) {   // 12 点物理（阿澪 14.4）
				t.timeUntilRegen = 10;   // 主要技能：受击无敌 20→10 减半，令目标更快可再次受击
			}
			s.damagedThisShot.add(t.getUuid());
		}
	}

	private static void endCombo(ServerPlayerEntity player, ComboSession s) {
		applyLaserSpeed(player, false);
		if (s.accumulatedCd > 0) {
			PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, s.accumulatedCd);
		}
		// 结束音效：三发射尽 / 窗口超时失效 / 被 SP 悦灵净化打断 等所有结束路径统一播放
		// （信标失活，与开场 BLOCK_BEACON_ACTIVATE 呼应；全员可闻）
		if (player.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
		}
		if (s.laser != null) { s.laser.discard(); s.laser = null; }
		s.active = false;
		s.shotTicks = 0;
		s.arraysLeft = 0;
		COMBOS.remove(player.getUuid());
	}

	/** 整段技能期移动速度 -50%（固定 UUID，apply/remove 幂等）。 */
	private static void applyLaserSpeed(ServerPlayerEntity player, boolean apply) {
		var attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr == null) return;
		attr.removeModifier(LASER_SPEED_UUID);
		if (apply) {
			attr.addTemporaryModifier(new EntityAttributeModifier(
					LASER_SPEED_UUID, "Enhanced Laser Slow", SPEED_PENALTY,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	public static void onPlayerDisconnect(UUID uuid) {
		LaserBeamEntity e = ACTIVE.remove(uuid);
		if (e != null) e.discard();
		ComboSession s = COMBOS.remove(uuid);
		if (s != null && s.laser != null) s.laser.discard();
	}

	public static void clearAll() {
		for (LaserBeamEntity e : ACTIVE.values()) e.discard();
		ACTIVE.clear();
		for (ComboSession s : COMBOS.values()) if (s.laser != null) s.laser.discard();
		COMBOS.clear();
	}
}
