package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 月尘魔法·诅咒标记投射物（诅咒系，jackcooper）。结构与 {@link SpellFireBoltEntity} 同范式：
 * 朝准星直线匀速飞行（无重力），命中目标后施加「诅咒标记」状态（受伤 +20%，时长由施法定）后消失；
 * 超距/超时自毁。默认白名单：主人在线且目标受保护则不施加。
 */
public class SpellCurseMarkEntity extends ProjectileEntity implements FlyingItemEntity {

	private static final double SPEED = 0.7;         // 14 格/秒（咒印缓慢感）
	private static final double MAX_DISTANCE = 30.0; // 最大飞行距离

	/** 魔法等级（1-5），DataTracker 同步。 */
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellCurseMarkEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** 标记时长（tick），DataTracker 同步（渲染端预留）。 */
	private static final TrackedData<Integer> DURATION =
			DataTracker.registerData(SpellCurseMarkEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private Vec3d startPos;
	private int ticksAlive = 0;

	public SpellCurseMarkEntity(EntityType<? extends SpellCurseMarkEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public SpellCurseMarkEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_CURSE_MARK_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	@Override
	protected void initDataTracker() {
		// 不调 super（Entity 的抽象方法无法跨两级访问；dataTracker 由 Entity 构造器初始化，同冰锥写法）
		this.dataTracker.startTracking(LEVEL, 1);
		this.dataTracker.startTracking(DURATION, 160);
	}

	/** 魔法等级（1-5）。 */
	public int getSpellLevel() {
		return this.dataTracker.get(LEVEL);
	}

	/** 设置魔法等级（服务端施法时调用，DataTracker 自动同步客户端）。 */
	public void setLevel(int level) {
		this.dataTracker.set(LEVEL, Math.max(1, Math.min(5, level)));
	}

	/** 标记时长（tick）。 */
	public int getDuration() {
		return this.dataTracker.get(DURATION);
	}

	/** 设置标记时长（tick）。 */
	public void setDuration(int durationTicks) {
		this.dataTracker.set(DURATION, Math.max(20, durationTicks));
	}

	/** 设置飞行方向（朝准星），速度按等级倍率缩放。 */
	public void setDirection(Vec3d direction, float speedMultiplier) {
		Vec3d velocity = direction.normalize().multiply(SPEED * speedMultiplier);
		this.setVelocity(velocity.x, velocity.y, velocity.z);
		updateRotationFromVelocity(velocity);
	}

	/** 按速度自算朝向（同冰锥公式），供渲染对正。 */
	private void updateRotationFromVelocity(Vec3d v) {
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYaw((float) (MathHelper.atan2(-v.x, v.z) * (180.0 / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(v.y, horiz) * (180.0 / Math.PI)));
		this.prevYaw = this.getYaw();
		this.prevPitch = this.getPitch();
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		// 无重力匀速移动
		Vec3d velocity = this.getVelocity();
		this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

		// 碰撞检测
		HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
		if (hitResult.getType() != HitResult.Type.MISS) {
			this.onCollision(hitResult);
		}
		if (this.isRemoved()) {
			return;
		}

		// 超距 / 超时自毁（仅服务端权威，理由同冰锥：客户端实体 startPos 恒原点，双端判会误删）
		if (!this.getWorld().isClient) {
			if (startPos != null && this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
				this.discard();
				return;
			}
			if (ticksAlive > 100) { // 5 秒超时
				this.discard();
				return;
			}
		}

		// 飞行拖尾粒子（服务端撒，天然多人同步）：暗紫咒印
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.WITCH,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity livingTarget && !this.getWorld().isClient) {
			// 默认白名单：主人在线且目标受保护 → 不施加标记
			if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer
					&& WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
				return;
			}
			// amplifier = 等级-1（L1=0 … L5=4）：mixin 按其计算受伤加深 1.2+0.1×amp
			livingTarget.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
					SscAddon.CURSE_MARK, getDuration(), getSpellLevel() - 1));
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 1.2f);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// 命中演出：暗紫咒印爆散
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.WITCH,
						this.getX(), this.getY(), this.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			}
			this.discard();
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("StartX")) {
			this.startPos = new Vec3d(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ"));
		}
		if (nbt.contains("SpellLevel")) {
			setLevel(nbt.getInt("SpellLevel"));
		}
		if (nbt.contains("MarkDuration")) {
			setDuration(nbt.getInt("MarkDuration"));
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (startPos != null) {
			nbt.putDouble("StartX", startPos.x);
			nbt.putDouble("StartY", startPos.y);
			nbt.putDouble("StartZ", startPos.z);
		}
		nbt.putInt("SpellLevel", getSpellLevel());
		nbt.putInt("MarkDuration", getDuration());
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public net.minecraft.item.ItemStack getStack() {
		// 用恶魂之泪渲染（咒印意象）
		return new net.minecraft.item.ItemStack(net.minecraft.item.Items.GHAST_TEAR);
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}
}
