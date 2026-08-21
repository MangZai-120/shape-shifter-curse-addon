package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager;

import java.util.Optional;
import java.util.UUID;

/**
 * 寒棘狐「冰刺」冰锥实体：一个实体承担两种状态——
 * <ul>
 *   <li><b>HOVER（环绕态）</b>：蓄力凝聚后环绕玩家斜上方漂浮，位置由 {@code FrostSpikeManager} 每 tick 设置；
 *       各自独立计时，最多存在 {@value #MAX_HOVER_TICKS} tick（60 秒）后自行消失；材质随存在时间分 3 阶段。</li>
 *   <li><b>FLY（飞行态）</b>：发射后以 {@value #SPEED} 格/tick 直线飞行，超过 {@value #STRAIGHT_DIST} 格后逐 tick 下坠；
 *       直击造成 {@value #DAMAGE} 点物理伤害（默认白名单豁免），撞方块 / 超时 5 秒自毁。</li>
 * </ul>
 * 无论何种状态，主人被 SP 悦灵净化（{@link SscAddon#PURIFIED}）时立即碎裂。
 * 全部判定在服务端，实体走原版 EntityTracker 天然多人同步；飞行速度用 DataTracker 全精度同步避免生成包截断失真。
 */
public class FrostThornEntity extends ProjectileEntity {

	public static final int STATE_HOVER = 0;
	public static final int STATE_FLY = 1;

