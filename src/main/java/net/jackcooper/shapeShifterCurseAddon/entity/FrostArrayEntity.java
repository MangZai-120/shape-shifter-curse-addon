package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * 寒棘狐「凝棘」蓄力法阵实体（纯视觉，无碰撞无伤害）。
 *
 * <p>蓄力期间跟随施法者眼部；渲染器 {@code FrostArrayRenderer} 按施法者准星在眼前 0.2 格画青蓝雪花法阵，
 * 并在法阵前 0.08 格中央画一根随蓄力等级放大的冰锥。由 {@link net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager}
 * 蓄力开始 spawn、蓄力结束 / 发射时 discard；{@value #MAX_TICKS} tick 超时为双保险。</p>
 *
 * <p>生命周期全在服务端，走 EntityTracker 天然多人同步（所有人可见施法者面前的法阵）。
 * 蓄力等级 {@code LEVEL} 用 DataTracker 同步，供渲染器决定中央冰锥大小。</p>
 */
public class FrostArrayEntity extends Entity {

	private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(FrostArrayEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LEVEL = DataTracker.registerData(FrostArrayEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final int MAX_TICKS = 400; // 20s 超时双保险（正常由管理器 discard）

	public FrostArrayEntity(EntityType<? extends FrostArrayEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public FrostArrayEntity(World world, PlayerEntity owner) {
		this(SscAddon.FROST_ARRAY_ENTITY, world);
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(OWNER_ID, -1);
		this.dataTracker.startTracking(LEVEL, 0);
	}

	/** 施法者实体 id（客户端渲染器据此取施法者准星算法阵位置）。 */
	public int getTrackedOwnerId() { return this.dataTracker.get(OWNER_ID); }

	public int getLevel() { return this.dataTracker.get(LEVEL); }

	public void setLevel(int level) { if (getLevel() != level) this.dataTracker.set(LEVEL, level); }

	/** 上一次读到的等级（客户端检测 LEVEL 跳变 = 服务端刚消耗一根冰锥 → 播 burst 密集汇聚）。 */
	private int clientPrevLevel = -1;

	@Override
	public void tick() {
		super.tick();
		// 客户端：次技能蓄力粒子本地自算（零网络包）——中心 = 头顶法阵中心（与服务端 secondaryFocus 严格同点）。
		// 服务端原逻辑：按下/每 4t 持续汇聚 + 每秒消耗时 24 颗 burst；此处等价复现（burst 由 LEVEL 跳变锚定，天然同步）。
		if (this.getWorld().isClient) {
			Entity ownerC = this.getWorld().getEntityById(getTrackedOwnerId());
			if (ownerC instanceof PlayerEntity p && FormUtils.isForm(p, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
				Vec3d center = FrostThornEntity.hoverTarget(p, 0);
				// 每秒消耗 burst：LEVEL 跳变（+1）= 刚消耗一根 → 密集波（24 颗，与服务端原版同密度）
				int lv = getLevel();
				if (clientPrevLevel >= 0 && lv > clientPrevLevel) {
					spawnInwardIceClient(center, 24);
				}
				clientPrevLevel = lv;
				// 持续汇聚（每 4t 一波 18 颗；服务端原条件 countThorns>0 的近似：等级未满 5 即还有冰锥可消耗）
				if (lv < 5 && this.age % 4 == 0) {
					spawnInwardIceClient(center, 18);
				}
			}
			return;
		}
		Entity owner = this.getWorld().getEntityById(getTrackedOwnerId());
		if (!(owner instanceof ServerPlayerEntity p) || p.isRemoved() || p.isDead()
				|| !FormUtils.isForm(p, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
			this.discard();
			return;
		}
		// 跟随施法者眼部（法阵前方位置由渲染器按准星实时算）
		this.setPosition(p.getX(), p.getEyeY(), p.getZ());
		if (this.age > MAX_TICKS) this.discard(); // 双保险超时
	}

	@Override
	public Box getVisibilityBoundingBox() {
		// 法阵在眼前 0.2 格 + 中央冰锥延伸出锚点，扩大可见盒避免视锥剔除整帧剔除
		return this.getBoundingBox().expand(4.0);
	}

	@Override protected void readCustomDataFromNbt(NbtCompound nbt) {}

	@Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override public boolean isCollidable() { return false; }

	@Override public boolean canHit() { return false; }

	/** 客户端本地生成向内汇聚冰晶（与服务端原 spawnInwardIceParticles 同几何：球面均匀、初速 1格/20t 向心）。 */
	private void spawnInwardIceClient(Vec3d center, int count) {
		for (int i = 0; i < count; i++) {
			double u = this.random.nextDouble() * 2 - 1;
			double theta = this.random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(1 - u * u);
			double dx = r * Math.cos(theta), dy = u, dz = r * Math.sin(theta);
			double speed = 1.0 / 20.0;
			this.getWorld().addParticle(SscAddon.INWARD_ICE_PARTICLE,
					center.x + dx, center.y + dy, center.z + dz,
					-dx * speed, -dy * speed, -dz * speed);
		}
	}
}
