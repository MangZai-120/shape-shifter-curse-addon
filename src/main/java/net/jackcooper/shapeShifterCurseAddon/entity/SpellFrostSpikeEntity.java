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
 * 月尘魔法·冰锥投射物（jackcooper）。
 *
 * <p>朝准星直线匀速飞行（无重力），命中生物造成魔法伤害后消失；超距/超时自毁。
 * 与雪狐 SP 的 {@link FrostBallEntity} 无关（不施加霜降、无护符分裂、无追踪），
 * 供魔法书「冰锥」魔法独立使用，保持低耦合。默认白名单：主人在线且目标受保护则不伤害。</p>
 */
public class SpellFrostSpikeEntity extends ProjectileEntity implements FlyingItemEntity {

	private static final double SPEED = 0.75;        // 15 格/秒
	private static final double MAX_DISTANCE = 50.0; // 最大飞行距离

	/** 魔法等级（1-5），DataTracker 同步供渲染端切换 L4+ 3D 冰锥模型。 */
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellFrostSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private Vec3d startPos;
	private int ticksAlive = 0;
	private float damage = 6.0f;

	/** 命中发放的经验赏金（×10 整数；exp_mode 1/2 挂起部分由施法时装入，NBT 持久化跨 tick；齐射均分后每枚持有份额）。 */
	private int expBountyTen = 0;

	/** 剩余可命中敌人数（含当前；0 = 命中即碎，单发冰锥默认；齐射按等级注入 2+等级-1）。 */
	private int pierceRemaining = 0;
	/** 已命中过的实体 UUID（穿刺期防同一目标被多 tick 重复判定）。 */
	private final java.util.List<java.util.UUID> piercedTargets = new java.util.ArrayList<>();
	/** 本 tick 命中为「穿刺穿过」的信号旗（onEntityHit 置位 → onCollision 跳过销毁）。 */
	private boolean passThroughHit = false;

	/** 设置单枚最多可命中敌人数（穿刺数；≤1 等同不穿刺，命中即碎）。 */
	public void setPierceCount(int count) {
		this.pierceRemaining = Math.max(0, count);
	}

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

	@Override
	protected void initDataTracker() {
		// 不调 super（Entity 的抽象方法无法跨两级访问；dataTracker 由 Entity 构造器初始化，同 ThrownWaterSpearEntity 写法）
		this.dataTracker.startTracking(LEVEL, 1);
	}

	/** 设置冰锥命中伤害。 */
	public void setDamage(float damage) {
		this.damage = damage;
	}

	/** 魔法等级（1-5；L4+ 渲染端换 3D 冰锥模型）。 */
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

	/** 按速度自算朝向（与寒棘狐冰锥同款公式：尖朝速度方向），供 3D 模型渲染对正。 */
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

		// 超距 / 超时自毁（仅服务端权威）：客户端实体经网络包构造，startPos 恒为 (0,0,0)，
		// 若双端执行会在离原点 50 格外第一 tick 就误删客户端实体 → 模型永不显示（只剩服务端粒子）。
		// 客户端实体的消失由服务端 discard 后的 tracker 移除包驱动，天然多人同步。
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
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		this.passThroughHit = false; // 每次碰撞先重置穿刺旗标
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// 穿刺穿过：轻量雪花反馈后继续飞行，不碎裂
			if (this.passThroughHit) {
				if (this.getWorld() instanceof ServerWorld serverWorld) {
					ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
							this.getX(), this.getY(), this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
				}
				return;
			}
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
			boolean damaged;
			if (this.getOwner() instanceof LivingEntity owner) {
				// 法术伤害专用类型（ssc_addon:spell_damage）：供法术抗性附魔精确识别（jackcooper）
				damaged = livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources(), owner), damage);
			} else {
				damaged = livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources()), damage);
			}
			// exp_mode 1/2 命中补发：damage 成功才发放，发放后清零防重复
			if (damaged && this.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
				net.jackcooper.shapeShifterCurseAddon.spell.SpellExpGrant.grant(ownerPlayer, expBountyTen);
				expBountyTen = 0;
			}
			// 流派命中钩子（2026-09-17）：雪狐霜脉冰系命中+寒霜；噬梦/噬咒等亦经此入口
			if (damaged && this.getOwner() instanceof ServerPlayerEntity styleOwner) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(
						styleOwner, livingTarget,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.ICE, refundCastId);
			}
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 1.2f);
			// 穿刺结算（齐射来源 pierceRemaining>0）：记入已命中表并递减；
			// 递减后仍 >0 → 置旗穿过继续飞，否则本次为最后一次命中、照常碎裂。
			// 默认单发冰锥 pierceRemaining=0 → 减成 -1 不 >0 → 行为不变（命中即碎）。
			this.pierceRemaining--;
			this.piercedTargets.add(livingTarget.getUuid());
			if (this.pierceRemaining > 0) {
				this.passThroughHit = true;
			}
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		// 排除盔甲架（与月灵光弹同口径）：假人不产返还/经验
		return super.canHit(entity) && entity != this.getOwner() && entity instanceof LivingEntity
				&& !(entity instanceof net.minecraft.entity.decoration.ArmorStandEntity)
				&& !this.piercedTargets.contains(entity.getUuid());
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
		if (nbt.contains("ExpBountyTen")) {
			this.expBountyTen = Math.max(0, nbt.getInt("ExpBountyTen"));
		}
		refundCastId = nbt.containsUuid("RefundCastId") ? nbt.getUuid("RefundCastId") : null;
		if (nbt.contains("PierceRemaining")) {
			this.pierceRemaining = nbt.getInt("PierceRemaining");
		}
		this.piercedTargets.clear();
		if (nbt.contains("PiercedTargets")) {
			net.minecraft.nbt.NbtList pierced = nbt.getList("PiercedTargets", 8);
			for (int i = 0; i < pierced.size(); i++) {
				try {
					this.piercedTargets.add(java.util.UUID.fromString(pierced.getString(i)));
				} catch (IllegalArgumentException ignored) {
				}
			}
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
		nbt.putInt("ExpBountyTen", this.expBountyTen);
		if (refundCastId != null) nbt.putUuid("RefundCastId", refundCastId);
		nbt.putInt("PierceRemaining", this.pierceRemaining);
		net.minecraft.nbt.NbtList pierced = new net.minecraft.nbt.NbtList();
		for (java.util.UUID uuid : this.piercedTargets) {
			pierced.add(net.minecraft.nbt.NbtString.of(uuid.toString()));
		}
		nbt.put("PiercedTargets", pierced);
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
