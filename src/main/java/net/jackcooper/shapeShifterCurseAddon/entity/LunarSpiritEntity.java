package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/**
 * 月灵（Lunar Spirit）——召唤系法术的协战飞行生物（jackcooper）。
 *
 * <p><b>贴身护卫</b>：始终悬浮在主人 3 格球内（理想半径 2.2，主人速度前馈 + 连续转向，
 * 追不上则瞬移兜底）；浮空灵体不受坠落影响；12 格内按优先级索敌
 * （主人亲手攻击过的 &gt; 攻击过主人/月灵的 &gt; 敌对生物），远程发射曲线光弹
 * （见 {@link LunarSpiritBoltEntity}）；对敌时站在主人背离敌人的一侧、只在必要时向射程内挪动，
 * 绝不贴近敌人；白名单友方不打不伤。</p>
 *
 * <p><b>三色变体</b>（DataTracker VARIANT 同步，多人一致）：0 粉×1.5 伤+点燃 3s /
 * 1 蓝×0.7 伤+缓速 I 3s / 2 绿正常伤+中毒 I 3s。寿命到期粒子消散。</p>
 */
public class LunarSpiritEntity extends PathAwareEntity implements GeoEntity {

	/** GeoEntity 动画缓存（GeckoLib 标准）。 */
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	/** 变体 id：0=粉红（火）、1=青蓝（冰）、2=绿（毒）。 */
	public static final int VARIANT_PINK = 0;
	public static final int VARIANT_CYAN = 1;
	public static final int VARIANT_GREEN = 2;

	/** 变体 DataTracker（同步客户端渲染配色/贴图）。 */
	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(LunarSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);

	// ---- 目标优先级（数值越小越优先，高优先级目标不会被低优先级候选顶掉）----
	/** 主人亲手攻击过的目标（「你打谁月灵就打谁」）。 */
	public static final int TIER_OWNER_ATTACKED = 1;
	/** 攻击过主人或月灵的目标（护主 / 反击）。 */
	public static final int TIER_DEFENSE = 2;
	/** 自动索敌到的敌对生物。 */
	public static final int TIER_SCAN = 3;

	// ---- 伴随 / 移动参数 ----
	/** 理想伴随半径（格），留出余量保证不越过 3 格硬上限。 */
	private static final double FOLLOW_RADIUS = 2.2;
	/** 伴随硬上限（格）：超出即进入加速追赶，并开始计时瞬移兜底。 */
	private static final double LEASH_RADIUS = 3.0;
	/** 与主人距离超过该值立即瞬移回身边（传送 / 鞘翅 / 隔墙）。 */
	private static final double TELEPORT_DISTANCE = 6.0;
	/** 在硬上限外持续该 tick 数（1.5s）仍未追回 → 瞬移。 */
	private static final int LEASH_TELEPORT_TICKS = 30;
	/** 悬浮高度（主人脚底以上，格）。 */
	private static final double HOVER_HEIGHT = 1.4;
	/** 常态最高速（格/tick ≈ 9 m/s，高于疾跑跳 7 m/s）。 */
	private static final double MAX_SPEED = 0.45;
	/** 出圈追赶时最高速（格/tick ≈ 18 m/s）。 */
	private static final double MAX_SPEED_CATCHUP = 0.9;
	/** 朝理想点的转向增益（每格偏差换算的期望速度）。 */
	private static final double STEER_GAIN = 0.28;

	// ---- 战斗参数 ----
	/** 攻击 / 索敌射程（格）。 */
	private static final double ATTACK_RANGE = 12.0;
	/** 目标脱离该距离（留 2 格滞回）即放弃。 */
	private static final double DROP_RANGE = 14.0;
	/** 对敌期望距离：射程留 1.5 格余量，尽量站远。 */
	private static final double PREFERRED_ENEMY_DISTANCE = ATTACK_RANGE - 1.5;
	/** 光弹发射间隔（tick）。 */
	private static final int SHOOT_INTERVAL = 40;
	/** 连续无视线该 tick 数即放弃目标重新索敌。 */
	private static final int LOS_LOSE_TICKS = 60;
	/** 无目标时索敌间隔（tick）。 */
	private static final int SCAN_INTERVAL = 10;
	/** 主人「最近攻击过」记录的有效窗口（tick）。 */
	private static final int OWNER_ATTACK_MEMORY = 200;

