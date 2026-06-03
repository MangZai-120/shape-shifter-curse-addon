package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class AllayFriendMarkerEntity extends ThrownItemEntity {

	private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-FriendMarker");

	public AllayFriendMarkerEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
		super(entityType, world);
	}

	public AllayFriendMarkerEntity(World world, LivingEntity owner) {
		super(SscAddon.FRIEND_MARKER_ENTITY_TYPE, owner, world);
	}

	public AllayFriendMarkerEntity(World world, double x, double y, double z) {
		super(SscAddon.FRIEND_MARKER_ENTITY_TYPE, x, y, z, world);
	}

	@Override
	protected Item getDefaultItem() {
		return SscAddon.FRIEND_MARKER;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.age > 40) {
			this.discard();
		}
	}

	@Override
	public void handleStatus(byte status) {
		if (status == 3) {
			for (int i = 0; i < 8; ++i) {
				this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, this.getStack()),
						this.getX(), this.getY(), this.getZ(),
						((double) this.random.nextFloat() - 0.5D) * 0.08D,
						((double) this.random.nextFloat() - 0.5D) * 0.08D,
						((double) this.random.nextFloat() - 0.5D) * 0.08D);
			}
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		if (this.getWorld().isClient) return;
		Entity entity = entityHitResult.getEntity();
		if (!(entity instanceof LivingEntity target)) return;

		// 解析投掷者为「当前活跃的服务端玩家实例」：
		// getOwner() 在多人 / 客机环境下可能返回过期或非活跃的玩家镜像，
		// 导致 addToWhitelist 写到了错误对象、活跃玩家的 commandTags 仍为空（客机白名单失效的根因之一）。
		ServerPlayerEntity writeTarget = resolveActiveOwner();
		if (writeTarget == null) {
			LOGGER.warn("[FriendMarker] 标记失败：无法解析投掷者活跃实例 owner={}",
					this.getOwner() == null ? "null" : this.getOwner().getClass().getSimpleName());
			return;
		}

		// 加入白名单（vex 不攻击、尖吓不标记）+ ssc_raid_friend（袭击生物不攻击目标）
		AllaySPGroupHeal.addToWhitelist(writeTarget, target);
		target.addCommandTag("ssc_raid_friend");

		long wlCount = writeTarget.getCommandTags().stream()
				.filter(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX)).count();
		LOGGER.info("[FriendMarker] {} 标记成功，当前白名单条目数={}",
				writeTarget.getName().getString(), wlCount);

		// 立即把最新白名单推送到该玩家客户端，确保 GUI 与服务端 commandTags 实时一致（修复多人下 GUI 缓存与服务端不同步）
		SscAddonNetworking.sendWhitelistSync(writeTarget);
	}

	/** 通过 ownerUuid 从 PlayerManager 取当前活跃服务端玩家实例，避免把白名单写到过期 / 重复镜像上。 */
	private ServerPlayerEntity resolveActiveOwner() {
		Entity owner = this.getOwner();
		UUID uuid = (owner instanceof ServerPlayerEntity sp) ? sp.getUuid() : null;
		if (uuid == null) return null;
		if (this.getServer() != null) {
			ServerPlayerEntity active = this.getServer().getPlayerManager().getPlayer(uuid);
			if (active != null) return active;
		}
		return (owner instanceof ServerPlayerEntity sp2) ? sp2 : null;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			this.getWorld().sendEntityStatus(this, (byte) 3);
			this.discard();
		}
	}
}
