package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class DomainNetworkBoundaryMixin {
	@Shadow public ServerPlayerEntity player;
	@Shadow private Vec3d requestedTeleportPos;
	@Shadow private Entity topmostRiddenEntity;
	@Shadow public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch);

	@Inject(method = "onPlayerMove", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;isMovementInvalid(DDDFF)Z"), cancellable = true)
	private void ssca$domainWalk(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		if (requestedTeleportPos != null || player.notInAnyWorld || player.hasVehicle()) return;
		Vec3d next = new Vec3d(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
		if (!Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)
				|| !Float.isFinite(packet.getYaw(player.getYaw())) || !Float.isFinite(packet.getPitch(player.getPitch()))) return;
		if (DomainManager.blocksTeleport(player, player.getWorld(), next)) {
			// 上抬候选点也必须留在边界内；无安全落点时纠正回原位，确保纠正包能发出。
			Vec3d clamped = DomainManager.liftOutOfBlocks(player, DomainManager.clampToBoundary(player, next));
			requestTeleport(clamped.x, clamped.y, clamped.z, packet.getYaw(player.getYaw()), packet.getPitch(player.getPitch()));
			ci.cancel();
		}
	}

	@Inject(method = "onVehicleMove", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;isMovementInvalid(DDDFF)Z"), cancellable = true)
	private void ssca$domainRide(VehicleMoveC2SPacket packet, CallbackInfo ci) {
		var vehicle = player.getRootVehicle();
		if (vehicle == player || vehicle != topmostRiddenEntity || vehicle.getControllingPassenger() != player) return;
		Vec3d next = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
		if (!Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)
				|| !Float.isFinite(packet.getYaw()) || !Float.isFinite(packet.getPitch())) return;
		if (DomainManager.blocksTeleport(vehicle, player.getWorld(), next)) {
			// 载具同款钳制 + 方块安全化（防陷边角方块/地底）
			Vec3d clamped = DomainManager.liftOutOfBlocks(vehicle, DomainManager.clampToBoundary(vehicle, next));
			vehicle.requestTeleport(clamped.x, clamped.y, clamped.z);
			player.networkHandler.sendPacket(new VehicleMoveS2CPacket(vehicle));
			ci.cancel();
		}
	}

	@Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), cancellable = true)
	private void ssca$domainTeleport(double x, double y, double z, float yaw, float pitch,
	                                 java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags, CallbackInfo ci) {
		if (DomainManager.blocksTeleport(player, player.getWorld(), new Vec3d(x, y, z))) ci.cancel();
	}
}
