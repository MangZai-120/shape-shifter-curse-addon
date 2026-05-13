package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.village.raid.Raid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契灵被动行为统一处理器（服务端）。
 * 职责：
 *  - mana 自然恢复 + 周围劫掠生物 bonus（每 3s +min(count,5)）
 *  - 25% 概率村庄袭击事件触发与完成奖励
 *  - 村民/商人遭契灵击杀的物品掉落
 *  - 村民/商人逃跑判定（白名单内的契灵不会吓跑）
 *
 * 注：抗伤恢复 / 战斗状态 由 MancianimaMarkManager 处理；
 *     药水免疫 由 form_familiar_fox_mancianima_no_buff_effect.json 处理。
 */
public final class MancianimaPassive {
	private MancianimaPassive() {}

	private static final Map<UUID, AssaultData> ASSAULTS = new ConcurrentHashMap<>();
	private static final long ASSAULT_ROLL_COOLDOWN = 24000L; // 1 MC 天内只能袭击 1 次（跨重启持久化，仅在“成功完成”后记入）
	private static final double ASSAULT_ABORT_DISTANCE_SQ = 96.0 * 96.0; // 超过起点 96 格则中止
	private static final int ASSAULT_PREPARE_TICKS = 200; // 读条 10s
	private static final int RAIDER_PILLAGER_COUNT = 4;
	private static final int RAIDER_VINDICATOR_COUNT = 2;
	private static final int RAIDER_LINGER_MIN_TICKS = 1200; // 袭击结束后在村庄逗留最少 1 分钟
	private static final int RAIDER_LINGER_MAX_TICKS = 6000; // 最多 5 分钟
	private static final int RAIDER_MARCH_DESPAWN_DISTANCE_SQ = 128 * 128; // 行军阶段超过 128 格无玩家则清理
	private static final int RAIDER_MARCH_DISTANCE = 200; // 行军目标：远离村庄 200 格的位置

	/** 劫掠军队伍：跟踪袭击 spawn 出的 raider，负责 linger → march → 脱适后清理。 */
	private enum RaiderPhase { ACTIVE, LINGER, MARCH }
	private static class RaiderGroup {
		final Set<UUID> raiderUuids = ConcurrentHashMap.newKeySet();
		final net.minecraft.util.Identifier dimensionId;
		final BlockPos villageCenter;
		RaiderPhase phase = RaiderPhase.ACTIVE;
		long phaseEndTime = Long.MAX_VALUE;
		BlockPos marchTarget;
		RaiderGroup(net.minecraft.util.Identifier dim, BlockPos center) {
			this.dimensionId = dim;
			this.villageCenter = center;
		}
	}
	/** key = 玩家 UUID（同一玩家同时只能拥有一组劫掠军）。 */
	private static final Map<UUID, RaiderGroup> RAIDER_GROUPS = new ConcurrentHashMap<>();

	// “被君临”还在生效的村民/商人 UUID + 上次刷新 tick
	private static final Map<UUID, Long> FLEE_AFFECTED = new ConcurrentHashMap<>();
	private static final UUID MANCIANIMA_FEAR_SLOW_UUID = UUID.fromString("7f3e2c4a-3b16-4ad2-9d4d-2bf1d8a5d111");
	private static final double FLEE_RADIUS = 12.0;
	private static final int FLEE_LINGER_TICKS = 60; // 离开后 3s 才清除 debuff

	private static final Random RNG = new Random();

	public static void tick(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;
		// 驱逃逻辑：每 10t 检一次，低开销
		if (player.age % 10 == 0) tickFlee(player);
		// 袭击读条推进（每 tick）
		tickAssaultPrepare(player);
		// 袭击超出范围检查（每 20t）
		if (player.age % 20 == 0) checkAssaultRange(player);
		if (player.age % 60 != 0) return;
		// 1. mana 劫掠 bonus（每 3s）
		int raiderCount = countNearbyRaiders(player, 10.0);
		if (raiderCount > 0) {
			ManaUtils.gainPlayerMana(player, Math.min(raiderCount, 5));
		}
		// 2. 过期卸载驱逃修饰符
		cleanupExpiredFlee(player.getWorld().getTime());
	}

