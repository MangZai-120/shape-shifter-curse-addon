package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;

/**
 * SP雪狐远程次要技能 - 冰风暴实体
 * 持续10秒，伤害半径3.5格，每秒2点魔法伤害
 * 吸附速度2b/s（6格内），6-10格吸附减弱
 */
public class FrostStormEntity extends Entity {

	private static final int DURATION = 200; // 10秒
	private static final double DAMAGE_RADIUS = 3.5;
	private static final double PULL_RADIUS_STRONG = 6.0;
	private static final double PULL_RADIUS_WEAK = 10.0;
	private static final float DAMAGE_PER_SECOND = 2.0f;
	private static final double PULL_SPEED = 0.1; // 2格/秒 = 0.1格/tick

	// 阶段 5：服务端权威快照读取（快照未初始化回退默认常量）
	private static final BalanceReader BAL = new BalanceReader("abilities.frost_storm");

    private int stormDuration = BAL.i("duration", DURATION);
    private double stormDamageRadius = BAL.d("damage_radius", DAMAGE_RADIUS);
    private double stormPullStrong = BAL.d("pull_radius_strong", PULL_RADIUS_STRONG);
    private double stormPullWeak = BAL.d("pull_radius_weak", PULL_RADIUS_WEAK);
    private double stormDamage = BAL.d("damage_per_second", DAMAGE_PER_SECOND);
    private double stormPullSpeed = BAL.d("pull_speed", PULL_SPEED);

	private int ticksAlive = 0;
	private UUID ownerUuid;
	/** 本次施放的 castId（修审查#3：旧风暴不能误伤新施放的记录；NBT 持久化）。 */
	private long boundCastId = 0;

	/** 绑定本次施放（生成时由 SnowFoxSpFrostStorm 调用）。 */
	public void bindCast(long castId) {
		this.boundCastId = castId;
        if (getWorld() instanceof ServerWorld sw) {
            net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.get(sw).bindEntity(castId, getUuid());
        }
	}

	public UUID getDecorationOwner() { return ownerUuid; }