	/** 默认寿命（tick）：30s（施法时按等级 +10s/级覆盖）。 */
	private int lifeTicks = 600;
	/** 攻击目标 UUID。 */
	private UUID targetUuid;
	/** 当前目标的优先级层级。 */
	private int targetTier = TIER_SCAN;
	/** 当前目标连续无视线 tick 数。 */
	private int noLosTicks;
	/** 本 tick 对目标是否有视线（validateTarget 计算，开火复用，避免二次射线）。 */
	private boolean targetVisible;
	/** 索敌间隔计时。 */
	private int scanCooldown;
	/** 光弹发射冷却（出生 1s 后首开火）。 */
	private int shootCooldown = 20;
	/** 处于 3 格硬上限外的连续 tick 数。 */
	private int outOfLeashTicks;
	/** 主人上一 tick 位置（服务端玩家 velocity 不可靠，用位移差自算）。 */
	private Vec3d lastOwnerPos;
	/** 平滑后的主人速度（前馈项，跟跑不掉队）。 */
	private Vec3d ownerVelocity = Vec3d.ZERO;
	/** 平滑后的主人朝向（决定待机时「肩后」位置，防鼠标抖动带着月灵乱晃）。 */
	private float smoothedOwnerYaw;
	private boolean ownerYawInit;
	/** 编队槽位：决定相对主人背后的左右偏角（多只不重叠），施法时按序号设置。 */
	private int formationSlot;
	/** 上下浮动 / 摆动相位（每只随机，多只不同步）。 */
	private final double bobPhase = this.random.nextDouble() * Math.PI * 2;

