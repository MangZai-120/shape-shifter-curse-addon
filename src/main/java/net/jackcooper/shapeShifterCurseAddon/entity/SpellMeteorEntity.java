package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.List;

/**
 * 月尘魔法·陨火实体（jackcooper）。两阶段：
 * <ol>
 *   <li><b>落点标记阶段</b>（前 {@link #DELAY_TICKS} tick）：悬停在目标落点上空，服务端持续撒
 *       火焰/烟圈粒子提示落点范围，客户端天然同步看到预警圈；</li>
 *   <li><b>落下阶段</b>：从落点上空 20 格急速坠落到落点，撞击后 AOE 结算
 *       （伤害 + 点燃 3s + 击退，白名单保护、不含施法者），火光/爆炸音效/粒子，随即消失。</li>
 * </ol>
 * 伤害与半径由施法逻辑写入。可命中判定：标记阶段与下落中的实体本身不可被攻击（canHit 恒 false）。
 */
public class SpellMeteorEntity extends ProjectileEntity implements FlyingItemEntity {

	/** 落点预警延迟（tick）。10t = 0.5 秒。 */
	private static final int DELAY_TICKS = 10;
	/** 下落起始高度（落点上空）。 */
	private static final double FALL_HEIGHT = 20.0;
	/** 下落速度（格/tick）。 */
	private static final double FALL_SPEED = 2.5;

