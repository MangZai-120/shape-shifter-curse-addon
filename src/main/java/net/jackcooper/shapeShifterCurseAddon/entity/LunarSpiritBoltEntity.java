package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 月灵光弹（jackcooper，召唤系）：月灵远程攻击的曲线光弹——发射时朝目标方向
 * 随机偏 ±45° 射出，飞行中每 tick 向目标转向（限速）形成弧线弹道（奥里的守卫风格）。
 *
 * <p><b>末地烛粒子拖尾</b>（END_ROD）；<b>不穿墙</b>（方块碰撞即消散）；
 * 命中造成魔法伤害（归因召唤主人的主人=玩家）+ 变体 debuff：
 * 粉=点燃 3s / 蓝=缓速 I 3s / 绿=中毒 I 3s。白名单友军免伤。</p>
 */
public class LunarSpiritBoltEntity extends ProjectileEntity {

	/** 飞行速度（格/tick）。 */
	private static final double SPEED = 0.7;
	/** 每 tick 转向强度（0-1，越大弯越急）。 */
	private static final double TURN_RATE = 0.14;
	/** 最大存活（tick）兜底。 */
	private static final int MAX_TICKS = 80;

	/** 变体（0 粉/1 蓝/2 绿，决定命中 debuff 与拖尾配色）。 */
	private static final TrackedData<Integer> VARIANT =
			DataTracker.registerData(LunarSpiritBoltEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** 追踪目标 UUID（曲线转向目标；目标消失则直线飞完）。 */
	private static final TrackedData<Integer> TARGET_ID =
			DataTracker.registerData(LunarSpiritBoltEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int ticksAlive = 0;

	public LunarSpiritBoltEntity(EntityType<? extends LunarSpiritBoltEntity> entityType, World world) {
		super(entityType, world);
	}

	public LunarSpiritBoltEntity(World world, LivingEntity owner) {
		super(SscAddon.LUNAR_SPIRIT_BOLT_ENTITY, world);
		this.setOwner(owner);
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(VARIANT, 2);
		this.dataTracker.startTracking(TARGET_ID, 0);
	}

	public int getVariant() {
		return this.dataTracker.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, Math.max(0, Math.min(2, variant)));
	}

	/** 设置追踪目标（服务端；实体 id 经 DataTracker 同步供渲染/调试）。 */
	public void setTargetEntity(LivingEntity target) {
		this.dataTracker.set(TARGET_ID, target == null ? 0 : target.getId());
	}

	private LivingEntity resolveTarget() {
		int id = this.dataTracker.get(TARGET_ID);
		if (id == 0) {
			return null;
		}
		Entity e = this.getWorld().getEntityById(id);
		return e instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		// —— 曲线转向：每 tick 把速度方向朝目标方向插值（限转向强度）——
		Vec3d velocity = this.getVelocity();
		LivingEntity target = resolveTarget();
		if (target != null) {
			Vec3d desired = target.getPos().add(0, target.getHeight() * 0.6, 0)
					.subtract(this.getPos()).normalize();
			Vec3d current = velocity.normalize();
			// 线性插值转向（夹角大时逐步收拢 → 弧线弹道）
			Vec3d steered = current.multiply(1.0 - TURN_RATE).add(desired.multiply(TURN_RATE)).normalize();
			velocity = steered.multiply(SPEED);
			this.setVelocity(velocity);
		}

		// 匀速位移 + 碰撞检测（含方块 → 不穿墙）
		this.setPosition(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
		HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
		if (hitResult.getType() != HitResult.Type.MISS) {
			this.onCollision(hitResult);
		}
		if (this.isRemoved()) {
			return;
		}

		// 超时自毁（仅服务端权威）
		if (!this.getWorld().isClient && ticksAlive > MAX_TICKS) {
			this.discard();
			return;
		}

		// 末地烛粒子拖尾（服务端撒，天然多人同步；数量按变体微调配色不可行——END_ROD 固定白色，加染色核心粒子）
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.002);
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity livingTarget && !this.getWorld().isClient) {
			// 白名单：光弹主人是月灵 → 归因链取玩家主人；受保护目标免伤
			ServerPlayerEntity ownerPlayer = resolveOwnerPlayer();
			if (ownerPlayer != null && WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
				this.discard();
				return;
			}
			// 伤害：月灵攻击力 + 变体倍率（粉×1.5 / 蓝×0.7 / 绿×1.0）；attacker 归因玩家主人（击杀 / 经验归玩家），
			// source 标记为光弹本体，供 LunarSpiritTargetLink 区分「玩家亲手攻击」与「月灵光弹命中」
			float damage = 3.0f;
			int variant = getVariant();
			if (variant == 0) {
				damage *= 1.5f;
			} else if (variant == 1) {
				damage *= 0.7f;
			}
			if (ownerPlayer != null) {
				livingTarget.damage(this.getDamageSources().indirectMagic(this, ownerPlayer), damage);
			} else {
				livingTarget.damage(this.getDamageSources().magic(), damage);
			}
			// 变体 debuff：粉=点燃 3s / 蓝=缓速 I 3s / 绿=中毒 I 3s
			if (variant == 0) {
				livingTarget.setFireTicks(60);
			} else if (variant == 1) {
				livingTarget.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
						net.minecraft.entity.effect.StatusEffects.SLOWNESS, 60, 0));
			} else {
				livingTarget.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
						net.minecraft.entity.effect.StatusEffects.POISON, 60, 0));
			}
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.6f);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// 命中方块 → 光弹碎裂消散（不穿墙）；实体命中的伤害在 onEntityHit
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.END_ROD,
						this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
			}
			this.discard();
		}
	}

	/** 归因链：光弹主人（月灵）的玩家主人。 */
	private ServerPlayerEntity resolveOwnerPlayer() {
		if (this.getOwner() instanceof LunarSpiritEntity spirit) {
			return spirit.getOwnerPlayer() instanceof ServerPlayerEntity sp ? sp : null;
		}
		return this.getOwner() instanceof ServerPlayerEntity sp ? sp : null;
	}

	@Override
	protected boolean canHit(Entity entity) {
		// 不打月灵（自己与同伴）、盔甲架；只打生物
		if (entity == this.getOwner() || entity instanceof LunarSpiritEntity || entity instanceof ArmorStandEntity) {
			return false;
		}
		if (!(entity instanceof LivingEntity living) || !super.canHit(entity)) {
			return false;
		}
		// 穿过主人与白名单友方：月灵常在主人背后开火，不能被主人身体挡住吞掉（客户端取不到主人玩家，伤害仍由服务端权威）
		ServerPlayerEntity ownerPlayer = resolveOwnerPlayer();
		return ownerPlayer == null || !WhitelistUtils.isProtected(ownerPlayer, living);
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Variant", getVariant());
		nbt.putInt("BoltTicks", ticksAlive);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Variant")) {
			setVariant(nbt.getInt("Variant"));
		}
		ticksAlive = nbt.getInt("BoltTicks");
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