	/** 敷鼎袭击触发入口：敷钟后调用。返回是否成功触发。 */
	public static boolean tryTriggerAssaultByBell(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return false;
		UUID pid = player.getUuid();
		if (ASSAULTS.containsKey(pid)) return false; // 正在进行中
		long now = player.getWorld().getTime();
		net.minecraft.server.MinecraftServer server = player.getServer();
		MancianimaAssaultState state = server == null ? null : MancianimaAssaultState.get(server);
		Long last = state == null ? null : state.lastRoll.get(pid);
		if (last != null && now - last < ASSAULT_ROLL_COOLDOWN) {
			player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.cooldown"), true);
			return false;
		}
		Box markBox = player.getBoundingBox().expand(64.0);
		Set<UUID> villagerTargets = new HashSet<>();
		List<MerchantEntity> merchants = player.getWorld().getEntitiesByClass(MerchantEntity.class, markBox,
				e -> e.isAlive() && !e.isRemoved());
		for (MerchantEntity m : merchants) villagerTargets.add(m.getUuid());
		if (villagerTargets.isEmpty()) {
			player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.no_villagers"), true);
			return false;
		}
		// 仅自定义袭击逻辑：不施加 BAD_OMEN 也不召来原版 raid，避免与原版袭击 bossbar 冲突
		// 让附近铁傀儡主动追击契灵
		for (IronGolemEntity g : player.getWorld().getEntitiesByClass(IronGolemEntity.class, markBox,
				e -> e.isAlive() && !e.isRemoved())) {
			g.setTarget(player);
			g.setAttacker(player);
		}
		// 创建读条 bossbar（黄色，10s后切换为袭击 bossbar）
		ServerBossBar prepareBar = new ServerBossBar(
				Text.translatable("ssc_addon.mancianima.assault.preparing"),
				BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
		prepareBar.setPercent(1.0f);
		prepareBar.addPlayer(player);
		AssaultData data = new AssaultData(villagerTargets, prepareBar,
				player.getWorld().getRegistryKey().getValue(), player.getPos(), now);
		data.prepareTicks = ASSAULT_PREPARE_TICKS;
		ASSAULTS.put(pid, data);
		// 注：冷却仅在 onAssaultTargetDeath 袭击成功完成后才记入，走远/下线/重启不扭冷却
		player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.preparing_msg", villagerTargets.size()), false);
		if (player.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EVENT_RAID_HORN.value(), player.getSoundCategory(), 1.0f, 0.9f);
		}
		return true;
	}

