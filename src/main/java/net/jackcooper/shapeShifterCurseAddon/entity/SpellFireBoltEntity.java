package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
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

/**
 * 月尘魔法·火球投射物（jackcooper）。结构与 {@link SpellFrostSpikeEntity} 同范式：
 * 朝准星直线匀速飞行（无重力），命中造成魔法伤害并<b>点燃</b>目标后消失；超距/超时自毁。
 * 默认白名单：主人在线且目标受保护则不伤害、不点燃。
 */
public class SpellFireBoltEntity extends ProjectileEntity implements FlyingItemEntity {

	private static final double SPEED = 0.8;         // 16 格/秒
	private static final double MAX_DISTANCE = 50.0; // 最大飞行距离

	/** 魔法等级（1-5），DataTracker 同步（预留渲染端换模型/缩放，当前仅存档用）。 */
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellFireBoltEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private Vec3d startPos;
	private int ticksAlive = 0;
	private float damage = 5.0f;
	/** 命中点燃时长（tick）。 */
	private int fireTicks = 60;

	public SpellFireBoltEntity(EntityType<? extends SpellFireBoltEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public SpellFireBoltEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_FIRE_BOLT_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	@Override
	protected void initDataTracker() {
		// 不调 super（Entity 的抽象方法无法跨两级访问；dataTracker 由 Entity 构造器初始化，同冰锥写法）
		this.dataTracker.startTracking(LEVEL, 1);
	}

	/** 设置火球命中伤害。 */
	public void setDamage(float damage) {
		this.damage = damage;
	}
	/** 命中发放的经验赏金（×10 整数；exp_mode 1/2 挂起部分由施法时装入，NBT 持久化跨 tick）。 */
	private int expBountyTen = 0;

	public void setExpBountyTen(int expTen) {
		this.expBountyTen = Math.max(0, expTen);
	}

	public int getExpBountyTen() {
		return expBountyTen;
	}

	/** 本次施法实际耗蓝（命中返还类流派用；与经验赏金同模式跨 tick 存 NBT）。 */
	private java.util.UUID refundCastId;

	public void setRefundCastId(java.util.UUID castId) {
		this.refundCastId = castId;
	}
	/** 魔法等级（1-5）。 */
	public int getSpellLevel() {
		return this.dataTracker.get(LEVEL);
	}

	/** 设置魔法等级（服务端施法时调用，DataTracker 自动同步客户端）。 */
	public void setLevel(int level) {
		this.dataTracker.set(LEVEL, Math.max(1, Math.min(5, level)));
	}

	/** 设置命中点燃时长（tick）。 */
	public void setFireTicks(int fireTicks) {
		this.fireTicks = fireTicks;
	}

	/** 设置飞行方向（朝准星），速度按等级倍率缩放。 */
	public void setDirection(Vec3d direction, float speedMultiplier) {
		Vec3d velocity = direction.normalize().multiply(SPEED * speedMultiplier);
		this.setVelocity(velocity.x, velocity.y, velocity.z);
		updateRotationFromVelocity(velocity);
	}

	/** 按速度自算朝向（同寒棘狐冰锥公式），供渲染对正。 */
	private void updateRotationFromVelocity(Vec3d v) {
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYaw((float) (MathHelper.atan2(-v.x, v.z) * (180.0 / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(-v.y, horiz) * (180.0 / Math.PI)));
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

		// 飞行拖尾粒子（服务端撒，天然多人同步）
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.4f);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME,
						this.getX(), this.getY(), this.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
			}
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity livingTarget && !this.getWorld().isClient) {
			// 公共命中结算（白名单豁免 → 法术伤害 → 经验补发 → 流派钩子）：见 SpellHitHelper
			net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.HitResult hit =
					net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.projectileHit(
							this.getOwner(), livingTarget, damage,
							net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.FIRE, refundCastId, expBountyTen);
			if (hit == net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.HitResult.HIT) {
				expBountyTen = 0; // 经验已发放，清零防重复
			}
			if (hit == net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.HitResult.PROTECTED) {
				return; // 白名单豁免：不点燃、不播音
			}
			// 点燃目标（伤害数值外的固定附加效果；时长由法术按等级写入）
			livingTarget.setFireTicks(fireTicks);
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_ON_FIRE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		// 排除盔甲架（与月灵光弹同口径）：假人不产返还/经验，防噬梦等流派打假人蹭回蓝
		return super.canHit(entity) && entity != this.getOwner()
				&& entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		refundCastId = nbt.containsUuid("RefundCastId") ? nbt.getUuid("RefundCastId") : null;
		if (nbt.contains("StartX")) {
			this.startPos = new Vec3d(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ"));
		}
		if (nbt.contains("Damage")) {
			this.damage = nbt.getFloat("Damage");
		}
		if (nbt.contains("SpellLevel")) {
			setLevel(nbt.getInt("SpellLevel"));
		}
		if (nbt.contains("FireTicks")) {
			this.fireTicks = nbt.getInt("FireTicks");
		}
		if (nbt.contains("ExpBountyTen")) {
			this.expBountyTen = Math.max(0, nbt.getInt("ExpBountyTen"));
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
		nbt.putFloat("Damage", this.damage);
		nbt.putInt("SpellLevel", getSpellLevel());
		nbt.putInt("FireTicks", this.fireTicks);
		nbt.putInt("ExpBountyTen", this.expBountyTen);
		if (refundCastId != null) nbt.putUuid("RefundCastId", refundCastId);
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(Items.FIRE_CHARGE);
	}
}
