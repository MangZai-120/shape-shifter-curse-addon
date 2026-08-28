package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;

/**
 * 荧光幼灵主要技能「法阵激光」实体（自驱状态机）。
 *
 * <p>阶段：
 * <ul>
 *   <li>CHARGE（0~7s=140t）：玩家定身（ROOTED，仅锁移动/跳跃、视角自由）。前方放法阵；
 *       0~2.5s 四条 END_ROD 白线从法阵四角延伸到 24 格；2.5~6.5s 四线随法阵旋转 + 加速向中心聚拢、
 *       末尾聚到较小间隔并慢慢停转；6.5~7s 停稳。可被净化打断（取消、无 CD）。</li>
 *   <li>RELEASE（7~10s=60t）：视角限速 5°/s（LASER_STATE=2 由 ViewRateLimitMixin 处理）。
 *       沿朝向发射直径 5 格、长 24 格的穿墙光柱，每 4t 造成 6 点魔法伤害（默认白名单）；
 *       周围白/青螺旋粒子沿光柱前进。不可被打断。</li>
 *   <li>FADE（10~11.5s=30t）：光柱半径 1.5s 内缩小后随法阵消失；结束后设 20s CD。</li>
 * </ul>
 * 渲染（法阵 + 光柱）由 {@code FluorescentLaserRenderer} 负责；四线/螺旋/爆裂粒子服务端生成（所有人可见）。
 */
public class LaserBeamEntity extends Entity {

	// ===== 时序 =====
	private static final int CHARGE_TICKS = 140;      // 7 秒蓄力
	private static final int RELEASE_TICKS = 60;      // 3 秒激光
	private static final int FADE_TICKS = 30;         // 1.5 秒消退
	private static final int CD_TICKS = 400;          // 20 秒 CD

	// ===== 几何 =====
	private static final double ARRAY_DIST = 3.0;     // 法阵在玩家前方距离
	private static final double BEAM_LENGTH = 32.0;    // 光柱/四线长度
	private static final double BEAM_RADIUS = 2.5;     // 光柱半径（直径 5 格）

	// ===== 海晶荧光坠增强（三连发单道视觉）=====
	private static final float ENH_BEAM_RADIUS = 0.75f;   // 增强光柱半径（原 2.5 的 30%）
	private static final float ENH_ARRAY_SCALE = 0.3f;    // 增强法阵缩到原 30%
	private static final int ENH_SHOT_TICKS = 8;          // 增强单道存活 8t（纯视觉）
	// 注：增强激光射程由 Manager.ENH_BEAM_LENGTH 管（resolveHitPoint 射线长度），实体不再单独存 ENH_LENGTH（渲染改用实时距离）

	// ===== 伤害 =====
	private static final int DAMAGE_INTERVAL = 10;      // 每 10t 结算
	private static final float DAMAGE = 20.0f;          // 每次 20 魔法伤害（释放 60t 共 6 次 = 120）