	private static void tickFlee(ServerPlayerEntity player) {
		Box box = player.getBoundingBox().expand(FLEE_RADIUS);
		long now = player.getWorld().getTime();
		List<MerchantEntity> merchants = player.getWorld().getEntitiesByClass(MerchantEntity.class, box,
				e -> e.isAlive() && !e.isRemoved()
						&& (e instanceof VillagerEntity || e instanceof WanderingTraderEntity));
		for (MerchantEntity m : merchants) {
			if (WhitelistUtils.isProtected(player, m)) continue;
			// 纯逵梦向量退避
			Vec3d away = m.getPos().subtract(player.getPos());
			if (away.lengthSquared() < 1.0e-4) {
				away = new Vec3d(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5);
			}
			away = away.normalize().multiply(8.0);
			Vec3d target = m.getPos().add(away.x, 0, away.z);
			if (m instanceof PathAwareEntity) {
				m.getNavigation().startMovingTo(target.x, m.getY(), target.z, 1.0);
			}
			// -25% 移速（乘法修饰，幂等衡）
			EntityAttributeInstance attr = m.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (attr != null && attr.getModifier(MANCIANIMA_FEAR_SLOW_UUID) == null) {
				attr.addPersistentModifier(new EntityAttributeModifier(MANCIANIMA_FEAR_SLOW_UUID,
						"Mancianima fear slow", -0.25,
						EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
			}
			FLEE_AFFECTED.put(m.getUuid(), now);
		}
	}

	/** 超过 linger 未刷新 → 移除修饰。 */
	private static void cleanupExpiredFlee(long now) {
		Iterator<Map.Entry<UUID, Long>> it = FLEE_AFFECTED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			if (now - e.getValue() <= FLEE_LINGER_TICKS) continue;
			it.remove();
		}
		// 注意：attr modifier 的实际移除需要在 entity 存在时才能做，由 onMerchantTickRemove 处理不可行。
		// 改为：下一轮 tickFlee 没有重新加入时，依靠下面的 maybeRemoveFleeModifier 反向扫描。
	}

	/** 外部调用（每驱逃 tick 后）：对不再被追踪的实体移除 modifier。这里由 SscAddon 每秒扫一次全世界不现实，
	 * 	改为在 tickFlee 中在 +modifier 同时记录 last seen，手动在 LivingEntityTickEvent 中检查。
	 * 	为避免额外 mixin，这里提供一个 static helper，由正在 tick 的商人自己调用。。。
	 * 	简化：这里不主动卸载；改用 SscAddon 服务器 tick 中调用下面的方法。 */
	public static void serverGlobalFleeCleanup(net.minecraft.server.MinecraftServer server) {
		long now = server.getOverworld().getTime();
		if (FLEE_AFFECTED.isEmpty()) return;
		for (ServerWorld world : server.getWorlds()) {
			for (UUID id : new ArrayList<>(FLEE_AFFECTED.keySet())) {
				if (now - FLEE_AFFECTED.getOrDefault(id, 0L) <= FLEE_LINGER_TICKS) continue;
				net.minecraft.entity.Entity ent = world.getEntity(id);
				if (ent instanceof LivingEntity le) {
					EntityAttributeInstance attr = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
					if (attr != null && attr.getModifier(MANCIANIMA_FEAR_SLOW_UUID) != null) {
						attr.removeModifier(MANCIANIMA_FEAR_SLOW_UUID);
					}
					FLEE_AFFECTED.remove(id);
				}
			}
		}
	}

	private static int countNearbyRaiders(PlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		return player.getWorld().getEntitiesByClass(RaiderEntity.class, box,
				e -> e.isAlive() && !e.isRemoved()).size();
	}

	private static class AssaultData {
		final Set<UUID> targets;
		final int initialCount;
		ServerBossBar bossBar; // 读条阶段 = prepareBar；spawn 后切为 mainBar
		final net.minecraft.util.Identifier dimensionId;
		final Vec3d origin;
		int prepareTicks; // > 0 表示读条中；到 0 时 spawn raider 并切换 bossbar
		boolean raidersSpawned = false;
		// —— 离开/暂停机制 ——
		boolean paused = false; // true：玩家离开村庄，bossbar 灰色显示
		long startTime;          // 触发袭击时的 world.getTime()
		long expireTime;         // 触发后 24000 ticks（一日）后自动失败
		AssaultData(Set<UUID> targets, ServerBossBar bossBar,
				net.minecraft.util.Identifier dimensionId, Vec3d origin, long startTime) {
			this.targets = targets;
			this.initialCount = targets.size();
			this.bossBar = bossBar;
			this.dimensionId = dimensionId;
			this.origin = origin;
			this.startTime = startTime;
			this.expireTime = startTime + 24000L;
		}
	}

	/** 每 tick 推进读条；到 0 则 spawn 劫掠军并切换 为 主 bossbar。 */
	private static void tickAssaultPrepare(ServerPlayerEntity player) {
		AssaultData data = ASSAULTS.get(player.getUuid());
		if (data == null || data.raidersSpawned) return;
		data.prepareTicks--;
		if (data.prepareTicks > 0) {
			data.bossBar.setPercent(Math.max(0f, (float) data.prepareTicks / ASSAULT_PREPARE_TICKS));
			return;
		}
		// 读条完成：移除 prepareBar，创建 main bossbar，spawn raider
		data.bossBar.clearPlayers();
		data.bossBar.setVisible(false);
		ServerBossBar mainBar = new ServerBossBar(
				Text.translatable("ssc_addon.mancianima.assault.bossbar", data.targets.size()),
				BossBar.Color.RED, BossBar.Style.NOTCHED_10);
		mainBar.setPercent(1.0f);
		mainBar.addPlayer(player);
		data.bossBar = mainBar;
		data.raidersSpawned = true;
		spawnRaiders(player);
		player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.start", data.targets.size()), false);
		if (player.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EVENT_RAID_HORN.value(), player.getSoundCategory(), 1.5f, 0.85f);
		}
	}

