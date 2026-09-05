package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 月尘魔法·冰锥投射物（jackcooper）。
 *
 * <p>朝准星直线匀速飞行（无重力），命中生物造成魔法伤害后消失；超距/超时自毁。
 * 与雪狐 SP 的 {@link FrostBallEntity} 无关（不施加霜降、无护符分裂、无追踪），
 * 供魔法书「冰锥」魔法独立使用，保持低耦合。默认白名单：主人在线且目标受保护则不伤害。</p>
 */
public class SpellFrostSpikeEntity extends ProjectileEntity implements FlyingItemEntity {

	private static final double SPEED = 0.75;        // 15 格/秒
	private static final double MAX_DISTANCE = 50.0; // 最大飞行距离

	private Vec3d startPos;
	private int ticksAlive = 0;
	private float damage = 6.0f;

	public SpellFrostSpikeEntity(EntityType<? extends SpellFrostSpikeEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public SpellFrostSpikeEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_FROST_SPIKE_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	/** 设置冰锥命中伤害。 */
	public void setDamage(float damage) {
		this.damage = damage;
	}

	/** 设置飞行方向（朝准星）。 */
	public void setDirection(Vec3d direction) {
		Vec3d velocity = direction.normalize().multiply(SPEED);
		this.setVelocity(velocity.x, velocity.y, velocity.z);
	}

	@Override
	protected void initDataTracker() {
		// 无需额外数据追踪器
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

		// 超距 / 超时自毁
		if (startPos != null && this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
			this.discard();
			return;
		}
		if (ticksAlive > 100) { // 5 秒超时
			this.discard();
			return;
		}

		// 飞行拖尾粒子（服务端撒，天然多人同步）
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
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
			// 默认白名单：主人在线且目标受保护 → 不造成伤害
			if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer
					&& WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
				return;
			}
			if (this.getOwner() instanceof LivingEntity owner) {
				livingTarget.damage(this.getDamageSources().indirectMagic(owner, owner), damage);
			} else {
				livingTarget.damage(this.getDamageSources().magic(), damage);
			}
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 1.2f);
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
		if (nbt.contains("Damage")) {
			this.damage = nbt.getFloat("Damage");
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
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(Items.SNOWBALL);
	}
}
