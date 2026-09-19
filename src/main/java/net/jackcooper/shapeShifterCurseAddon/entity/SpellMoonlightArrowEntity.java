package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
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

	/** 按速度自算朝向（与寒棘狐冰锥同款公式：尖朝速度方向），供渲染对正。 */
	private void updateRotationFromVelocity(Vec3d v) {
		double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYaw((float) (MathHelper.atan2(-v.x, v.z) * (180.0 / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(-v.y, horiz) * (180.0 / Math.PI))); // 取负：与寒棘狐/冰锥惯例一致，配合渲染器 -pitch 旋转
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
			boolean damaged;
			if (this.getOwner() instanceof LivingEntity owner) {
				// 法术伤害专用类型（ssc_addon:spell_damage）：供法术抗性附魔精确识别（jackcooper）
				damaged = livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources(), owner), finalDamage);
			} else {
				damaged = livingTarget.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
						.of(this.getDamageSources()), finalDamage);
			}
			// exp_mode 1/2 命中补发：damage 成功才发放，发放后清零防重复
			if (damaged && this.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
				net.jackcooper.shapeShifterCurseAddon.spell.SpellExpGrant.grant(ownerPlayer, expBountyTen);
				expBountyTen = 0;
			}
			// 流派命中钩子（2026-09-17）：月辉系（织月+mana / 审魂+灵魂等）；噬梦/噬咒等亦经此入口
			if (damaged && this.getOwner() instanceof ServerPlayerEntity styleOwner) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(
						styleOwner, livingTarget,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.LUNAR, refundCastId);
			}
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.5f);
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
		// 排除盔甲架（与月灵光弹同口径）：假人不产返还/经验
		return super.canHit(entity) && entity != this.getOwner()
				&& entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity);
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
		refundCastId = nbt.containsUuid("RefundCastId") ? nbt.getUuid("RefundCastId") : null;
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
		if (refundCastId != null) nbt.putUuid("RefundCastId", refundCastId);
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