	/**
	 * 发射曲线光弹：朝目标方向随机偏 ±45° 射出，飞行中逐 tick 转向目标形成弧线
	 * （奥里的守卫风格）。伤害/debuff 在光弹命中时结算。
	 */
	public void shootBoltAt(LivingEntity target) {
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		LunarSpiritBoltEntity bolt = new LunarSpiritBoltEntity(this.getWorld(), this);
		bolt.setVariant(getVariant());
		bolt.setTargetEntity(target);
		// 发射方向：朝目标方向随机水平偏 ±45°（曲线起点，光弹飞行中自己收拢）
		Vec3d toTarget = target.getPos().add(0, target.getHeight() * 0.6, 0)
				.subtract(this.getX(), this.getBodyY(0.6), this.getZ());
		double yaw = Math.atan2(toTarget.z, toTarget.x);
		double yawOffset = (this.random.nextDouble() * 2.0 - 1.0) * Math.toRadians(45.0);
		double distXZ = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		double pitch = Math.atan2(toTarget.y, distXZ);
		double dirX = Math.cos(yaw + yawOffset) * Math.cos(pitch);
		double dirZ = Math.sin(yaw + yawOffset) * Math.cos(pitch);
		Vec3d dir = new Vec3d(dirX, Math.sin(pitch), dirZ).normalize();
		bolt.setPosition(this.getX(), this.getBodyY(0.6), this.getZ());
		bolt.setVelocity(dir.x * 0.7, dir.y * 0.7, dir.z * 0.7);
		serverWorld.spawnEntity(bolt);
		// 发射演出：光弹凝聚 + 轻音效（自己听清瞭不吵）
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				this.getX(), this.getBodyY(0.6), this.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4f, 1.8f);
	}

	public LunarSpiritEntity(EntityType<? extends LunarSpiritEntity> entityType, World world) {
		super(entityType, world);
		this.setPersistent();
		this.setNoGravity(true);
		this.experiencePoints = 0;
		this.formationSlot = this.random.nextInt(2);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(VARIANT, VARIANT_GREEN);
	}

	/** 变体 id（0 粉/1 蓝/2 绿）。 */
	public int getVariant() {
		return this.dataTracker.get(VARIANT);
	}

	/** 设置变体（服务端施法时调用，DataTracker 自动同步客户端）。 */
	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, Math.max(VARIANT_PINK, Math.min(VARIANT_GREEN, variant)));
	}

	/** 编队槽位（0 起，施法按召唤序号设置）：左右交替、逐级外扩，多只月灵不重叠。 */
	public void setFormationSlot(int slot) {
		this.formationSlot = Math.max(0, slot);
	}

	public static DefaultAttributeContainer.Builder createLunarSpiritAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 3.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.4)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		// AI 全部在 tick 里连续驱动（平滑飞行），不用 goal+moveControl（会到点急停一顿一顿）
	}

	// ---- 主人 ----

	/** 主人玩家 UUID（施法者）。 */
	private UUID ownerUuid;

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public void setOwnerUuid(UUID uuid) {
		this.ownerUuid = uuid;
		// 召唤物归属标签：纳入白名单系统的「玩家召唤物」保护（与堕落悦灵恕魔的 owner 标签同机制）
		this.getCommandTags().removeIf(tag -> tag.startsWith("ssc_owner:"));
		if (uuid != null) {
			this.addCommandTag("ssc_owner:" + uuid);
		}
	}

	public PlayerEntity getOwnerPlayer() {
		if (this.ownerUuid == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return null;
		}
		var entity = serverWorld.getEntity(this.ownerUuid);
		return entity instanceof PlayerEntity player && player.isAlive() ? player : null;
	}

	// ---- 寿命 ----

	public void setLifeTicks(int ticks) {
		this.lifeTicks = ticks;
	}

	// ---- 攻击目标 ----

	/** 主人亲手攻击的目标：最高优先级，直接接管当前目标（null 清空）。 */
	public void setAttackTarget(LivingEntity target) {
		if (target == null) {
			clearTarget();
			return;
		}
		offerTarget(target, TIER_OWNER_ATTACKED);
	}

	/**
	 * 提交候选目标（服务端）：通过合法性校验，且优先级不低于当前目标（tier 更小或相等）才接受。
	 *
	 * @return 是否接受
	 */
	public boolean offerTarget(LivingEntity candidate, int tier) {
		if (!(this.getWorld() instanceof ServerWorld)) {
			return false;
		}
		PlayerEntity owner = getOwnerPlayer();
		if (owner == null || !isValidCandidate(candidate, owner, DROP_RANGE)) {
			return false;
		}
		LivingEntity current = getAttackTargetEntity();
		if (current != null && current != candidate && tier > this.targetTier) {
			return false;
		}
		this.targetTier = current == candidate ? Math.min(this.targetTier, tier) : tier;
		this.targetUuid = candidate.getUuid();
		this.noLosTicks = 0;
		return true;
	}

	public LivingEntity getAttackTargetEntity() {
		if (this.targetUuid == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
			return null;
		}
		var entity = serverWorld.getEntity(this.targetUuid);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private void clearTarget() {
		this.targetUuid = null;
		this.targetTier = TIER_SCAN;
		this.noLosTicks = 0;
		this.targetVisible = false;
	}

	/** 候选合法性：存活、非主人 / 同主人月灵 / 盔甲架、非创造或旁观玩家、在 range 内、不受白名单保护。 */
	private boolean isValidCandidate(LivingEntity candidate, PlayerEntity owner, double range) {
		if (candidate == null || candidate == this || candidate == owner || !candidate.isAlive() || candidate.isRemoved()) {
			return false;
		}
		if (candidate instanceof ArmorStandEntity) {
			return false;
		}
		if (candidate instanceof LunarSpiritEntity spirit
				&& this.ownerUuid != null && this.ownerUuid.equals(spirit.getOwnerUuid())) {
			return false;
		}
		if (candidate instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		if (this.squaredDistanceTo(candidate) > range * range) {
			return false;
		}
		return !(owner instanceof ServerPlayerEntity ownerSp) || !WhitelistUtils.isProtected(ownerSp, candidate);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
			return; // 客户端不跑 AI，位置 / 朝向 / 变体全由服务端同步
		}
		// 寿命倒计时（仅服务端权威）
		if (--this.lifeTicks <= 0) {
			this.expire(serverWorld);
			return;
		}
		// 主人断线 / 死亡 / 不在本维度 → 提前消散
		PlayerEntity owner = getOwnerPlayer();
		if (owner == null) {
			this.expire(serverWorld);
			return;
		}
		serverTickAI(serverWorld, owner);
		// 月辉粒子拖尾（服务端撒，天然多人同步）
		if (this.age % 4 == 0) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.15, 0.15, 0.15, 0.005);
		}
	}

	/**
	 * 服务端 AI（每 tick）：①记录主人位移 / 朝向（前馈与待机站位用）②校验并维持目标，无目标时每 10t 索敌
	 * ③算理想悬浮点 → 出圈过久或过远则瞬移，否则连续转向飞行 ④面向目标 / 飞行方向 ⑤射程内有视线则发射光弹。
	 */
	private void serverTickAI(ServerWorld serverWorld, PlayerEntity owner) {
		trackOwnerMotion(owner);

		LivingEntity target = validateTarget(owner);
		if (target == null && --this.scanCooldown <= 0) {
			this.scanCooldown = SCAN_INTERVAL;
			target = scanForTarget(serverWorld, owner);
		}
		this.setTarget(target); // 同步原版目标字段，供其它系统读取

		Vec3d idealPos = computeIdealPos(owner, target);
		double distToOwner = this.getPos().add(0, this.getHeight() * 0.5, 0)
				.distanceTo(owner.getPos().add(0, owner.getHeight() * 0.5, 0));
		if (distToOwner > TELEPORT_DISTANCE || this.outOfLeashTicks >= LEASH_TELEPORT_TICKS) {
			teleportNearOwner(serverWorld, owner, idealPos);
			return;
		}
		this.outOfLeashTicks = distToOwner > LEASH_RADIUS ? this.outOfLeashTicks + 1 : 0;

		steerTowards(idealPos, distToOwner > LEASH_RADIUS ? MAX_SPEED_CATCHUP : MAX_SPEED);
		faceTowards(target);

		if (this.shootCooldown > 0) {
			this.shootCooldown--;
		}
		if (target != null && this.shootCooldown <= 0 && this.targetVisible
				&& this.squaredDistanceTo(target) <= ATTACK_RANGE * ATTACK_RANGE) {
			this.shootCooldown = SHOOT_INTERVAL;
			shootBoltAt(target);
		}
	}

	/** 记录主人位移（速度前馈）与朝向（平滑）。服务端玩家 getVelocity 不随移动包更新，必须用位移差。 */
	private void trackOwnerMotion(PlayerEntity owner) {
		Vec3d pos = owner.getPos();
		if (this.lastOwnerPos == null) {
			this.lastOwnerPos = pos;
		}
		Vec3d delta = pos.subtract(this.lastOwnerPos);
		this.lastOwnerPos = pos;
		if (delta.lengthSquared() > 9.0) {
			// 单 tick 位移 >3 格视为传送，不进前馈（否则会把月灵甩飞）
			delta = Vec3d.ZERO;
			this.ownerVelocity = Vec3d.ZERO;
		}
		this.ownerVelocity = this.ownerVelocity.multiply(0.6).add(delta.multiply(0.4));
		float yaw = owner.getYaw();
		this.smoothedOwnerYaw = this.ownerYawInit
				? MathHelper.lerpAngleDegrees(0.08f, this.smoothedOwnerYaw, yaw) : yaw;
		this.ownerYawInit = true;
	}

	/** 校验当前目标：死亡 / 超出 14 格 / 变为受保护或创造 → 放弃；连续 3s 无视线 → 放弃。同时刷新本 tick 视线状态。 */
	private LivingEntity validateTarget(PlayerEntity owner) {
		LivingEntity target = getAttackTargetEntity();
		if (target == null || !isValidCandidate(target, owner, DROP_RANGE)) {
			clearTarget();
			return null;
		}
		this.targetVisible = this.canSee(target);
		if (this.targetVisible) {
			this.noLosTicks = 0;
		} else if (++this.noLosTicks > LOS_LOSE_TICKS) {
			clearTarget();
			return null;
		}
		return target;
	}

	/**
	 * 索敌（12 格内、需有视线，优先级从高到低）：
	 * ①主人最近亲手攻击过的（原版 attacking 记录，200t 内）②最近攻击过主人的（原版 attacker 记录）
	 * ③最近的敌对生物（Monster 系 / 正在瞄准主人或月灵的 / 对主人愤怒的中立生物）。被动生物不主动招惹。
	 */
	private LivingEntity scanForTarget(ServerWorld serverWorld, PlayerEntity owner) {
		LivingEntity attacking = owner.getAttacking();
		if (attacking != null && owner.age - owner.getLastAttackTime() <= OWNER_ATTACK_MEMORY
				&& isValidCandidate(attacking, owner, ATTACK_RANGE) && this.canSee(attacking)) {
			return acquireTarget(attacking, TIER_OWNER_ATTACKED);
		}
		LivingEntity attacker = owner.getAttacker();
		if (attacker != null && isValidCandidate(attacker, owner, ATTACK_RANGE) && this.canSee(attacker)) {
			return acquireTarget(attacker, TIER_DEFENSE);
		}
		List<LivingEntity> candidates = serverWorld.getEntitiesByClass(LivingEntity.class,
				this.getBoundingBox().expand(ATTACK_RANGE),
				e -> e != this && e != owner && e.isAlive() && !(e instanceof ArmorStandEntity));
		LivingEntity best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distSq = this.squaredDistanceTo(candidate);
			if (distSq >= bestDistSq || !isHostileTo(candidate, owner)
					|| !isValidCandidate(candidate, owner, ATTACK_RANGE) || !this.canSee(candidate)) {
				continue;
			}
			best = candidate;
			bestDistSq = distSq;
		}
		return best == null ? null : acquireTarget(best, TIER_SCAN);
	}

	private LivingEntity acquireTarget(LivingEntity target, int tier) {
		this.targetUuid = target.getUuid();
		this.targetTier = tier;
		this.noLosTicks = 0;
		this.targetVisible = true;
		return target;
	}

	/** 敌对判定：Monster 系、正在瞄准主人 / 月灵的生物、对主人愤怒的中立生物（蜂 / 野狼 / 末影人等）。 */
	private boolean isHostileTo(LivingEntity candidate, PlayerEntity owner) {
		if (candidate instanceof Monster) {
			return true;
		}
		if (candidate instanceof MobEntity mob && (mob.getTarget() == owner || mob.getTarget() == this)) {
			return true;
		}
		return candidate instanceof Angerable angerable && owner.getUuid().equals(angerable.getAngryAt());
	}

	/**
	 * 理想悬浮点（主人脚底 +1.4 格并上下浮动，水平在半径 2.2 的圈上）：
	 * 无目标 → 主人（平滑朝向）背后肩侧 + 轻微摆动，不挡视野；
	 * 有目标 → 圈上背离敌人的一侧（离敌最远），仅当该点超出期望射程 10.5 格时，
	 * 用余弦定理沿圈向敌方挪到恰好 10.5 格处，绝不主动贴近敌人。槽位偏角让多只月灵左右分列。
	 */
	private Vec3d computeIdealPos(PlayerEntity owner, LivingEntity target) {
		Vec3d center = owner.getPos().add(0, HOVER_HEIGHT + Math.sin(this.age * 0.06 + this.bobPhase) * 0.2, 0);
		double slotAngle = slotAngle();
		double yawRad = Math.toRadians(this.smoothedOwnerYaw);
		Vec3d behind = new Vec3d(Math.sin(yawRad), 0, -Math.cos(yawRad));
		if (target == null) {
			double sway = Math.sin(this.age * 0.02 + this.bobPhase) * 0.25;
			return center.add(behind.rotateY((float) (slotAngle + sway)).multiply(FOLLOW_RADIUS));
		}
		Vec3d away = new Vec3d(owner.getX() - target.getX(), 0, owner.getZ() - target.getZ());
		double d = away.length();
		if (d < 1.0e-3) {
			away = behind;
			d = 1.0e-3;
		} else {
			away = away.multiply(1.0 / d);
		}
		double wanted = Math.min(d + FOLLOW_RADIUS, PREFERRED_ENEMY_DISTANCE);
		double cosTheta = MathHelper.clamp(
				(wanted * wanted - d * d - FOLLOW_RADIUS * FOLLOW_RADIUS) / (2.0 * d * FOLLOW_RADIUS), -1.0, 1.0);
		double theta = MathHelper.clamp(Math.acos(cosTheta) + Math.abs(slotAngle) * 0.5, 0.0, Math.PI)
				* Math.signum(slotAngle);
		return center.add(away.rotateY((float) theta).multiply(FOLLOW_RADIUS));
	}

	/** 槽位偏角（弧度）：0→+0.75、1→-0.75、2→+1.3、3→-1.3…（左右交替、逐级外扩）。 */
	private double slotAngle() {
		double magnitude = 0.75 + 0.55 * (this.formationSlot / 2);
		return this.formationSlot % 2 == 0 ? magnitude : -magnitude;
	}

	/** 连续转向飞行：期望速度 = 主人速度前馈 + 朝理想点的限幅修正，再对当前速度插值（无急停、无硬设方向）。 */
	private void steerTowards(Vec3d idealPos, double maxSpeed) {
		Vec3d correction = idealPos.subtract(this.getPos()).multiply(STEER_GAIN);
		double len = correction.length();
		if (len > maxSpeed) {
			correction = correction.multiply(maxSpeed / len);
		}
		Vec3d desired = this.ownerVelocity.add(correction);
		this.setVelocity(this.getVelocity().multiply(0.7).add(desired.multiply(0.3)));
	}

	/** 朝向：有目标面向目标；无目标时快速移动面向飞行方向，悬停则与主人同向。 */
	private void faceTowards(LivingEntity target) {
		Vec3d dir;
		if (target != null) {
			dir = target.getPos().add(0, target.getHeight() * 0.5, 0)
					.subtract(this.getPos().add(0, this.getHeight() * 0.5, 0));
		} else if (this.getVelocity().horizontalLengthSquared() > 0.04) {
			dir = this.getVelocity();
		} else {
			this.setYaw(this.smoothedOwnerYaw);
			this.setPitch(0.0f);
			this.bodyYaw = this.smoothedOwnerYaw;
			this.headYaw = this.smoothedOwnerYaw;
			return;
		}
		double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
		float pitch = (float) -(MathHelper.atan2(dir.y, horizontal) * (180.0 / Math.PI));
		this.setYaw(yaw);
		this.setPitch(pitch);
		this.bodyYaw = yaw;
		this.headYaw = yaw;
	}

	/** 瞬移兜底：依次尝试理想点 / 主人头顶 / 主人身侧 / 主人脚下，取第一个无方块碰撞的落点，避免卡墙窒息。 */
	private void teleportNearOwner(ServerWorld serverWorld, PlayerEntity owner, Vec3d idealPos) {
		Vec3d[] candidates = {
				idealPos,
				owner.getPos().add(0, HOVER_HEIGHT, 0),
				owner.getPos().add(0, 0.5, 0),
				owner.getPos()
		};
		Vec3d dest = candidates[candidates.length - 1];
		for (Vec3d candidate : candidates) {
			Box box = this.getDimensions(this.getPose()).getBoxAt(candidate);
			if (serverWorld.isSpaceEmpty(this, box)) {
				dest = candidate;
				break;
			}
		}
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				this.getX(), this.getBodyY(0.5), this.getZ(), 8, 0.2, 0.2, 0.2, 0.03);
		this.refreshPositionAndAngles(dest.x, dest.y, dest.z, this.getYaw(), this.getPitch());
		this.setVelocity(Vec3d.ZERO);
		this.fallDistance = 0.0f;
		this.outOfLeashTicks = 0;
		this.ownerVelocity = Vec3d.ZERO;
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				dest.x, dest.y + this.getHeight() * 0.5, dest.z, 8, 0.2, 0.2, 0.2, 0.03);
		serverWorld.playSound(null, dest.x, dest.y, dest.z,
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.3f, 1.6f);
	}

	/** 到期/主人离线消散：粒子演出 + 移除（不掉落不掉经验）。 */
	private void expire(ServerWorld serverWorld) {
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				this.getX(), this.getBodyY(0.5), this.getZ(), 16, 0.4, 0.4, 0.4, 0.05);
		serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.8f);
		this.discard();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// 同主人的月灵之间互相免伤
		if (source.getAttacker() instanceof LunarSpiritEntity spirit
				&& spirit.getOwnerUuid() != null && spirit.getOwnerUuid().equals(this.ownerUuid)) {
			return false;
		}
		// 反击：谁打我 → 以护主优先级接管目标（主人误击不算）
		if (!this.getWorld().isClient && source.getAttacker() instanceof LivingEntity attacker
				&& attacker != this) {
			PlayerEntity owner = getOwnerPlayer();
			if (owner == null || attacker != owner) {
				offerTarget(attacker, TIER_DEFENSE);
			}
		}
		return super.damage(source, amount);
	}

	// ---- 浮空灵体：不累计坠落距离、不触发落地效果、不受坠落伤害（同原版悦灵 / 蜜蜂） ----

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void pushAway(Entity entity) {
		// 不推挤主人与其它生物
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		// 有寿命的召唤物不自然消失
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	// ========== GeoEntity 实现：三段动画（geo 模型 my_addon/moon_spirit） ==========

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 4, state -> {
			// 召唤出生展开（1.2s 一次性）：age < 24 tick 内优先播放
			if (this.age < 24) {
				return state.setAndContinue(RawAnimation.begin()
						.thenPlay("animation.moon_spirit.summon"));
			}
			// 移动中 → 飞行振翅；悬停 → 待机漂浮
			if (state.isMoving()) {
				return state.setAndContinue(RawAnimation.begin()
						.thenLoop("animation.moon_spirit.fly"));
			}
			return state.setAndContinue(RawAnimation.begin()
					.thenLoop("animation.moon_spirit.idle"));
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	// ---- NBT 持久化 ----

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.ownerUuid != null) {
			nbt.putUuid("Owner", this.ownerUuid);
		}
		if (this.targetUuid != null) {
			nbt.putUuid("AttackTarget", this.targetUuid);
			nbt.putInt("TargetTier", this.targetTier);
		}
		nbt.putInt("LifeTicks", this.lifeTicks);
		nbt.putInt("Variant", getVariant());
		nbt.putInt("FormationSlot", this.formationSlot);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("Owner")) {
			this.ownerUuid = nbt.getUuid("Owner");
		}
		if (nbt.containsUuid("AttackTarget")) {
			this.targetUuid = nbt.getUuid("AttackTarget");
			this.targetTier = nbt.contains("TargetTier") ? nbt.getInt("TargetTier") : TIER_SCAN;
		}
		this.lifeTicks = nbt.getInt("LifeTicks");
		if (this.lifeTicks <= 0) {
			this.lifeTicks = 100; // 兜底：NBT 缺失时给 5s 缓冲消散
		}
		if (nbt.contains("Variant")) {
			setVariant(nbt.getInt("Variant"));
		}
		if (nbt.contains("FormationSlot")) {
			setFormationSlot(nbt.getInt("FormationSlot"));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		// 死亡也走消散演出（不掉战利品——装备/掉落都为空）
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					this.getX(), this.getBodyY(0.5), this.getZ(), 12, 0.3, 0.3, 0.3, 0.04);
		}
	}

	@Override
	public void handleStatus(byte status) {
		super.handleStatus(status);
		if (status == 7) { // 治愈粒子（与悦灵一致的表现）
			this.getWorld().addParticle(ParticleTypes.HEART,
					this.getX() + (this.random.nextDouble() - 0.5), this.getY() + this.getHeight(),
					this.getZ() + (this.random.nextDouble() - 0.5), 0, 0.05, 0);
		}
	}
}
