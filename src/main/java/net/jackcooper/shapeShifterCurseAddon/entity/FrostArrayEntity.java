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

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) return; // 渲染由 FrostArrayRenderer 负责
		Entity owner = this.getWorld().getEntityById(getTrackedOwnerId());
		if (!(owner instanceof ServerPlayerEntity p) || p.isRemoved() || p.isDead()
				|| !FormUtils.isForm(p, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
			this.discard();
			return;
		}
		// 静态锚点：不逐 tick 跟随施法者（跟随会每 tick 发 move 包，~20包/秒纯浪费——
		// 渲染位置由客户端按施法者本地算 hoverTarget，实体位置仅作锚点/剔除基准；
		// 蓄力减速 90% 下 20s 最远 ~12 格，可见盒扩 16 格防远磨被视锥剔除）
		if (this.age > MAX_TICKS) this.discard(); // 双保险超时
	}

	@Override
	public Box getVisibilityBoundingBox() {
		// 静态锚点后玩家可能走离锚点：扩 16 格覆盖蓄力期最大移动距离，防法阵被整帧剔除
		return this.getBoundingBox().expand(16.0);
	}

	@Override protected void readCustomDataFromNbt(NbtCompound nbt) {}

	@Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}

	@Override public boolean isCollidable() { return false; }

	@Override public boolean canHit() { return false; }
}
