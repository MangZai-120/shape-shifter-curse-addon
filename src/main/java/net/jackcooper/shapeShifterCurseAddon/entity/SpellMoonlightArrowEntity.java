package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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
 * 月尘魔法·月光箭投射物（月辉系，jackcooper）。结构与 {@link SpellFireBoltEntity} 同范式：
 * 朝准星直线匀速飞行（无重力），命中造成魔法伤害；<b>对亡灵生物额外 +50% 伤害</b>后消失；
 * 超距/超时自毁。默认白名单：主人在线且目标受保护则不伤害。
 */
public class SpellMoonlightArrowEntity extends ProjectileEntity {

	private static final double SPEED = 1.0;         // 20 格/秒（比火球快，箭矢感）
	private static final double MAX_DISTANCE = 50.0; // 最大飞行距离

	/** 魔法等级（1-5），DataTracker 同步（预留渲染端换模型/缩放，当前仅存档用）。 */
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellMoonlightArrowEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private Vec3d startPos;
	private int ticksAlive = 0;
	private float damage = 4.0f;

	public SpellMoonlightArrowEntity(EntityType<? extends SpellMoonlightArrowEntity> entityType, World world) {
		super(entityType, world);
		this.startPos = this.getPos();
	}

	public SpellMoonlightArrowEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_MOONLIGHT_ARROW_ENTITY, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.getPos();
	}

	@Override
	protected void initDataTracker() {
		// 不调 super（Entity 的抽象方法无法跨两级访问；dataTracker 由 Entity 构造器初始化，同冰锥写法）
		this.dataTracker.startTracking(LEVEL, 1);
	}

	/** 设置命中伤害。 */
	public void setDamage(float damage) {
		this.damage = damage;
	}

	/** 魔法等级（1-5）。 */
	public int getSpellLevel() {
		return this.dataTracker.get(LEVEL);
	}

	/** 设置魔法等级（服务端施法时调用，DataTracker 自动同步客户端）。 */
	public void setLevel(int level) {
		this.dataTracker.set(LEVEL, Math.max(1, Math.min(5, level)));
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

		// 飞行拖尾粒子（服务端撒，天然多人同步）：月光尾迹
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.END_ROD,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
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
			// 对亡灵生物（僵尸/骷髅/幽灵等）额外增伤：每级 +10%（L1=+10% … L5=+50%）
			float finalDamage = livingTarget.isUndead() ? damage * (1.0f + 0.1f * getSpellLevel()) : damage;
			if (this.getOwner() instanceof LivingEntity owner) {
				livingTarget.damage(this.getDamageSources().indirectMagic(owner, owner), finalDamage);
			} else {
				livingTarget.damage(this.getDamageSources().magic(), finalDamage);
			}
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.5f);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// 命中演出：月辉爆闪消散（实体命中的伤害在 onEntityHit，这里统一粒子 + 收尾）
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.END_ROD,
						this.getX(), this.getY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
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
		if (nbt.contains("Damage")) {
			this.damage = nbt.getFloat("Damage");
		}
		if (nbt.contains("SpellLevel")) {
			setLevel(nbt.getInt("SpellLevel"));
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
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
