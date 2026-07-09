package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

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

	// ===== 伤害 =====
	private static final int DAMAGE_INTERVAL = 10;      // 每 10t 结算
	private static final float DAMAGE = 20.0f;          // 每次 20 魔法伤害（释放 60t 共 6 次 = 120）

	// ===== 同步数据（供渲染器）=====
	private static final TrackedData<Integer> PHASE = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);       // 0 CHARGE / 1 RELEASE / 2 FADE
	private static final TrackedData<Integer> PHASE_TICK = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);  // 当前阶段已用 tick
	private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> IS_ALING = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private enum Phase { CHARGE, RELEASE, FADE }

	private Phase phase = Phase.CHARGE;
	private int phaseTicks = 0;
	private UUID ownerUuid;
	// 阿澪(axolotl_aling)差异化：伤害/释放时长 +20%，伤害距离由 beamLength() 按 IS_ALING 放大
	private boolean isAling = false;
	private float damage = DAMAGE;
	private int releaseTicks = RELEASE_TICKS;

	private static final DustParticleEffect CYAN =
			new DustParticleEffect(new Vector3f(0.35f, 0.90f, 1.0f), 1.3f);
	private static final DustParticleEffect WHITE =
			new DustParticleEffect(new Vector3f(0.92f, 0.98f, 1.0f), 1.2f);

	public LaserBeamEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public LaserBeamEntity(World world, ServerPlayerEntity owner) {
		super(SscAddon.LASER_BEAM_ENTITY, world);
		this.ownerUuid = owner.getUuid();
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
		// 阿澪：伤害 20->24、释放 60->72t、伤害距离 32->38.4（同步客机渲染）
		this.isAling = FormUtils.isForm(owner, FormIdentifiers.AXOLOTL_ALING);
		this.dataTracker.set(IS_ALING, this.isAling);
		if (this.isAling) {
			this.damage = DAMAGE * 1.2f;
			this.releaseTicks = (int) Math.round(RELEASE_TICKS * 1.2);
		}
		this.noClip = true;
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(PHASE, 0);
		this.dataTracker.startTracking(PHASE_TICK, 0);
		this.dataTracker.startTracking(OWNER_ID, 0);
		this.dataTracker.startTracking(IS_ALING, false);
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

	public static double arrayDist() {
		return ARRAY_DIST;
	}

	public double beamLength() {
		return this.dataTracker.get(IS_ALING) ? BEAM_LENGTH * 1.2 : BEAM_LENGTH;
	}

	public static double beamRadius() {
		return BEAM_RADIUS;
	}

	@Override
	public void tick() {
		super.tick();
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
		PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 1);

		// 蓄力音效
		if (phaseTicks == 1) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		}
		if (phaseTicks % 12 == 0) {
			float p = phaseTicks / (float) CHARGE_TICKS;
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.8f + p * 0.8f);
		}

		// 四条白线由渲染器绘制（客户端，无粒子残留）；法阵核心发光粒子改为客户端渲染器按视角生成
		// （第一人称不生成、第三人称生成），避免服务端粒子无法区分视角导致第一人称被遮挡
		if (phaseTicks >= CHARGE_TICKS) {
			phase = Phase.RELEASE;
			phaseTicks = 0;
			this.dataTracker.set(PHASE, 1);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 2);
			// 发射音
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 1.0f);
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.7f);
		}
	}

	// ==================== RELEASE ====================
	private void tickRelease(ServerWorld sw, ServerPlayerEntity owner, Vec3d aim, Vec3d arrayPos) {
		PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 2);
		// 螺旋粒子沿光柱前进
		spawnBeamSpiral(sw, arrayPos, aim, BEAM_RADIUS);
		// 伤害：每 4t 一次，5 格直径穿墙圆柱
		if (phaseTicks % DAMAGE_INTERVAL == 0) {
			beamDamage(sw, owner, arrayPos, aim, BEAM_RADIUS);
		}
		if (phaseTicks % 8 == 0) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.7f, 1.4f);
		}
		if (phaseTicks >= releaseTicks) {
			phase = Phase.FADE;
			phaseTicks = 0;
			this.dataTracker.set(PHASE, 2);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 3);
		}
	}

	// ==================== FADE ====================
	private void tickFade(ServerWorld sw, ServerPlayerEntity owner, Vec3d aim, Vec3d arrayPos) {
		double shrink = 1.0 - phaseTicks / (double) FADE_TICKS;
		double r = BEAM_RADIUS * Math.max(0.0, shrink);
		spawnBeamSpiral(sw, arrayPos, aim, r);
		if (phaseTicks >= FADE_TICKS) {
			// 完全消失 → 进 CD、解除定身、清状态
			owner.removeStatusEffect(SscAddon.ROOTED);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
			PowerUtils.setResourceValueAndSync(owner, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS);
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.0f);
			this.discard();
		}
	}

	// ==================== 四线粒子 ====================
	// ==================== 光柱螺旋粒子 ====================
	private void spawnBeamSpiral(ServerWorld sw, Vec3d arrayPos, Vec3d aim, double radius) {
		if (radius <= 0.01) return;
		Vec3d right = aim.crossProduct(new Vec3d(0, 1, 0));
		if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
		right = right.normalize();
		Vec3d up = right.crossProduct(aim).normalize();
		double beamLen = beamLength();
		int steps = (int) (beamLen / 0.8);
		double baseAng = phaseTicks * 0.6;
		for (int s = 0; s <= steps; s++) {
			double d = beamLen * s / steps;
			// 双螺旋（白 + 青，相位差 π）
			double ang1 = baseAng + d * 0.9;
			double ang2 = ang1 + Math.PI;
			Vec3d axis = arrayPos.add(aim.multiply(d));
			Vec3d o1 = right.multiply(Math.cos(ang1) * radius).add(up.multiply(Math.sin(ang1) * radius));
			Vec3d o2 = right.multiply(Math.cos(ang2) * radius).add(up.multiply(Math.sin(ang2) * radius));
			if (s % 2 == 0) {
				Vec3d p1 = axis.add(o1);
				sw.spawnParticles(WHITE, p1.x, p1.y, p1.z, 1, 0, 0, 0, 0.0);
				Vec3d p2 = axis.add(o2);
				sw.spawnParticles(CYAN, p2.x, p2.y, p2.z, 1, 0, 0, 0, 0.0);
			}
			// 芯部发光
			if (s % 3 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, axis.x, axis.y, axis.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

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
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
		}
	}

	/** 被净化打断时取消：返还 40% CD（进 60% CD = 400 × 0.6 = 240t = 12 秒）。 */
	private void cancelWithInterruptCd(ServerPlayerEntity owner) {
		if (owner != null) {
			owner.removeStatusEffect(SscAddon.ROOTED);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
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
			if (e instanceof LaserBeamEntity laser) {
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
