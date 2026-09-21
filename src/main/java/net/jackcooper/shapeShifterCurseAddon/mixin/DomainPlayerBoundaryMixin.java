package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class DomainPlayerBoundaryMixin {
	@Shadow protected abstract TeleportTarget getTeleportTarget(ServerWorld destination);

	@Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"), cancellable = true)
	private void ssca$domainTeleport(ServerWorld world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
		if (DomainManager.blocksTeleport((Entity) (Object) this, world, new Vec3d(x, y, z))) ci.cancel();
	}

	@Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
	private void ssca$domainTeleportFlags(ServerWorld world, double x, double y, double z,
	                                      java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags,
	                                      float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
		if (DomainManager.blocksTeleport((Entity) (Object) this, world, new Vec3d(x, y, z))) cir.setReturnValue(false);
	}

	@Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
	private void ssca$domainDimension(ServerWorld world, CallbackInfoReturnable<Entity> cir) {
		Entity entity = (Entity) (Object) this;
		if (DomainManager.enclosed(entity.getWorld(), entity.getPos())) { cir.setReturnValue(null); return; }
		if (!DomainManager.hasActive(world)) return;
		TeleportTarget target = getTeleportTarget(world);
		if (target != null && DomainManager.blocksTeleport(entity, world, target.position)) cir.setReturnValue(null);
	}
}