	/** 魔法等级（1-5），DataTracker 同步。 */
	private static final TrackedData<Integer> LEVEL =
			DataTracker.registerData(SpellMeteorEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int ticksAlive = 0;
	private float damage = 8.0f;
	/** AOE 半径（格）。 */
	private double radius = 3.0;
	private boolean falling = false;

	public SpellMeteorEntity(EntityType<? extends SpellMeteorEntity> entityType, World world) {
		super(entityType, world);
	}

	public SpellMeteorEntity(World world, LivingEntity owner) {
		super(SscAddon.SPELL_METEOR_ENTITY, world);
		this.setOwner(owner);
	}

	@Override
	protected void initDataTracker() {
		// 不调 super（同冰锥写法：dataTracker 由 Entity 构造器初始化）
		this.dataTracker.startTracking(LEVEL, 1);
	}

	/** 设置陨火 AOE 伤害。 */
	public void setDamage(float damage) {
		this.damage = damage;
	}

	/** 设置 AOE 半径（格）。 */
	public void setRadius(double radius) {
		this.radius = radius;
	}

	/** 魔法等级（1-5）。 */
	public int getSpellLevel() {
		return this.dataTracker.get(LEVEL);
	}

	/** 设置魔法等级（DataTracker 自动同步客户端）。 */
	public void setLevel(int level) {
		this.dataTracker.set(LEVEL, Math.max(1, Math.min(5, level)));
	}

	/** 初始化落点：悬停位置 = 落点上空 FALL_HEIGHT（施法时调用）。 */
	public void setImpactTarget(double x, double y, double z) {
		this.setPosition(x, y + FALL_HEIGHT, z);
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		if (!falling) {
			// 标记阶段：服务端撒落点预警粒子（火焰圈 + 上升火星），客户端经粒子包同步
			if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
				// 落点 = 当前悬停位置正下方 FALL_HEIGHT 处
				double ix = this.getX();
				double iy = this.getY() - FALL_HEIGHT;
				double iz = this.getZ();
				// 预警圈：沿半径撒火焰粒子勾勒 AOE 范围
				int ringCount = (int) Math.max(12, radius * 10);
				for (int i = 0; i < ringCount; i++) {
					double angle = (i * 2 * Math.PI / ringCount) + (this.age * 0.15);
					double r = radius * 0.95;
					serverWorld.spawnParticles(ParticleTypes.FLAME,
							ix + Math.cos(angle) * r, iy + 0.1, iz + Math.sin(angle) * r,
							1, 0.02, 0.02, 0.02, 0.005);
				}
				// 中心上升烟柱提示
				serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
						ix, iy + 0.5, iz, 3, 0.3, 0.5, 0.3, 0.02);
			}
			// 延迟期满：切入下落阶段（双端都切，客户端实体也要开始下落动画）
			if (ticksAlive >= DELAY_TICKS) {
				falling = true;
				if (!this.getWorld().isClient) {
					this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
							SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.6f);
				}
			}
			return;
		}

		// 下落阶段：双端同步下坠（客户端按速度插值即可看到火球砸下）
		Vec3d velocity = new Vec3d(0, -FALL_SPEED, 0);
		this.setPosition(this.getX(), this.getY() + velocity.y, this.getZ());
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME,
					this.getX(), this.getY(), this.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		}

		// 服务端：下落阶段每 tick 降 FALL_SPEED，累计 ceil(FALL_HEIGHT / FALL_SPEED) tick 后到达落点，引爆
		if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld impactWorld) {
			if (ticksAlive >= DELAY_TICKS + Math.ceil(FALL_HEIGHT / FALL_SPEED)) {
				explode(impactWorld);
			}
		}
	}

	/** 引爆：AOE 伤害 + 点燃 3s + 击退，白名单保护、不含施法者；火光爆炸演出后自毁。 */
	private void explode(ServerWorld serverWorld) {
		double ix = this.getX();
		double iy = this.getY();
		double iz = this.getZ();
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				this.getBoundingBox().expand(radius), e -> e != this.getOwner() && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(this) > radius) {
				continue;
			}
			// 默认白名单：主人在线且目标受保护 → 免伤
			if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer
					&& WhitelistUtils.isProtected(ownerPlayer, target)) {
				continue;
			}
			// 距离衰减：中心满伤 → 边缘 40%
			double dist = target.distanceTo(this);
			float dmg = damage * (float) (1.0 - 0.6 * (dist / radius));
			if (dmg <= 0) {
				continue;
			}
			if (this.getOwner() instanceof LivingEntity owner) {
				target.damage(this.getDamageSources().indirectMagic(owner, owner), dmg);
			} else {
				target.damage(this.getDamageSources().magic(), dmg);
			}
			// 点燃 3s + 轻微击退（离开爆心方向）
			target.setFireTicks(60);
			Vec3d knock = new Vec3d(target.getX() - ix, 0.1, target.getZ() - iz).normalize().multiply(0.6);
			target.addVelocity(knock.x, knock.y, knock.z);
			target.velocityModified = true;
		}
		// 演出：爆炸粒子 + 火光 + 双层音效
		serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, ix, iy + 0.5, iz, 1, 0, 0, 0, 0);
		serverWorld.spawnParticles(ParticleTypes.FLAME, ix, iy + 0.5, iz, 40, (double) (radius * 0.6), 0.4, (double) (radius * 0.6), 0.15);
		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, ix, iy + 0.5, iz, 20, (double) (radius * 0.5), 0.3, (double) (radius * 0.5), 0.1);
		this.getWorld().playSound(null, ix, iy, iz,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.1f);
		this.getWorld().playSound(null, ix, iy, iz,
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.5f);
		this.discard();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return false; // 纯区域魔法载体：不可被攻击命中
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Damage")) {
			this.damage = nbt.getFloat("Damage");
		}
		if (nbt.contains("Radius")) {
			this.radius = nbt.getDouble("Radius");
		}
		if (nbt.contains("SpellLevel")) {
			setLevel(nbt.getInt("SpellLevel"));
		}
		if (nbt.contains("Falling")) {
			this.falling = nbt.getBoolean("Falling");
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("Damage", this.damage);
		nbt.putDouble("Radius", this.radius);
		nbt.putInt("SpellLevel", getSpellLevel());
		nbt.putBoolean("Falling", this.falling);
	}

	@Override
	public ItemStack getStack() {
		// 渲染用：小规模火焰弹物品（FlyingItemEntityRenderer 精灵，客户端不必知道真实伤害）
		return new ItemStack(Items.FIRE_CHARGE);
		// 陨火本体主要靠粒子拖尾 + 火焰弹精灵组合表现
	}
}