	public FrostStormEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
		this.noClip = true;
	}

	public FrostStormEntity(World world, double x, double y, double z, PlayerEntity owner) {
		super(SscAddon.FROST_STORM_ENTITY, world);
		this.setPosition(x, y, z);
		this.ownerUuid = owner.getUuid();
		this.noClip = true;
	}

	@Override
	protected void initDataTracker() {
		// 暂时不需要初始化数据跟踪器
	}

	/** 风暴消失即冰风暴效果结束：凭 castId 精确结束本次施放，旧风暴不误伤新施放。 */
	private void settleOwnerEffectEnd() {
		if (this.getWorld().isClient || !(this.getWorld() instanceof ServerWorld sw) || ownerUuid == null) return;
        var manager = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.get(sw);
        var cast = manager.control(ownerUuid, net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpFrostStorm.SKILL_ID);
        if (cast != null && cast.castId == boundCastId && (cast.persistentEntity == null || getUuid().equals(cast.persistentEntity))) {
            manager.finish(boundCastId, sw.getServer().getOverworld().getTime());
        }
	}

	@Override
    public void remove(RemovalReason reason) {
        // 区块卸载也结算：风暴远离玩家后 CD 照常起算，不会因实体未加载而锁死技能
        if (!isRemoved() && reason.shouldDestroy()) settleOwnerEffectEnd();
        super.remove(reason);
    }

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		if (!this.getWorld().isClient && ticksAlive > stormDuration) {
			// 自然到期：结算（discard() 内统一处理，见下）
			this.discard();
			return;
		}

		if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
			// 同函数多次读取局部变量化
			int duration = stormDuration;
			double damageRadius = stormDamageRadius;

			// damage_per_second is one full hit per 20 server ticks, phased by persisted lifetime.
			if (ticksAlive % 20 == 0) {
				dealDamage(serverWorld);
			}

			// 每tick吸附敌人
			pullEntities();

			// 同步时间轴，雪花与旋转云由客户端按原密度生成。
			net.jackcooper.shapeShifterCurseAddon.network.SustainedVisuals.touch(this,
					net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind.FROST_STORM,
					ticksAlive, duration, damageRadius, 0);

			// 播放环境音效
			if (ticksAlive % 40 == 0) {
				this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.ENTITY_SNOW_GOLEM_AMBIENT, SoundCategory.HOSTILE, 0.5f, 0.5f);
			}
		}
	}

	private void dealDamage(ServerWorld world) {
		// 同函数多次读取局部变量化
		double damageRadius = stormDamageRadius;
		Box damageBox = new Box(
				this.getX() - damageRadius, this.getY() - 1, this.getZ() - damageRadius,
				this.getX() + damageRadius, this.getY() + 3, this.getZ() + damageRadius
		);

		List<LivingEntity> targets = world.getEntitiesByClass(
				LivingEntity.class, damageBox,
				entity -> entity.getUuid() != ownerUuid && entity.isAlive()
		);

		PlayerEntity owner = ownerUuid != null ? world.getPlayerByUuid(ownerUuid) : null;

		for (LivingEntity target : targets) {
			double dist = this.squaredDistanceTo(target.getX(), this.getY(), target.getZ());
			if (dist <= damageRadius * damageRadius) {
				if (WhitelistUtils.isProtected(ownerUuid, world, target)) continue;
				DamageSource source = owner != null
						? target.getDamageSources().playerAttack(owner)
						: target.getDamageSources().magic();
				target.damage(source, (float) stormDamage);
			}
		}
	}

	private void pullEntities() {
		// 同函数多次读取局部变量化
		double pullRadiusWeak = stormPullWeak;
		double pullRadiusStrong = stormPullStrong;
		double pullSpeed = stormPullSpeed;
		Box pullBox = new Box(
				this.getX() - pullRadiusWeak, this.getY() - 2, this.getZ() - pullRadiusWeak,
				this.getX() + pullRadiusWeak, this.getY() + 4, this.getZ() + pullRadiusWeak
		);

		List<LivingEntity> targets = this.getWorld().getEntitiesByClass(
				LivingEntity.class, pullBox,
				entity -> entity.getUuid() != ownerUuid && entity.isAlive()
		);

		Vec3d center = new Vec3d(this.getX(), this.getY(), this.getZ());
		ServerWorld pullWorld = this.getWorld() instanceof ServerWorld sw ? sw : null;

		for (LivingEntity target : targets) {
			Vec3d targetPos = target.getPos();
			double dist = Math.sqrt(target.squaredDistanceTo(this.getX(), this.getY(), this.getZ()));

			if (dist > pullRadiusWeak || dist < 0.5) continue;
			if (pullWorld != null && WhitelistUtils.isProtected(ownerUuid, pullWorld, target)) continue;

			// 计算吸附速度
			double pullStrength;
			if (dist <= pullRadiusStrong) {
				pullStrength = pullSpeed; // 正常吸附速度
			} else {
				// 6-10格，吸附减弱
				double factor = 1.0 - ((dist - pullRadiusStrong) / (pullRadiusWeak - pullRadiusStrong));
				pullStrength = pullSpeed * factor * 0.3; // 骤减吸附
			}

			// 计算吸附方向
			Vec3d direction = center.subtract(targetPos).normalize();
			Vec3d pullVelocity = direction.multiply(pullStrength);

			// 应用吸附
			Vec3d newVelocity = target.getVelocity().add(pullVelocity);
			target.setVelocity(newVelocity);
			target.velocityModified = true;
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Balance", 10)) {
            NbtCompound values = nbt.getCompound("Balance");
            if (values.contains("duration")) stormDuration = values.getInt("duration");
            if (values.contains("damage_radius")) stormDamageRadius = values.getDouble("damage_radius");
            if (values.contains("pull_radius_strong")) stormPullStrong = values.getDouble("pull_radius_strong");
            if (values.contains("pull_radius_weak")) stormPullWeak = values.getDouble("pull_radius_weak");
            if (values.contains("damage_per_second")) stormDamage = values.getDouble("damage_per_second");
            if (values.contains("pull_speed")) stormPullSpeed = values.getDouble("pull_speed");
        }
		this.ticksAlive = nbt.getInt("TicksAlive");
		if (nbt.containsUuid("Owner")) {
			this.ownerUuid = nbt.getUuid("Owner");
		}
		this.boundCastId = nbt.getLong("BoundCastId");
        if (boundCastId > 0 && getWorld() instanceof ServerWorld sw) {
            var manager = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.get(sw);
            var cast = manager.control(ownerUuid, net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpFrostStorm.SKILL_ID);
            if (cast != null && cast.castId == boundCastId) manager.bindEntity(boundCastId, getUuid());
        }
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
        NbtCompound values = new NbtCompound();
        values.putInt("duration", stormDuration);
        values.putDouble("damage_radius", stormDamageRadius);
        values.putDouble("pull_radius_strong", stormPullStrong);
        values.putDouble("pull_radius_weak", stormPullWeak);
        values.putDouble("damage_per_second", stormDamage);
        values.putDouble("pull_speed", stormPullSpeed);
        nbt.put("Balance", values);
		nbt.putInt("TicksAlive", this.ticksAlive);
		nbt.putLong("BoundCastId", this.boundCastId);
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return super.createSpawnPacket();
	}

	@Override
	public boolean isCollidable() {
		return super.isCollidable();
	}
}