	// ===== 同步数据（供渲染器）=====
	private static final TrackedData<Integer> PHASE = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);       // 0 CHARGE / 1 RELEASE / 2 FADE
	private static final TrackedData<Integer> PHASE_TICK = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);  // 当前阶段已用 tick
	private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	// 海晶荧光坠增强：单道视觉标记 + 发射方向（Vec3d 拆 3 个 float 同步给渲染器）
	private static final TrackedData<Boolean> ENHANCED = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> DIR_X = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DIR_Y = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DIR_Z = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> FIRING = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> ARRAYS_LEFT = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> FIRING_IDX = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	// 海晶荧光坠增强：实时锁定点（准星落点，服务端每 tick 同步）——供渲染器让剩余待发射法阵预瞑转向
	private static final TrackedData<Float> LOCK_X = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> LOCK_Y = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> LOCK_Z = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private enum Phase { CHARGE, RELEASE, FADE }

	private Phase phase = Phase.CHARGE;
	private int phaseTicks = 0;
	private UUID ownerUuid;
	private float damage = DAMAGE;
	private int releaseTicks = RELEASE_TICKS;
	private int enhFiringTicks = 0;   // 增强发射态剩余 tick

	/** 客户端渲染器粒子门控：记录本阶段 tick 已发过粒子（render 每帧调用，同 tick 多帧只放行一次，
	 *  保证粒子频率与服务端每 tick 一致；非同步字段，仅客户端使用）。 */
	public int clientParticleGate = -1;

	public LaserBeamEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public LaserBeamEntity(World world, ServerPlayerEntity owner) {
		super(SscAddon.LASER_BEAM_ENTITY, world);
		this.ownerUuid = owner.getUuid();
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
		this.noClip = true;
	}

	/** 海晶荧光坠增强：持久「待机法阵」实体——combo 期间跟随玩家、在其前方显示旋转法阵（像原激光）；
	 *  发射时 startFiring 进 8t 发射态额外画光柱。不定身/不伤害/不设 CD（由 Manager 管，combo 结束 discard）。 */
	public LaserBeamEntity(World world, ServerPlayerEntity owner, boolean enhancedStandby) {
		super(SscAddon.LASER_BEAM_ENTITY, world);
		this.ownerUuid = owner.getUuid();
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
		this.dataTracker.set(ENHANCED, true);
		this.dataTracker.set(PHASE, 1);
		this.noClip = true;
	}

	/** 发射一道：进入 8t 发射态，记录发射法阵索引 + 发射瞬间定格的世界锁定点。
	 *  激光落点固定为此世界点，法阵随玩家移动时激光始终指向它（追踪锁定，方向由渲染器/伤害按当前法阵位置实时算）。 */
	public void startFiring(int arrayIdx, Vec3d fireLock) {
		this.dataTracker.set(FIRING, true);
		this.dataTracker.set(FIRING_IDX, arrayIdx);
		this.dataTracker.set(DIR_X, (float) fireLock.x);
		this.dataTracker.set(DIR_Y, (float) fireLock.y);
		this.dataTracker.set(DIR_Z, (float) fireLock.z);
		this.enhFiringTicks = ENH_SHOT_TICKS;
	}

	public boolean isFiring() {
		return this.dataTracker.get(FIRING);
	}

	/** combo 当前剩余法阵数（渲染器据此画斜后方剩余法阵）。 */
	public void setArraysLeft(int n) {
		this.dataTracker.set(ARRAYS_LEFT, n);
	}

	public int getArraysLeft() {
		return this.dataTracker.get(ARRAYS_LEFT);
	}

	public int getFiringIdx() {
		return this.dataTracker.get(FIRING_IDX);
	}

	/** 服务端每 tick 同步的实时锁定点（准星落点），供渲染器让剩余待发射法阵预瞑转向。 */
	public void setLockPoint(Vec3d p) {
		this.dataTracker.set(LOCK_X, (float) p.x);
		this.dataTracker.set(LOCK_Y, (float) p.y);
		this.dataTracker.set(LOCK_Z, (float) p.z);
	}

	/** 锁定点（未同步时返回 null，渲染器回退朝准星）。 */
	public Vec3d getLockPoint() {
		float x = this.dataTracker.get(LOCK_X);
		float y = this.dataTracker.get(LOCK_Y);
		float z = this.dataTracker.get(LOCK_Z);
		if (x == 0f && y == 0f && z == 0f) return null;
		return new Vec3d(x, y, z);
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(PHASE, 0);
		this.dataTracker.startTracking(PHASE_TICK, 0);
		this.dataTracker.startTracking(OWNER_ID, 0);
		this.dataTracker.startTracking(ENHANCED, false);
		this.dataTracker.startTracking(DIR_X, 0f);
		this.dataTracker.startTracking(DIR_Y, 0f);
		this.dataTracker.startTracking(DIR_Z, 0f);
		this.dataTracker.startTracking(FIRING, false);
		this.dataTracker.startTracking(ARRAYS_LEFT, 3);
		this.dataTracker.startTracking(FIRING_IDX, 0);
		this.dataTracker.startTracking(LOCK_X, 0f);
		this.dataTracker.startTracking(LOCK_Y, 0f);
		this.dataTracker.startTracking(LOCK_Z, 0f);
	}

	// ===== 渲染器读取 =====
	public int getPhaseId() {
		return this.dataTracker.get(PHASE);
	}

	public int getPhaseTick() {
		return this.dataTracker.get(PHASE_TICK);
	}

	public int getTrackedOwnerId() {
		return this.dataTracker.get(OWNER_ID);
	}

	public boolean isEnhanced() {
		return this.dataTracker.get(ENHANCED);
	}

	/** 增强单道发射瞬间定格的世界锁定点（渲染器/伤害据当前法阵位置算方向与长度，实现落点固定的追踪锁定）。 */
	public Vec3d getFireLock() {
		return new Vec3d(this.dataTracker.get(DIR_X), this.dataTracker.get(DIR_Y), this.dataTracker.get(DIR_Z));
	}

	public float enhArrayScale() {
		return ENH_ARRAY_SCALE;
	}

	public float enhBeamRadius() {
		return ENH_BEAM_RADIUS;
	}

	public static double arrayDist() {
		return ARRAY_DIST;
	}

	public double beamLength() {
		return BEAM_LENGTH;
	}

	/** 增强单道光柱达 24 格、碰撞盒仅 0.5，放宽视锥剔除避免离屏被 cull。 */
	@Override
	public boolean shouldRender(double distance) {
		return true;
	}

	@Override
	public Box getVisibilityBoundingBox() {
		// 增强单道光柱最长 24 格、起点在玩家斜后方，扩大可见盒避免视锥剔除导致法阵/光柱整体不渲染
		return this.dataTracker.get(ENHANCED) ? this.getBoundingBox().expand(48.0) : super.getVisibilityBoundingBox();
	}

	@Override
	public void tick() {
		super.tick();
		// 海晶荧光坠增强：持久「待机法阵」——combo 期间跟随玩家、前方显示旋转法阵；发射态额外画光柱 8t；由 Manager combo 结束 discard
		if (this.dataTracker.get(ENHANCED)) {
			if (this.getWorld().isClient) return;
			if (!(this.getWorld() instanceof ServerWorld sw2)) return;
			ServerPlayerEntity ownerE = getOwner(sw2);
			if (ownerE == null || ownerE.isRemoved() || ownerE.isDead() || !FormUtils.isAxolotlFluorescent(ownerE)) {
				this.discard();
				return;
			}
			this.setPosition(ownerE.getX(), ownerE.getEyeY(), ownerE.getZ());   // 跟随玩家
			phaseTicks++;
			this.dataTracker.set(PHASE_TICK, phaseTicks);
			if (this.dataTracker.get(FIRING)) {
				enhFiringTicks--;
				if (enhFiringTicks <= 0) this.dataTracker.set(FIRING, false);
			}
			return;
		}
		if (this.getWorld().isClient) {
			return;   // 渲染由 FluorescentLaserRenderer 负责；粒子由服务端生成
		}
		if (!(this.getWorld() instanceof ServerWorld sw)) return;

		ServerPlayerEntity owner = getOwner(sw);
		if (owner == null || owner.isRemoved() || owner.isDead()
				|| !FormUtils.isAxolotlFluorescent(owner)) {
			cancelNoCd(owner);
			this.discard();
			return;
		}

		// 跟随玩家眼部（法阵在前方由 aim 计算）
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());

		// 定身：每 tick 刷新 ROOTED（仅锁移动，视角自由；释放期视角限速由 mixin 处理）
		owner.addStatusEffect(new StatusEffectInstance(SscAddon.ROOTED, 8, 0, false, false, false));

		Vec3d aim = owner.getRotationVec(1.0f).normalize();
		Vec3d arrayPos = new Vec3d(owner.getX(), owner.getEyeY(), owner.getZ()).add(aim.multiply(ARRAY_DIST));

		phaseTicks++;
		switch (phase) {
			case CHARGE -> tickCharge(sw, owner, aim, arrayPos);
			case RELEASE -> tickRelease(sw, owner, aim, arrayPos);
			case FADE -> tickFade(sw, owner, aim, arrayPos);
		}
		this.dataTracker.set(PHASE_TICK, phaseTicks);
	}

	// ==================== LASER_STATE 去重同步 ====================
	/** 上次已同步给客户端的 laser_state 值（-1 = 尚未同步）；值不变时不重发，阶段内每 tick 重复同步是纯带宽浪费。 */
	private int lastSyncedLaserState = -1;

	private void syncLaserStateDedup(ServerPlayerEntity owner, int value) {
		if (lastSyncedLaserState == value) return;
		lastSyncedLaserState = value;
		PowerUtils.setResourceValueAndSync(owner, LASER_STATE, value);
	}

	// ==================== CHARGE ====================
	private void tickCharge(ServerWorld sw, ServerPlayerEntity owner, Vec3d aim, Vec3d arrayPos) {
		// 净化打断：取消，返还 40% CD（进 60% CD）
		if (owner.hasStatusEffect(SscAddon.PURIFIED)) {
			cancelWithInterruptCd(owner);
			sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.2f);
			this.discard();
			return;
		}
		syncLaserStateDedup(owner, 1);

		// 蓄力音效（volume=3.0：反编译实证客户端 clamp 到 1，近处响度不变不震耳；
		// 可闻半径 16×3=48 格=光柱射程 32 格+16 格富余，OpenAL 距离线性衰减到 48 格归零）
		if (phaseTicks == 1) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.8f);
		}
		if (phaseTicks % 12 == 0) {
			float p = phaseTicks / (float) CHARGE_TICKS;
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 3.0f, 0.8f + p * 0.8f);
		}

		// 四条白线由渲染器绘制（客户端，无粒子残留）；法阵核心发光粒子改为客户端渲染器按视角生成
		// （第一人称不生成、第三人称生成），避免服务端粒子无法区分视角导致第一人称被遮挡
		if (phaseTicks >= CHARGE_TICKS) {
			phase = Phase.RELEASE;
			phaseTicks = 0;
			this.dataTracker.set(PHASE, 1);
			syncLaserStateDedup(owner, 2);
				// 发射音（volume=3.0：48 格可闻=射程+16，距离衰减归零；近处 clamp 到 1 不震耳）
				sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
						SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);
				sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
						SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.7f);
		}
	}

	// ==================== RELEASE ====================
	private void tickRelease(ServerWorld sw, ServerPlayerEntity owner, Vec3d aim, Vec3d arrayPos) {
		syncLaserStateDedup(owner, 2);
		// 螺旋粒子已移到客户端渲染器自绘（逐行照抄未改参数，网络包归零）
		// 伤害：每 4t 一次，5 格直径穿墙圆柱
		if (phaseTicks % DAMAGE_INTERVAL == 0) {
			beamDamage(sw, owner, arrayPos, aim, BEAM_RADIUS);
		}
		if (phaseTicks % 8 == 0) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 3.0f, 1.4f);
		}
		if (phaseTicks >= releaseTicks) {
			phase = Phase.FADE;
			phaseTicks = 0;
			this.dataTracker.set(PHASE, 2);
			syncLaserStateDedup(owner, 3);
		}
	}

	// ==================== FADE ====================
	private void tickFade(ServerWorld sw, ServerPlayerEntity owner, Vec3d aim, Vec3d arrayPos) {
		// 螺旋粒子已移到客户端渲染器自绘（含消退期半径缩小，网络包归零）
		if (phaseTicks >= FADE_TICKS) {
			// 完全消失 → 进 CD、解除定身、清状态
			owner.removeStatusEffect(SscAddon.ROOTED);
			syncLaserStateDedup(owner, 0);
			PowerUtils.setResourceValueAndSync(owner, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS);
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 3.0f, 1.0f);
			this.discard();
		}
	}

	// ==================== 四线粒子 ====================
	// ==================== 光柱螺旋粒子 ====================
        // 已移到客户端 FluorescentLaserRenderer.spawnBeamSpiralClient 自绘（逐行照抄未改参数；
        // phase/phaseTick 已 DataTracker 同步、螺旋角纯时间函数），服务端不再广播——持续粒子网络包归零。方法删除。
	// ==================== 伤害 ====================
	private void beamDamage(ServerWorld sw, ServerPlayerEntity owner, Vec3d arrayPos, Vec3d aim, double radius) {
		double beamLen = beamLength();
		Vec3d end = arrayPos.add(aim.multiply(beamLen));
		Box box = new Box(arrayPos, end).expand(radius);
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(ownerUuid));
		RegistryKey<net.minecraft.entity.damage.DamageType> key =
				RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
		for (LivingEntity t : targets) {
			// 默认白名单豁免
			if (WhitelistUtils.isProtected(owner, t)) continue;
			// 到光柱轴线的距离（圆柱判定，穿墙 → 不检查方块遮挡）
			Vec3d toT = t.getPos().add(0, t.getHeight() * 0.5, 0).subtract(arrayPos);
			double proj = toT.dotProduct(aim);
			if (proj < 0 || proj > beamLen) continue;
			Vec3d closest = arrayPos.add(aim.multiply(proj));
			double distSq = t.getPos().add(0, t.getHeight() * 0.5, 0).squaredDistanceTo(closest);
			if (distSq > radius * radius) continue;
			t.damage(t.getDamageSources().create(key, owner, owner), damage);
		}
	}

	// ==================== 取消/清理 ====================
	private void cancelNoCd(ServerPlayerEntity owner) {
		if (owner != null) {
			owner.removeStatusEffect(SscAddon.ROOTED);
			syncLaserStateDedup(owner, 0);
		}
	}

	/** 被净化打断时取消：返还 40% CD（进 60% CD = 400 × 0.6 = 240t = 12 秒）。 */
	private void cancelWithInterruptCd(ServerPlayerEntity owner) {
		if (owner != null) {
			owner.removeStatusEffect(SscAddon.ROOTED);
			syncLaserStateDedup(owner, 0);
			PowerUtils.setResourceValueAndSync(owner, FormIdentifiers.SP_PRIMARY_CD, (int)(CD_TICKS * 0.6));
		}
	}

	private ServerPlayerEntity getOwner(ServerWorld sw) {
		if (ownerUuid == null) return null;
		var server = sw.getServer();
		return server == null ? null : server.getPlayerManager().getPlayer(ownerUuid);
	}

	/** laser_state 资源 id（0 空闲 /1 蓄力 /2 释放 /3 消退）。 */
	public static final Identifier LASER_STATE =
			new Identifier("my_addon", "form_axolotl_fluorescent_laser_state");

	/** 客户端查询某玩家的活跃激光实体（供视角限速读取 phase/phaseTick）。 */
	public static LaserBeamEntity getActiveForClient(net.minecraft.client.network.ClientPlayerEntity player) {
		net.minecraft.client.world.ClientWorld w = net.minecraft.client.MinecraftClient.getInstance().world;
		if (w == null) return null;
		for (Entity e : w.getEntities()) {
			if (e instanceof LaserBeamEntity laser && !laser.isEnhanced()) {
				if (player != null && player.getId() == laser.getTrackedOwnerId()) return laser;
			}
		}
		return null;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Phase", phase.ordinal());
		nbt.putInt("PhaseTicks", phaseTicks);
		if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		int p = nbt.contains("Phase") ? nbt.getInt("Phase") : 0;
		phase = Phase.values()[MathHelper.clamp(p, 0, Phase.values().length - 1)];
		phaseTicks = nbt.contains("PhaseTicks") ? nbt.getInt("PhaseTicks") : 0;
		if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}
}
