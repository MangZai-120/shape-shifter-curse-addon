package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

/**
 * 进化美西螈「投掷水矛」的直线水矛投射物。
 *
 * <p>无重力、匀速直线飞行（仿 {@link FrostBallEntity}）；直击目标 12 点物理伤害，
 * 命中点 2 格半径内额外 5 点范围伤害。默认白名单：豁免玩家 / 宠物 / 白名单个体。
 * 所有判定在服务端。</p>
 */
public class ThrownWaterSpearEntity extends ProjectileEntity {

	private static final double SPEED = 8.5;          // 格/tick（高速直刺）
	private static final double MAX_DISTANCE = 64.0;  // 最大飞行距离
	private static final float DIRECT_DAMAGE = 12.0f; // 直击物理伤害
	private static final float AOE_DAMAGE = 5.0f;     // 范围伤害
	private static final double AOE_RADIUS = 2.0;     // 范围半径

	// 全精度速度同步：生成包 velocity 用 short 编码（约 ±3.9 格/tick），8.5 格/tick 会被逐分量 clamp
	// → 客户端 velocity 方向失真 + 变慢，视觉方向/位置与服务端真实命中不符。改用 DataTracker 同步全精度速度。
	private static final TrackedData<Float> VEL_X = DataTracker.registerData(ThrownWaterSpearEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> VEL_Y = DataTracker.registerData(ThrownWaterSpearEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> VEL_Z = DataTracker.registerData(ThrownWaterSpearEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private Vec3d startPos;
	private int ticksAlive = 0;

	public ThrownWaterSpearEntity(EntityType<? extends ThrownWaterSpearEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public ThrownWaterSpearEntity(World world, LivingEntity owner) {
		super(SscAddon.THROWN_WATER_SPEAR_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	/** 设置飞行方向（归一化后按 SPEED 赋速），并让实体朝向飞行方向。 */
	public void setDirection(Vec3d direction) {
		Vec3d velocity = direction.normalize().multiply(SPEED);
		this.setVelocity(velocity.x, velocity.y, velocity.z);
		// 全精度速度写入 DataTracker，供客户端精确复现飞行方向/速度
		this.dataTracker.set(VEL_X, (float) velocity.x);
		this.dataTracker.set(VEL_Y, (float) velocity.y);
		this.dataTracker.set(VEL_Z, (float) velocity.z);
		double horiz = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		float yaw = (float) (net.minecraft.util.math.MathHelper.atan2(velocity.x, velocity.z) * (180.0 / Math.PI));
		float pitch = (float) (net.minecraft.util.math.MathHelper.atan2(velocity.y, horiz) * (180.0 / Math.PI));
		this.setYaw(yaw);
		this.prevYaw = yaw;
		this.setPitch(pitch);
		this.prevPitch = pitch;
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(VEL_X, 0.0f);
		this.dataTracker.startTracking(VEL_Y, 0.0f);
		this.dataTracker.startTracking(VEL_Z, 0.0f);
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;
		// 客户端用 DataTracker 全精度速度覆盖被生成包截断/失真的 velocity，保证视觉方向/位置与服务端一致
		if (this.getWorld().isClient) {
			this.setVelocity(this.dataTracker.get(VEL_X), this.dataTracker.get(VEL_Y), this.dataTracker.get(VEL_Z));
		}
		Vec3d velocity = this.getVelocity();

		// 碰撞判定仅服务端，且在移动前做：getCollision 扫掠 当前位置→当前+velocity（本 tick 将经过的整段路径），
		// 高速（8.5 格/tick）下也不会漏近距离目标。
		// （另：判定仅服务端——客户端 startPos 未随生成包同步、构造时为 (0,0,0)，若在客户端跑超距判定会被立即 discard，
		// 导致投射物在客户端瞬间消失、看不到模型。）
		if (!this.getWorld().isClient) {
			HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
			if (hitResult.getType() != HitResult.Type.MISS) {
				this.onCollision(hitResult);
			}
			if (this.isRemoved()) return;
		}

		// 无重力匀速移动（客户端也外推位置，保证飞行平滑）
		this.prevYaw = this.getYaw();
		this.prevPitch = this.getPitch();
		this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
		// 朝向飞行方向（供 3D 渲染器摆正水矛）
		double horiz = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		if (horiz > 1.0e-6 || velocity.y != 0) {
			this.setYaw((float) (net.minecraft.util.math.MathHelper.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
			this.setPitch((float) (net.minecraft.util.math.MathHelper.atan2(velocity.y, horiz) * (180.0 / Math.PI)));
		}

		// 超距 / 超时销毁 + 拖尾 仅服务端
		if (!this.getWorld().isClient) {
			if (startPos != null && this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
				this.discard();
				return;
			}
			if (ticksAlive > 60) {
				this.discard();
				return;
			}
			// 水矛飞行水花拖尾
			ParticleUtils.spawnParticles((ServerWorld) this.getWorld(), ParticleTypes.BUBBLE,
					this.getX(), this.getY(), this.getZ(), 3, 0.08, 0.08, 0.08, 0.02);
			ParticleUtils.spawnParticles((ServerWorld) this.getWorld(), ParticleTypes.SPLASH,
					this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		if (this.getWorld().isClient) return;
		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity living) {
			// 直击 12 物理（白名单豁免）
			if (!(this.getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity ownerP)
					|| !WhitelistUtils.isProtected(ownerP, living)) {
				living.damage(this.getDamageSources().mobAttack(this.getOwner() instanceof LivingEntity l ? l : null), DIRECT_DAMAGE);
			}
		}
		// 命中点范围伤害
		explodeAoe(target);
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (this.getWorld().isClient) return;
		if (hitResult.getType() == HitResult.Type.BLOCK) {
			explodeAoe(null); // 撞方块也做范围水花伤害
		}
		if (!this.isRemoved()) {
			this.discard();
		}
	}

	/** 命中点 2 格半径范围伤害 + 水花特效音效（服务端）。 */
	private void explodeAoe(Entity directTarget) {
		if (!(this.getWorld() instanceof ServerWorld sw)) return;
		sw.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 0.9f);
		ParticleUtils.spawnWaterBurst(sw, this.getX(), this.getY(), this.getZ(), 1.2);
		net.minecraft.server.network.ServerPlayerEntity ownerP =
				this.getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity p ? p : null;
		LivingEntity ownerLiving = this.getOwner() instanceof LivingEntity l ? l : null;
		Box box = this.getBoundingBox().expand(AOE_RADIUS);
		for (Entity e : sw.getOtherEntities(this, box)) {
			if (e == directTarget) continue; // 直击目标不重复受 AOE
			if (e instanceof LivingEntity living) {
				if (ownerP != null && WhitelistUtils.isProtected(ownerP, living)) continue;
				living.damage(this.getDamageSources().mobAttack(ownerLiving), AOE_DAMAGE);
			}
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
		if (startPos != null) {
			nbt.putDouble("StartX", startPos.x);
			nbt.putDouble("StartY", startPos.y);
			nbt.putDouble("StartZ", startPos.z);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("StartX")) {
			this.startPos = new Vec3d(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ"));
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