	/** 以玩家面向为方向，在附近村庄集会点外圈生成劫掠军；随机阵容，带一面不祥之旗。 */
	private static void spawnRaiders(ServerPlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) return;
		BlockPos villageCenter = findVillageCenter(world, player);
		BlockPos spawnPos = findRaiderSpawnPos(world, player, villageCenter);
		RaiderGroup group = new RaiderGroup(world.getRegistryKey().getValue(), villageCenter);

		// 随机阵容：4 Pillager + 2 Vindicator 为基础；30% 将 1 只 Vindicator 替换为 Evoker；30% +1 Witch；10% +1 Ravager
		int pillagers = RAIDER_PILLAGER_COUNT;
		int vindicators = RAIDER_VINDICATOR_COUNT;
		int evokers = 0;
		int witches = 0;
		int ravagers = 0;
		if (RNG.nextFloat() < 0.30f && vindicators > 0) { vindicators--; evokers++; }
		if (RNG.nextFloat() < 0.30f) witches++;
		if (RNG.nextFloat() < 0.10f) ravagers++;

		boolean leaderAssigned = false;
		for (int i = 0; i < pillagers; i++) {
			PillagerEntity p = EntityType.PILLAGER.create(world);
			if (p == null) continue;
			BlockPos pp = spawnPos.add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);
			p.refreshPositionAndAngles(pp.getX() + 0.5, pp.getY(), pp.getZ() + 0.5, 0f, 0f);
			p.initialize(world, world.getLocalDifficulty(pp), SpawnReason.EVENT, null, null);
			p.setPersistent();
			configureRaiderPatrol(p, villageCenter);
			if (!leaderAssigned) {
				// 首位 Pillager 带上不祥之旗作为队长
				p.setPatrolLeader(true);
				p.equipStack(EquipmentSlot.HEAD, Raid.getOminousBanner());
				leaderAssigned = true;
			}
			world.spawnEntity(p);
			group.raiderUuids.add(p.getUuid());
		}
		for (int i = 0; i < vindicators; i++) {
			VindicatorEntity v = EntityType.VINDICATOR.create(world);
			if (v == null) continue;
			BlockPos vp = spawnPos.add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);
			v.refreshPositionAndAngles(vp.getX() + 0.5, vp.getY(), vp.getZ() + 0.5, 0f, 0f);
			v.initialize(world, world.getLocalDifficulty(vp), SpawnReason.EVENT, null, null);
			v.setPersistent();
			configureRaiderPatrol(v, villageCenter);
			world.spawnEntity(v);
			group.raiderUuids.add(v.getUuid());
		}
		for (int i = 0; i < evokers; i++) {
			EvokerEntity e = EntityType.EVOKER.create(world);
			if (e == null) continue;
			BlockPos ep = spawnPos.add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);
			e.refreshPositionAndAngles(ep.getX() + 0.5, ep.getY(), ep.getZ() + 0.5, 0f, 0f);
			e.initialize(world, world.getLocalDifficulty(ep), SpawnReason.EVENT, null, null);
			e.setPersistent();
			configureRaiderHome(e, villageCenter, 32);
			world.spawnEntity(e);
			group.raiderUuids.add(e.getUuid());
		}
		for (int i = 0; i < witches; i++) {
			WitchEntity w = EntityType.WITCH.create(world);
			if (w == null) continue;
			BlockPos wp = spawnPos.add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);
			w.refreshPositionAndAngles(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 0f, 0f);
			w.initialize(world, world.getLocalDifficulty(wp), SpawnReason.EVENT, null, null);
			w.setPersistent();
			configureRaiderHome(w, villageCenter, 32);
			world.spawnEntity(w);
			group.raiderUuids.add(w.getUuid());
		}
		for (int i = 0; i < ravagers; i++) {
			RavagerEntity r = EntityType.RAVAGER.create(world);
			if (r == null) continue;
			BlockPos rp = spawnPos.add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);
			r.refreshPositionAndAngles(rp.getX() + 0.5, rp.getY(), rp.getZ() + 0.5, 0f, 0f);
			r.initialize(world, world.getLocalDifficulty(rp), SpawnReason.EVENT, null, null);
			r.setPersistent();
			configureRaiderHome(r, villageCenter, 32);
			world.spawnEntity(r);
			group.raiderUuids.add(r.getUuid());
		}

		RAIDER_GROUPS.put(player.getUuid(), group);

		// 提示附近铁傀儡追击玩家
		Box markBox = player.getBoundingBox().expand(64.0);
		for (IronGolemEntity g : world.getEntitiesByClass(IronGolemEntity.class, markBox,
				e -> e.isAlive() && !e.isRemoved())) {
			g.setTarget(player);
			g.setAttacker(player);
		}
	}

	/** 让 raider 在整个村庄范围内活动；PatrolEntity 设 patrolTarget 并激活巡逻 AI，
	 *  让劫掠者真正朝村庄中心移动而非只在出生点徘徊。 */
	private static void configureRaiderPatrol(net.minecraft.entity.mob.PatrolEntity r, BlockPos center) {
		// 把活动范围放大到 64 格，覆盖整个村庄；PatrolEntity 内部 home 距离判定也据此
		r.setPositionTarget(center, 64);
		r.setPatrolTarget(center);
		// 激活巡逻 AI（默认 patrolling=false，PatrolGoal 不会触发主动移动）
		((net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity.PatrolEntityAccessor) r)
				.invokeSetPatrolling(true);
	}

	/** 非 PatrolEntity 的 raider（Witch / Evoker / Ravager）只需设 home。 */
	private static void configureRaiderHome(MobEntity m, BlockPos center, int range) {
		m.setPositionTarget(center, range);
	}

	private static BlockPos findVillageCenter(ServerWorld world, ServerPlayerEntity player) {
		return world.getPointOfInterestStorage()
				.getNearestPosition(
						entry -> entry.matchesKey(PointOfInterestTypes.MEETING),
						player.getBlockPos(), 64,
						PointOfInterestStorage.OccupationStatus.ANY)
				.orElse(player.getBlockPos());
	}

	/** 以“玩家面向”为方向在村庄中心前方 32 格位置作为入场点；这样劫掠军会从玩家移动方向进攻。 */
	private static BlockPos findRaiderSpawnPos(ServerWorld world, ServerPlayerEntity player, BlockPos villageCenter) {
		Vec3d facing = player.getRotationVec(1.0f);
		double flatLen = Math.hypot(facing.x, facing.z);
		Vec3d dir;
		if (flatLen > 1e-3) {
			dir = new Vec3d(facing.x / flatLen, 0, facing.z / flatLen);
		} else {
			double ang = RNG.nextDouble() * Math.PI * 2;
			dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
		}
		dir = dir.multiply(32);
		BlockPos approxTarget = villageCenter.add((int) dir.x, 0, (int) dir.z);
		return findGroundAt(world, approxTarget);
	}

	private static BlockPos findGroundAt(ServerWorld world, BlockPos pos) {
		int x = pos.getX(), z = pos.getZ();
		int startY = Math.min(world.getTopY() - 2, pos.getY() + 16);
		for (int y = startY; y > world.getBottomY() + 1; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockPos below = p.down();
			net.minecraft.block.BlockState belowState = world.getBlockState(below);
			net.minecraft.block.BlockState atState = world.getBlockState(p);
			net.minecraft.block.BlockState upState = world.getBlockState(p.up());
			// 站立位与上方必须为空气（避免方块内部），且都不能是流体
			if (!atState.isAir() || !upState.isAir()) continue;
			if (!atState.getFluidState().isEmpty() || !upState.getFluidState().isEmpty()) continue;
			// 脚下必须是固体且不是流体（排除水面/岩浆面）
			if (!belowState.isSolidBlock(world, below)) continue;
			if (!belowState.getFluidState().isEmpty()) continue;
			return p;
		}
		// 兜底：原始位置上方 + 1 至少不在地下
		return pos.up();
	}

	/**
	 * 玩家走出袭击起点过远或跨维度：暂停袭击。
	 * - 暂停时 bossbar 切灰色 (WHITE) 显示"离开区域"
	 * - 玩家回来 → 自动恢复 (恢复 RED 颜色, 显示原 bossbar 文本)
	 * - 当天内 (24000 ticks) 未回来 → 显示"袭击失败" 5 秒后清理，劫掠军 LINGER
	 * 注意：暂停期间劫掠军仍在攻击村民，进度仍在前进，玩家回来时会看到当前实际剩余村民数。
	 */
	private static void checkAssaultRange(ServerPlayerEntity player) {
		AssaultData data = ASSAULTS.get(player.getUuid());
		if (data == null) return;
		long now = player.getWorld().getTime();
		boolean differentDim = !player.getWorld().getRegistryKey().getValue().equals(data.dimensionId);
		boolean tooFar = player.getPos().squaredDistanceTo(data.origin) > ASSAULT_ABORT_DISTANCE_SQ;
		boolean outOfRange = differentDim || tooFar;

		if (outOfRange) {
			// 检查超时（一日内未回 → 袭击失败）
			if (now > data.expireTime) {
				data.bossBar.setColor(BossBar.Color.WHITE);
				data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.failed"));
				data.bossBar.setPercent(0f);
				data.bossBar.clearPlayers();
				data.bossBar.setVisible(false);
				ASSAULTS.remove(player.getUuid());
				player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.failed_msg"), false);
				// 劫掠军继续 LINGER → MARCH 撤离
				startLingerForGroup(player);
				return;
			}
			// 进入暂停状态
			if (!data.paused) {
				data.paused = true;
				data.bossBar.setColor(BossBar.Color.WHITE);
				data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.paused"));
			}
		} else {
			// 玩家回到范围 → 恢复
			if (data.paused) {
				data.paused = false;
				if (data.raidersSpawned) {
					data.bossBar.setColor(BossBar.Color.RED);
					int remaining = data.targets.size();
					data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.bossbar", remaining));
					data.bossBar.setPercent(data.initialCount > 0 ? (float) remaining / data.initialCount : 0f);
				} else {
					data.bossBar.setColor(BossBar.Color.YELLOW);
					data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.preparing"));
				}
				player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.resumed"), false);
			}
		}
	}

	/** mob 死亡时调用：若属于某玩家的袭击目标 → 移除并检查是否完成。 */
	public static void onAssaultTargetDeath(LivingEntity dead) {
		if (dead.getWorld().isClient()) return;
		UUID deadId = dead.getUuid();
		Iterator<Map.Entry<UUID, AssaultData>> it = ASSAULTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, AssaultData> e = it.next();
			AssaultData data = e.getValue();
			if (!data.targets.remove(deadId)) continue;
			// 读条阶段不递减 bossbar（但仍从 targets 中移除，避免重复计数）
			if (!data.raidersSpawned) continue;
			// 更新 bossbar
			int remaining = data.targets.size();
			data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.bossbar", remaining));
			data.bossBar.setPercent(data.initialCount > 0 ? (float) remaining / data.initialCount : 0f);
			if (remaining == 0) {
				ServerPlayerEntity p = dead.getWorld().getServer() == null ? null
						: dead.getWorld().getServer().getPlayerManager().getPlayer(e.getKey());
				if (p != null) {
					// 袭击成功完成：在此记录当天冷却（持久化）
					net.minecraft.server.MinecraftServer srv = p.getServer();
					if (srv != null) {
						MancianimaAssaultState state = MancianimaAssaultState.get(srv);
						state.lastRoll.put(p.getUuid(), p.getWorld().getTime());
						state.markDirty();
					}
					// 奖励以实体掉落物形式出现在玩家 2 格半径内（不直接进背包）
					dropAssaultRewards(p);
					p.sendMessage(Text.translatable("ssc_addon.mancianima.assault.complete"), false);
					if (p.getWorld() instanceof ServerWorld sw) {
						sw.playSound(null, p.getX(), p.getY(), p.getZ(),
								SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, p.getSoundCategory(), 1.0f, 1.0f);
					}
				}
				data.bossBar.clearPlayers();
				data.bossBar.setVisible(false);
				it.remove();
				// 袭击成功后劫掠军进入 LINGER。key 为玩家 UUID。
				if (e.getKey() != null) startLingerForGroupByPlayerId(e.getKey(), dead.getWorld() instanceof ServerWorld sw0 ? sw0 : null);
			}
		}
	}

	/** 在玩家周围 2 格半径内以实体项掉落奖励。 */
	private static void dropAssaultRewards(ServerPlayerEntity p) {
		dropAroundPlayer(p, new ItemStack(Items.EMERALD_BLOCK, 2));
		dropAroundPlayer(p, new ItemStack(Items.TOTEM_OF_UNDYING, 1));
	}

	private static void dropAroundPlayer(ServerPlayerEntity p, ItemStack stack) {
		double ang = RNG.nextDouble() * Math.PI * 2;
		double r = RNG.nextDouble() * 2.0;
		double x = p.getX() + Math.cos(ang) * r;
		double z = p.getZ() + Math.sin(ang) * r;
		double y = p.getY() + 0.5;
		net.minecraft.entity.ItemEntity ie = new net.minecraft.entity.ItemEntity(p.getWorld(), x, y, z, stack);
		ie.setVelocity((RNG.nextDouble() - 0.5) * 0.1, 0.2, (RNG.nextDouble() - 0.5) * 0.1);
		ie.setPickupDelay(10);
		p.getWorld().spawnEntity(ie);
	}

	/** 将玩家名下的 RaiderGroup 转为 LINGER 阶段。 */
	private static void startLingerForGroup(ServerPlayerEntity player) {
		startLingerForGroupByPlayerId(player.getUuid(),
				player.getWorld() instanceof ServerWorld sw ? sw : null);
	}

	private static void startLingerForGroupByPlayerId(UUID playerId, ServerWorld worldHint) {
		RaiderGroup group = RAIDER_GROUPS.get(playerId);
		if (group == null || group.phase != RaiderPhase.ACTIVE) return;
		group.phase = RaiderPhase.LINGER;
		long now = worldHint != null ? worldHint.getTime() : 0L;
		group.phaseEndTime = now + RAIDER_LINGER_MIN_TICKS
				+ RNG.nextInt(RAIDER_LINGER_MAX_TICKS - RAIDER_LINGER_MIN_TICKS);
	}

	/** 服务器主线程定期驱动（建议每秒一次）。处理 RaiderGroup 的 LINGER → MARCH → 脱适清理。 */
	public static void tickRaiderGroups(net.minecraft.server.MinecraftServer server) {
		// 兜底：检查所有进行中袭击是否已超时（玩家下线情况下 checkAssaultRange 不会被调用）
		if (!ASSAULTS.isEmpty()) {
			ServerWorld ow = server.getOverworld();
			long now = ow != null ? ow.getTime() : 0L;
			Iterator<Map.Entry<UUID, AssaultData>> ait = ASSAULTS.entrySet().iterator();
			while (ait.hasNext()) {
				Map.Entry<UUID, AssaultData> e = ait.next();
				AssaultData d = e.getValue();
				if (now > d.expireTime) {
					d.bossBar.clearPlayers();
					d.bossBar.setVisible(false);
					ait.remove();
					startLingerForGroupByPlayerId(e.getKey(), ow);
				}
			}
		}
		if (RAIDER_GROUPS.isEmpty()) return;
		Iterator<Map.Entry<UUID, RaiderGroup>> it = RAIDER_GROUPS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, RaiderGroup> entry = it.next();
			RaiderGroup group = entry.getValue();
			ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(
					net.minecraft.registry.RegistryKeys.WORLD, group.dimensionId));
			if (world == null) { it.remove(); continue; }
			// 收集仍存活的 raider 实体
			List<MobEntity> alive = new ArrayList<>();
			Iterator<UUID> uit = group.raiderUuids.iterator();
			while (uit.hasNext()) {
				UUID uid = uit.next();
				net.minecraft.entity.Entity ent = world.getEntity(uid);
				if (ent instanceof MobEntity m && m.isAlive() && !m.isRemoved()) {
					alive.add(m);
				} else if (ent == null || ent.isRemoved()) {
					// 实体可能在未加载区块；保留 UUID，等区块再次加载时再判
					if (ent == null) continue;
					uit.remove();
				}
			}
			if (group.raiderUuids.isEmpty()) { it.remove(); continue; }
			long now = world.getTime();
			switch (group.phase) {
				case ACTIVE -> { /* 由 onAssaultTargetDeath / abort 主动转 LINGER */ }
				case LINGER -> {
					if (now >= group.phaseEndTime) {
						// 进入 MARCH：选一个远离村庄方向 200 格的 patrol 目标
						double ang = RNG.nextDouble() * Math.PI * 2;
						BlockPos target = group.villageCenter.add(
								(int) (Math.cos(ang) * RAIDER_MARCH_DISTANCE), 0,
								(int) (Math.sin(ang) * RAIDER_MARCH_DISTANCE));
						group.marchTarget = target;
						group.phase = RaiderPhase.MARCH;
						for (MobEntity m : alive) {
							// 解除 home，让他们能离开村庄
							m.setPositionTarget(BlockPos.ORIGIN, -1);
							if (m instanceof net.minecraft.entity.mob.PatrolEntity pe) {
								pe.setPatrolTarget(target);
							}
						}
					}
				}
				case MARCH -> {
					// 检查整组 raider 是否都不在任何玩家 128 格以内 → 全员清理
					boolean anyNearPlayer = false;
					for (MobEntity m : alive) {
						for (ServerPlayerEntity sp : world.getPlayers()) {
							if (sp.squaredDistanceTo(m) <= RAIDER_MARCH_DESPAWN_DISTANCE_SQ) {
								anyNearPlayer = true;
								break;
							}
						}
						if (anyNearPlayer) break;
					}
					if (!anyNearPlayer) {
						for (MobEntity m : alive) m.discard();
						it.remove();
					}
				}
			}
		}
	}

	/** 村民/商人死亡时：契灵击杀 → 掉落部分可售卖物品 + 概率绿宝石。 */
	public static void onMerchantKilledByMancianima(MerchantEntity merchant, ServerPlayerEntity killer) {
		TradeOfferList offers = merchant.getOffers();
		boolean droppedSomething = false;
		if (offers != null && !offers.isEmpty()) {
			int picks = Math.min(2, offers.size());
			List<TradeOffer> shuffled = new ArrayList<>(offers);
			Collections.shuffle(shuffled, RNG);
			for (int i = 0; i < picks; i++) {
				ItemStack sell = shuffled.get(i).getSellItem();
				if (sell == null || sell.isEmpty()) continue;
				ItemStack drop = sell.copy();
				drop.setCount(Math.max(1, sell.getCount()));
				merchant.dropStack(drop);
				droppedSomething = true;
			}
		}
		// 无交易的村民（无职业/幼年）占多数：fallback 掉点 base 材料
		if (!droppedSomething) {
			merchant.dropStack(new ItemStack(Items.WHEAT, 1 + RNG.nextInt(3)));
			merchant.dropStack(new ItemStack(Items.BREAD, 1 + RNG.nextInt(2)));
		}
		// 30% 额外绿宝石（1-3）
		if (RNG.nextDouble() < 0.30) {
			merchant.dropStack(new ItemStack(Items.EMERALD, 1 + RNG.nextInt(3)));
		}
	}

	/** 是否应当因为契灵在场而逃跑（村民/商人 FleeEntityGoal 的谓词）。 */
	public static boolean shouldVillagerFlee(LivingEntity villager, PlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return false;
		if (player instanceof ServerPlayerEntity sp && WhitelistUtils.isProtected(sp, villager)) return false;
		return true;
	}

	public static void clearAll() {
		for (AssaultData data : ASSAULTS.values()) {
			if (data.bossBar != null) {
				data.bossBar.clearPlayers();
				data.bossBar.setVisible(false);
			}
		}
		ASSAULTS.clear();
		// LAST_ASSAULT_ROLL 已迁移到 MancianimaAssaultState（持久化），此处无需清空
		FLEE_AFFECTED.clear();
		// raider 组在 server 重启时丢弃跟踪（实体本身已带 Persistent 持久化），避免悬空状态
		RAIDER_GROUPS.clear();
	}
}
