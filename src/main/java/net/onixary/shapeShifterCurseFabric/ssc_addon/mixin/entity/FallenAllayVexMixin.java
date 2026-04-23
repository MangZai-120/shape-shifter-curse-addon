package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(VexEntity.class)
public abstract class FallenAllayVexMixin extends MobEntity {

	// Persistent target lock (vex UUID -> target entity UUID)
	@Unique
	private static final ConcurrentHashMap<UUID, UUID> VEX_TARGET = new ConcurrentHashMap<>();
	// Idle wander destination when no target (vex UUID -> [x, y, z])
	@Unique
	private static final ConcurrentHashMap<UUID, double[]> VEX_WANDER_DEST = new ConcurrentHashMap<>();
	// Ticks until a new wander point is picked
	@Unique
	private static final ConcurrentHashMap<UUID, Integer> VEX_WANDER_TIMER = new ConcurrentHashMap<>();

	protected FallenAllayVexMixin(EntityType<? extends MobEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void ssc_addon$onVexTick(CallbackInfo ci) {
		if (this.getWorld().isClient()) return;

		Set<String> tags = this.getCommandTags();
		String ownerUuidStr = null;
		boolean isFallenVex = false;

		for (String tag : tags) {
			if (tag.equals("ssc_fallen_allay_vex")) {
				isFallenVex = true;
			} else if (tag.startsWith("owner:")) {
				ownerUuidStr = tag.substring("owner:".length());
			}
		}

		if (!isFallenVex || ownerUuidStr == null) return;

		ServerWorld serverWorld = (ServerWorld) this.getWorld();
		PlayerEntity owner = serverWorld.getServer().getPlayerManager().getPlayer(UUID.fromString(ownerUuidStr));

		// On death: clean up all maps, start CD if last vex
		if (this.isDead() || this.getHealth() <= 0 || !this.isAlive()) {
			VEX_TARGET.remove(this.getUuid());
			VEX_WANDER_DEST.remove(this.getUuid());
			VEX_WANDER_TIMER.remove(this.getUuid());
			if (owner != null) {
				applyCooldownIfLast(owner, ownerUuidStr, serverWorld);
			}
			return;
		}

		if (owner == null) return;

		pinVexCd(owner);

		boolean hasWhitelist = owner.getCommandTags().stream()
				.anyMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

		// Validate stored target; clear if dead/gone
		LivingEntity currentTarget = resolveTarget(serverWorld);

		// After a kill (or on first spawn), search around the VEX ITSELF for a new target
		if (currentTarget == null) {
			currentTarget = findBestTarget(owner, ownerUuidStr, serverWorld, hasWhitelist);
			if (currentTarget != null) {
				VEX_TARGET.put(this.getUuid(), currentTarget.getUuid());
			}
		}

		if (currentTarget != null) {
			// Has a target: hand off to vanilla ChargeTargetGoal — no position restriction
			this.setTarget(currentTarget);
		} else {
			// No target: manually wander within 10-block sphere around owner
			this.setTarget(null);
			wanderNearOwner(owner);
		}
	}

	/**
	 * Wander within a 10-block sphere around owner.
	 * Picks a new random destination every 40 ticks (or when the current one is reached).
	 * If already beyond 10 blocks, fly back toward owner instead.
	 */
	@Unique
	private void wanderNearOwner(PlayerEntity owner) {
		UUID id = this.getUuid();
		double dist2ToOwner = this.squaredDistanceTo(owner);

		// If outside the sphere, fly back toward owner
		if (dist2ToOwner > 100.0) {
			net.minecraft.util.math.Vec3d toOwner = owner.getPos().add(0, 1.0, 0).subtract(this.getPos());
			this.setVelocity(toOwner.normalize().multiply(Math.min(0.35, 0.1 + dist2ToOwner * 0.003)));
			VEX_WANDER_DEST.remove(id);
			VEX_WANDER_TIMER.put(id, 0);
			return;
		}

		// Count down to next destination pick
		int timer = VEX_WANDER_TIMER.getOrDefault(id, 0) - 1;
		VEX_WANDER_TIMER.put(id, timer);

		double[] dest = VEX_WANDER_DEST.get(id);
		boolean needNew = dest == null || timer <= 0
				|| this.squaredDistanceTo(dest[0], dest[1], dest[2]) < 1.0;

		if (needNew) {
			// Pick a random point inside the 10-block sphere around owner
			java.util.Random rand = new java.util.Random();
			double ox, oy, oz;
			do {
				ox = (rand.nextDouble() * 2 - 1) * 10.0;
				oy = (rand.nextDouble() * 2 - 1) * 10.0;
				oz = (rand.nextDouble() * 2 - 1) * 10.0;
			} while (ox * ox + oy * oy + oz * oz > 100.0); // reject points outside sphere
			dest = new double[]{owner.getX() + ox, owner.getY() + oy, owner.getZ() + oz};
			VEX_WANDER_DEST.put(id, dest);
			VEX_WANDER_TIMER.put(id, 40 + rand.nextInt(20));
		}

		// Fly toward the chosen wander point at casual speed
		net.minecraft.util.math.Vec3d delta = new net.minecraft.util.math.Vec3d(
				dest[0] - this.getX(), dest[1] - this.getY(), dest[2] - this.getZ());
		double d2 = delta.lengthSquared();
		if (d2 > 0.25) {
			this.setVelocity(delta.normalize().multiply(Math.min(0.3, 0.1 + d2 * 0.008)));
		}
	}

	/**
	 * Resolve the stored target; clear slot if dead or absent.
	 */
	@Unique
	private LivingEntity resolveTarget(ServerWorld serverWorld) {
		UUID targetId = VEX_TARGET.get(this.getUuid());
		if (targetId == null) return null;
		Entity e = serverWorld.getEntity(targetId);
		if (e instanceof LivingEntity le && le.isAlive()) return le;
		VEX_TARGET.remove(this.getUuid());
		return null;
	}

	/**
	 * Find the best target centered on the VEX ITSELF (radius 16).
	 * Priority: marked (glowing) > player > hostile > other
	 */
	@Unique
	private LivingEntity findBestTarget(PlayerEntity owner, String ownerUuidStr,
	                                    ServerWorld serverWorld, boolean hasWhitelist) {
		Box searchBox = this.getBoundingBox().expand(16.0);
		List<LivingEntity> candidates = serverWorld.getEntitiesByClass(LivingEntity.class, searchBox,
				e -> e != owner && e != this && e.isAlive()
						&& !(e instanceof VexEntity)
						&& !(e instanceof RaiderEntity));

		LivingEntity markedTarget = null;
		LivingEntity playerTarget = null;
		LivingEntity hostileTarget = null;
		LivingEntity otherTarget = null;

		boolean ownerIsServerPlayer = owner instanceof net.minecraft.server.network.ServerPlayerEntity;
		net.minecraft.server.network.ServerPlayerEntity serverOwner = ownerIsServerPlayer
				? (net.minecraft.server.network.ServerPlayerEntity) owner : null;

		for (LivingEntity e : candidates) {
			// 始终跳过自己的驯服动物
			if (e instanceof net.minecraft.entity.passive.TameableEntity tameable
					&& owner.getUuid().equals(tameable.getOwnerUuid())) {
				continue;
			}
			// 始终跳过自己的恕魔（候选列表已过滤 VexEntity，但保留保险）
			if (e instanceof VexEntity vex
					&& vex.getCommandTags().contains("owner:" + ownerUuidStr)) {
				continue;
			}
			// 统一白名单判定：受服务端总开关控制
			if (serverOwner != null
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isProtected(serverOwner, e)) {
				continue;
			}

			if (e.hasStatusEffect(StatusEffects.GLOWING)) {
				if (markedTarget == null) markedTarget = e;
			} else if (e instanceof PlayerEntity) {
				if (playerTarget == null) playerTarget = e;
			} else if (e instanceof HostileEntity) {
				if (hostileTarget == null) hostileTarget = e;
			} else {
				if (otherTarget == null) otherTarget = e;
			}
		}

		return markedTarget != null ? markedTarget :
				playerTarget != null ? playerTarget :
						hostileTarget != null ? hostileTarget :
								otherTarget;
	}

	/**
	 * While at least one vex is alive, keep vex_cd pinned at 400 so the skill can't be recast.
	 */
	@Unique
	private void pinVexCd(PlayerEntity owner) {
		try {
			io.github.apace100.apoli.component.PowerHolderComponent component = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(owner);
			io.github.apace100.apoli.power.PowerType<?> powerType = io.github.apace100.apoli.power.PowerTypeRegistry.get(new net.minecraft.util.Identifier("my_addon", "form_fallen_allay_sp_vex_cd"));
			io.github.apace100.apoli.power.Power power = component.getPower(powerType);
			if (power instanceof io.github.apace100.apoli.power.VariableIntPower vip && vip.getValue() < 400) {
				vip.setValue(400);
				io.github.apace100.apoli.component.PowerHolderComponent.syncPower(owner, power.getType());
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * When the last vex dies, set vex_cd to 400 and let the JSON ticker count it down.
	 */
	@Unique
	private void applyCooldownIfLast(PlayerEntity owner, String ownerUuidStr, ServerWorld serverWorld) {
		boolean hasOtherVex = false;
		for (Entity v : serverWorld.getEntitiesByClass(VexEntity.class, owner.getBoundingBox().expand(128.0),
				e -> e != (Object) this && e.isAlive())) {
			if (v.getCommandTags().contains("owner:" + ownerUuidStr) && v.getCommandTags().contains("ssc_fallen_allay_vex")) {
				hasOtherVex = true;
				break;
			}
		}
		if (!hasOtherVex) {
			try {
				io.github.apace100.apoli.component.PowerHolderComponent component = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(owner);
				io.github.apace100.apoli.power.PowerType<?> powerType = io.github.apace100.apoli.power.PowerTypeRegistry.get(new net.minecraft.util.Identifier("my_addon", "form_fallen_allay_sp_vex_cd"));
				io.github.apace100.apoli.power.Power power = component.getPower(powerType);
				if (power instanceof io.github.apace100.apoli.power.VariableIntPower vip) {
					vip.setValue(400);
					io.github.apace100.apoli.component.PowerHolderComponent.syncPower(owner, power.getType());
				}
			} catch (Exception ignored) {
			}
		}
	}
}