	private static final TrackedData<Integer> STATE = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> STAGE = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.INTEGER);
	// 环绕 slot 索引 + 主人 UUID：供客户端 HOVER 态按本地玩家自算贴合位置（平滑跟手，不依赖网络包跳位）
	private static final TrackedData<Integer> SLOT = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Float> VEL_X = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> VEL_Y = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> VEL_Z = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.FLOAT);
	// 强化等级（凝棘次技能）：0=普通冰刺；>=1=强化直飞冰锥（消耗 N 个环绕冰锥凝成），同步客户端用于渲染放大
	private static final TrackedData<Integer> LEVEL = DataTracker.registerData(FrostThornEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final double SPEED = 0.8;          // 16 格/秒
	private static final double STRAIGHT_DIST = 16.0; // 16 格内直线，之后下坠
	private static final double GRAVITY = 0.03;       // 16 格后每 tick 竖直衰减
	private static final int MAX_FLY_TICKS = 100;     // 5 秒超时自毁
	private static final int MAX_HOVER_TICKS = 1200;  // 60 秒存在时间
	private static final double MAX_FLY_DIST = 128.0; // 最远飞行 128 格
	private static final float DAMAGE = 8.0f;
	private static final double CONVERGE = 0.6;       // 向准星射线靠拢的弯曲强度（越大越急弯）
	private static final double CONVERGE_DONE = 0.35; // 靠拢完成阈值（格），汇入射线后转直飞
	// ===== 凝棘强化直飞（次技能）=====
	private static final int ENHANCED_MAX_FLY_TICKS = 200;  // 10 秒后自动消失（无距离销毁、无下坠）
	private static final float ENHANCED_BASE_DAMAGE = 8.0f; // 每消耗一个冰锥 +100%：伤害 = 8×(1+level)
	// 强化冰锥专用并线参数：并线路程 ≈ 初始偏差/弯曲强度（与速度无关）。大冰锥从头顶出发偏差约 0.5~1 格，
	// 用低弯曲强度把弧线拉长到约 9 格（偏转角仅 ~3.4°，平缓滑入准星线，用距离换视觉效果，防近处猛拐）；
	// 阈值收紧到 0.1 格——主技能 0.35 格残差对小冰锥看不出来，大冰锥会肉眼可见「没在射线上」
	private static final double ENHANCED_CONVERGE = 0.06;
	private static final double ENHANCED_CONVERGE_DONE = 0.1;
	private static final int FLIGHT_SOUND_INTERVAL = 7;  // 飞行中每 7 tick 播一次高速划破空气音（连续呼啸）

	// ===== 环绕几何（锺点参照雪狐 FeralBody 胸部方块：背部竖直面 150° 开屏，服务端 / 客户端共用一套计算） =====
	private static final double HOVER_RADIUS = 0.7;
	private static final double HOVER_HEIGHT = 0.45;
	private static final double BACK_DIST = 0.25;    // 扇形圆心沿背后方向的偏移
	/** 冰锥与身体朝向平行，尖朝头部所指方向，水平/垂直最多各沿这条平行线偏转此角度。 */
	private static final float MAX_THORN_TURN = 45f;
	/** 5 个 slot 的仰角（度）：上→左→右→左上→右上；扇形以正上为中心对称跨 150°（间隔 37.5°）。 */
	private static final double[] SLOT_ELEV = {90, 15, 15, 52.5, 52.5};
	/** 5 个 slot 的左右符号（沿玩家左手方向为正）：上=0、左=+1、右=-1、左上=+1、右上=-1。 */
	private static final int[] SLOT_SIDE = {0, 1, -1, 1, -1};

	private int hoverTicks = 0;
	private int flyTicks = 0;
	private Vec3d flyStart;
	private Vec3d rayOrigin;              // 发射瞬间的准星射线起点（玩家眼睛）
	private Vec3d rayDir;                 // 发射瞬间的准星射线方向（归一化）
	private boolean converging = false;   // 是否处于向准星射线靠拢阶段
	private double enhancedSpeed = SPEED; // 凝棘强化冰锥飞行速度（converging 靠拢/直飞共用）

	public FrostThornEntity(EntityType<? extends FrostThornEntity> type, World world) {
		super(type, world);
	}

	public FrostThornEntity(World world, PlayerEntity owner) {
		super(SscAddon.FROST_THORN_ENTITY, world);
		this.setOwner(owner);
		this.dataTracker.set(OWNER_UUID, Optional.of(owner.getUuid()));
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(STATE, STATE_HOVER);
		this.dataTracker.startTracking(STAGE, 0);
		this.dataTracker.startTracking(SLOT, 0);
		this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
		this.dataTracker.startTracking(VEL_X, 0.0f);
		this.dataTracker.startTracking(VEL_Y, 0.0f);
		this.dataTracker.startTracking(VEL_Z, 0.0f);
		this.dataTracker.startTracking(LEVEL, 0);
	}

	public int getState() { return this.dataTracker.get(STATE); }
	public int getStage() { return this.dataTracker.get(STAGE); }
	/** 强化等级：0=普通冰刺，>=1=凝棘强化冰锥（渲染放大 + 命中伤害用）。 */
	public int getLevel() { return this.dataTracker.get(LEVEL); }
	public int getHoverTicks() { return hoverTicks; }
	public boolean isHover() { return getState() == STATE_HOVER; }
	public void setSlot(int slot) { this.dataTracker.set(SLOT, slot); }
	public int getSlot() { return this.dataTracker.get(SLOT); }
	/** 主人 UUID（DataTracker，重载后由 NBT 恢复）——客户端跟随 / 服务端自认领都用它找回主人。 */
	public Optional<UUID> getOwnerUuid() { return this.dataTracker.get(OWNER_UUID); }
	/** 重进重建时恢复已存在 tick（阶段材质/剩余寿命延续退出前状态）。 */
	public void restoreHoverTicks(int ticks) {
		this.hoverTicks = Math.max(0, ticks);
		updateStage();
	}

	/** 环绕目标位置（服务端 / 客户端共用同一套几何）：背部竖直面 150° 开屏。 */
	public static Vec3d hoverTarget(LivingEntity owner, int slot) {
		float bodyYaw = owner.bodyYaw;
		double bodyRad = Math.toRadians(bodyYaw);
		// MC 水平朝向 facing=(-sin,0,cos)，背后=(sin,0,-cos)；左侧方向 left=(cos,0,sin)
		double centerX = owner.getX() + Math.sin(bodyRad) * BACK_DIST;
		double centerZ = owner.getZ() - Math.cos(bodyRad) * BACK_DIST;
		double centerY = owner.getY() + HOVER_HEIGHT;
		double leftX = Math.cos(bodyRad);
		double leftZ = Math.sin(bodyRad);
		double th = Math.toRadians(SLOT_ELEV[slot]);
		double a = SLOT_SIDE[slot] * Math.cos(th);
		double b = Math.sin(th);
		return new Vec3d(centerX + leftX * a * HOVER_RADIUS, centerY + b * HOVER_RADIUS, centerZ + leftZ * a * HOVER_RADIUS);
	}

	/** 环绕朝向（水平）：与身体朝向平行，尖朝头部所指方向，最多偏 ±MAX_THORN_TURN。 */
	public static float hoverYaw(LivingEntity owner) {
		float headDelta = MathHelper.wrapDegrees(owner.headYaw - owner.bodyYaw);
		return owner.bodyYaw + MathHelper.clamp(headDelta, -MAX_THORN_TURN, MAX_THORN_TURN);
	}

	/** 环绕朝向（垂直）：尖随头部俯仰抬起/压低，最多偏 ±MAX_THORN_TURN。 */
	public static float hoverPitch(LivingEntity owner) {
		return MathHelper.clamp(owner.getPitch(), -MAX_THORN_TURN, MAX_THORN_TURN);
	}

	/** 客户端 HOVER 态：按本地玩家每 tick 自算贴合位置（渲染 lerp(prev, cur)，prev 先同步防瞬移包拖影）。 */
	private void hoverFollowClient() {
		Optional<UUID> oid = this.dataTracker.get(OWNER_UUID);
		if (oid.isEmpty()) return;
		PlayerEntity p = this.getWorld().getPlayerByUuid(oid.get());
		if (p == null) return;
		// 先同步 prev 位置/朝向再写入新值（渲染用 lerp(prev, cur, tickDelta)）：
		// 防服务端 40 格校正的瞬移包造成「从旧位置到新位置」的一帧插值拖影闪现，也保证朝向变化平滑插值
		this.prevX = this.getX();
		this.prevY = this.getY();
		this.prevZ = this.getZ();
		this.prevYaw = this.getYaw();
		this.prevPitch = this.getPitch();
		Vec3d target = hoverTarget(p, getSlot());
		this.setPosition(target.x, target.y, target.z);
		// 钉死渲染插值源：trackedPos 是 EntityRenderer 平移/lerp 的唯一数据源，
		// 若不重置，服务器同步包（生成位/校正位等旧值）会周期性喂入缓冲、逐帧向旧位逼近，
		// 与本地自算位置打架——表现为冰锥周期性「自后向前蹿一下」。用公开 API 更新插值源。
		this.updateTrackedPosition(target.x, target.y, target.z);
		this.setYaw(hoverYaw(p));
		this.setPitch(hoverPitch(p));		// 成形汇聚（前 20t）：在自身当前位置发向内汇聚粒子（客户端本地）。
		// 中心点 = 冰锥渲染位置，每 tick 随本地玩家同步更新——彻底消除服务端预测点与渲染位置的错位。
		if (this.age < 20) {
			for (int i = 0; i < 2; i++) {
				double u = this.random.nextDouble() * 2 - 1;
				double theta = this.random.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(1 - u * u);
				double dx = r * Math.cos(theta), dy = u, dz = r * Math.sin(theta);
				double speed = 1.0 / 20.0; // 与服务端同款：1格/20t，抵达中心即寿命尽消失
				this.getWorld().addParticle(SscAddon.INWARD_ICE_PARTICLE,
						this.getX() + dx, this.getY() + dy, this.getZ() + dz,
						-dx * speed, -dy * speed, -dz * speed);
			}
		}		// 环境粒子：仿雪狐 ambient 粒子机制（apoli particle power：零初速生成，靠粒子自身物理缓飘）
		// 白色小冰晶密度为上一版的 10%（100 tick 一朵）；雪花概率为上一版的 30%（1/100）
		// 每个客户端各自本地生成（所有客户端都自算贴合位置，各视角一致）
		if (this.age % 100 == 0) {
			this.getWorld().addParticle(ParticleTypes.WHITE_ASH, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		}
		if (this.random.nextInt(100) == 0) {
			this.getWorld().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		}
	}

	/** FrostSpikeManager 每 tick 设置 HOVER 冰锥的位置与朝向（环绕玩家斜上方）。 */
	public void setHoverTransform(Vec3d pos, float yaw, float pitch) {
		this.setPosition(pos.x, pos.y, pos.z);
		this.setYaw(yaw); this.prevYaw = yaw;
		this.setPitch(pitch); this.prevPitch = pitch;
	}

	/** 发射：切 FLY 态，从当前(环绕)位置出发，飞行中靠拢到发射瞬间的准星射线。 */
	public void launch(Vec3d rayOrigin, Vec3d rayDir) {
		this.dataTracker.set(STATE, STATE_FLY);
		this.flyStart = this.getPos();
		this.flyTicks = 0;
		this.rayOrigin = rayOrigin;
		this.rayDir = rayDir.normalize();
		this.converging = true;
		Vec3d v = this.rayDir.multiply(SPEED);
		this.setVelocity(v.x, v.y, v.z);
		this.dataTracker.set(VEL_X, (float) v.x);
		this.dataTracker.set(VEL_Y, (float) v.y);
		this.dataTracker.set(VEL_Z, (float) v.z);
		updateRotationFromVelocity(v);
	}

	/** 凝棘发射：强化冰锥（消耗 level 个环绕冰锥凝成）。从头顶法阵中心沿调用方给定的方向**纯直线**飞行（尖朝速度方向，无弯曲汇入——弯曲拐弯会被看成「旋转着飞」），无下坠、无距离销毁，{@value #ENHANCED_MAX_FLY_TICKS} tick(10s) 后消失。 */
	public void launchEnhanced(Vec3d origin, Vec3d dir, int level, Vec3d rayOrigin, Vec3d rayDir) {
		this.dataTracker.set(LEVEL, Math.max(1, level));
		this.dataTracker.set(STATE, STATE_FLY);
		this.setPosition(origin.x, origin.y, origin.z);
		this.flyStart = origin;
		this.flyTicks = 0;
		this.rayOrigin = rayOrigin;
		this.rayDir = rayDir.normalize();
		this.converging = true;   // 发射即垂足靠拢并入准星中线（横向位置偏差收敛到 CONVERGE_DONE 后沿准星线直飞）
		this.enhancedSpeed = SPEED * (1.0 + 0.5 * level); // 每消耗一个冰锥 +50% 飞行速度（基础 16 格/s）
		Vec3d v = dir.normalize().multiply(this.enhancedSpeed);
		this.setVelocity(v.x, v.y, v.z);
		this.dataTracker.set(VEL_X, (float) v.x);
		this.dataTracker.set(VEL_Y, (float) v.y);
		this.dataTracker.set(VEL_Z, (float) v.z);
		updateRotationFromVelocity(v);
	}

	private void updateRotationFromVelocity(Vec3d v) {
		// 渲染器绕 Y(+yaw)/X(+pitch) 旋转且模型尖朝 +Z：yaw/pitch 取负号才能让尖端指向速度方向
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYaw((float) (MathHelper.atan2(-v.x, v.z) * (180.0 / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(-v.y, horiz) * (180.0 / Math.PI)));
	}

	/**
	 * 客户端 FLY 态：忽略服务端 move/rotate 跟踪包（位置 + 朝向全部以本地外推为唯一权威）。
	 *
	 * <p>反编译 {@code EntityTrackerEntry} 实证：服务端每 tick 发的 rotate 包携带的是上一 tick 的朝向
	 * （网络延迟 1~3t + 256 级量化），基类 {@code updateTrackedPositionAndAngles} 对非生物实体是
	 * 无插值的直接 setRotation——网络旧值与本地按速度自算的新朝向每 tick 相互拉锯，
	 * 弯曲段（向准星射线靠拢，yaw 逐 tick 大变）被放大成「旋转着飞」。
	 * 位置/速度经 DataTracker 全精度每 tick 同步（VEL_X/Y/Z），本地外推已自洽，忽略跟踪包即可根治。</p>
	 */
	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
		if (this.getWorld().isClient && getState() == STATE_FLY) return; // FLY 态：外推权威，忽略网络包
		super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps, interpolate);
	}

	@Override
	public void updateTrackedHeadRotation(float yaw, int interpolationSteps) {
		if (this.getWorld().isClient && getState() == STATE_FLY) return;
		super.updateTrackedHeadRotation(yaw, interpolationSteps);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			// 客户端外推 FLY 位置（用 DataTracker 全精度速度覆盖被生成包截断的 velocity）
			if (getState() == STATE_FLY) {
				Vec3d v = new Vec3d(dataTracker.get(VEL_X), dataTracker.get(VEL_Y), dataTracker.get(VEL_Z));
				// 速度数据包比生成包晚到 1~2 tick：未到前 VEL 为初始 0，若照常更新会把朝向 atan2(0,0)=0 归零，
				// 数据到达后跳回正确朝向 → 起飞瞬间「先甩头再归位」。未到前不动不转，保持生成包携带的正确姿态。
				if (v.lengthSquared() > 1.0e-8) {
					this.setVelocity(v.x, v.y, v.z);
					this.prevYaw = getYaw(); this.prevPitch = getPitch();
					this.setPosition(getX() + v.x, getY() + v.y, getZ() + v.z);
					// 朝向与速度同步：客户端按与服务端同款公式自算，平滑贴合轨迹（单/多人一致）
					updateRotationFromVelocity(v);
				}
			} else {
				hoverFollowClient();  // HOVER 态：按本地玩家自算贴合，平滑跟手
			}
			return;
		}
		// 重载的飞行残留：速度/起点不持久化，重进后会冻在空中——直接清除
		if (getState() == STATE_FLY && this.flyStart == null) {
			this.discard();
			return;
		}
		// 净化消除：主人被 SP 悦灵净化 → 无论环绕 / 飞行都碎裂（owner 引用在下方 adopt 中恢复）
		if (this.getOwner() instanceof ServerPlayerEntity op && op.hasStatusEffect(SscAddon.PURIFIED)) {
			shatter();
			return;
		}
		if (getState() == STATE_HOVER) {
			hoverTicks++;
			updateStage();
			if (hoverTicks > MAX_HOVER_TICKS) { shatter(); return; } // 存在时间到期
			// 重进游戏自找回：ProjectileEntity 的 owner 不写 NBT、管理器静态状态也不持久化，
			// 重载后环绕冰锥成孤儿——用 OWNER_UUID 找回主人并重新挂进管理器（幂等，已认领则跳过）
			FrostSpikeManager.adopt(this);
		} else {
			tickFly();
		}
	}

	private void updateStage() {
		int stage = hoverTicks < 400 ? 0 : (hoverTicks < 800 ? 1 : 2);
		if (this.dataTracker.get(STAGE) != stage) this.dataTracker.set(STAGE, stage);
	}

	private void tickFly() {
		flyTicks++;
		// 凝棘强化冰锥：从法阵中央弯曲汇入准星射线（同主技能），汇入后直飞（无下坠、无距离销毁），10 秒后消失；飞行中持续播高速划破空气音
		if (getLevel() > 0) {
			HitResult ehit = ProjectileUtil.getCollision(this, this::canHit);
			if (ehit.getType() != HitResult.Type.MISS) {
				this.onCollision(ehit);
				if (this.isRemoved()) return;
			}
			Vec3d ev = this.getVelocity();
			if (converging && rayDir != null) {
				// 真正并入准星中线（垂足靠拢）：朝射线上最近点偏转，横向偏差收敛到阈值后沿准星线直飞。
				// 强化冰锥用低弯曲强度（ENHANCED_CONVERGE）：弧线拉长约 9 格平缓滑入，防近处猛拐；
				// 阈值收紧 0.1 格，大冰锥最终弹道与准星线重合（残差肉眼不可见）
				Vec3d pos = this.getPos();
				double t = Math.max(0.0, pos.subtract(rayOrigin).dotProduct(rayDir));
				Vec3d proj = rayOrigin.add(rayDir.multiply(t));
				Vec3d lateral = proj.subtract(pos);
				if (lateral.length() < ENHANCED_CONVERGE_DONE) {
					converging = false;
					ev = rayDir.multiply(enhancedSpeed);
				} else {
					Vec3d cdir = rayDir.add(lateral.normalize().multiply(ENHANCED_CONVERGE)).normalize();
					ev = cdir.multiply(enhancedSpeed);
				}
				this.setVelocity(ev);
				this.dataTracker.set(VEL_X, (float) ev.x);
				this.dataTracker.set(VEL_Y, (float) ev.y);
				this.dataTracker.set(VEL_Z, (float) ev.z);
			}
			this.prevYaw = getYaw(); this.prevPitch = getPitch();
			this.setPosition(getX() + ev.x, getY() + ev.y, getZ() + ev.z);
			updateRotationFromVelocity(ev);
			if (this.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 3, 0.08, 0.08, 0.08, 0.0);
				if (flyTicks % FLIGHT_SOUND_INTERVAL == 1) {
					// 高速划破空气音：用 FromEntity 包让声源跟随冰锥移动（非钉在播放坐标）
					RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(SoundEvents.ITEM_TRIDENT_RIPTIDE_3);
					PlaySoundFromEntityS2CPacket pkt = new PlaySoundFromEntityS2CPacket(entry, SoundCategory.PLAYERS, this, 0.8f, 1.0f, this.random.nextLong());
					for (ServerPlayerEntity p : sw.getPlayers()) {
						if (p.squaredDistanceTo(this) < 64.0 * 64.0) p.networkHandler.sendPacket(pkt);
					}
				}
			}
			if (flyTicks > ENHANCED_MAX_FLY_TICKS) { this.discard(); }
			return;
		}
		// 碰撞判定仅服务端、移动前扫掠整段路径（高速不漏怪）
		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) {
			this.onCollision(hit);
			if (this.isRemoved()) return;
		}
		Vec3d v = this.getVelocity();
		if (converging && rayDir != null) {
			// 向发射瞬间的准星射线自然靠拢：速度方向 = 射线方向 + 指向射线垂足的横向分量（随靠近而减小）
			Vec3d pos = this.getPos();
			double t = Math.max(0.0, pos.subtract(rayOrigin).dotProduct(rayDir));
			Vec3d proj = rayOrigin.add(rayDir.multiply(t));
			Vec3d lateral = proj.subtract(pos);
			if (lateral.length() < CONVERGE_DONE) {
				// 已汇入射线 → 转直飞，从此点重新起算 16 格直线
				converging = false;
				v = rayDir.multiply(SPEED);
				this.flyStart = pos;
			} else {
				Vec3d dir = rayDir.add(lateral.normalize().multiply(CONVERGE)).normalize();
				v = dir.multiply(SPEED);
			}
			this.setVelocity(v);
			this.dataTracker.set(VEL_X, (float) v.x);
			this.dataTracker.set(VEL_Y, (float) v.y);
			this.dataTracker.set(VEL_Z, (float) v.z);
		} else {
			// 直飞：16 格后逐 tick 下坠
			if (flyStart != null && this.squaredDistanceTo(flyStart) > STRAIGHT_DIST * STRAIGHT_DIST) {
				v = new Vec3d(v.x, v.y - GRAVITY, v.z);
				this.setVelocity(v);
				this.dataTracker.set(VEL_Y, (float) v.y);
			}
		}
		this.prevYaw = getYaw(); this.prevPitch = getPitch();
		this.setPosition(getX() + v.x, getY() + v.y, getZ() + v.z);
		updateRotationFromVelocity(v);
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
		if (flyStart != null && this.squaredDistanceTo(flyStart) > MAX_FLY_DIST * MAX_FLY_DIST) { this.discard(); return; }
		if (flyTicks > MAX_FLY_TICKS) { this.discard(); }
	}

	@Override
	protected void onEntityHit(EntityHitResult hitResult) {
		super.onEntityHit(hitResult);
		if (this.getWorld().isClient) return;
		if (getState() != STATE_FLY) return;
		if (hitResult.getEntity() instanceof LivingEntity living) {
			// 默认白名单：豁免玩家 / 宠物 / 白名单个体
			if (!(this.getOwner() instanceof ServerPlayerEntity op) || !WhitelistUtils.isProtected(op, living)) {
				float dmg = getLevel() > 0 ? ENHANCED_BASE_DAMAGE * (1 + getLevel()) : DAMAGE;
				living.damage(this.getDamageSources().mobAttack(this.getOwner() instanceof LivingEntity l ? l : null), dmg);
			}
		}
		shatter();
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (this.getWorld().isClient) return;
		if (getState() != STATE_FLY) return;
		if (hitResult.getType() == HitResult.Type.BLOCK) shatter();
	}

	/** 碎裂：冰晶粒子 + 玻璃碎裂音效 + 移除。 */
	private void shatter() {
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 12, 0.15, 0.15, 0.15, 0.05);
			sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.7f, 1.3f);
		}
		this.discard();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("State", getState());
		nbt.putInt("HoverTicks", hoverTicks);
		nbt.putInt("Slot", getSlot());
		// 主人 UUID 持久化：重进游戏后客户端跟随 / 服务端自认领都靠它找回主人
		getOwnerUuid().ifPresent(u -> nbt.putUuid("OwnerUuid", u));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(STATE, nbt.getInt("State"));
		this.hoverTicks = nbt.getInt("HoverTicks");
		this.dataTracker.set(SLOT, nbt.getInt("Slot"));
		if (nbt.containsUuid("OwnerUuid")) {
			this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("OwnerUuid")));
		}
	}

	@Override
	public Box getVisibilityBoundingBox() {
		// 碰撞盒仅 0.3³，而渲染模型（缩放 0.48 + 偏心补偿平移）延伸到锚点外约半格——
		// 扩大可见盒避免视锥剔除把模型整帧剔除导致的周期性闪烁（同 LaserBeamEntity 先例）
		return this.getBoundingBox().expand(2.0);
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
