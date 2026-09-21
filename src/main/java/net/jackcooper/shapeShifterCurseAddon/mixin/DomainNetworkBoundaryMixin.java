package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
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
	@Shadow public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch);

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void ssca$domainWalk(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		Vec3d next = new Vec3d(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
		if (DomainManager.blocksTeleport(player, player.getWorld(), next)) {
			// 越界钳制墙（2026-09-22 反馈修正版②）：注入点必须在 HEAD——反编译确认 onPlayerMove
			// 开头有 awaitingTeleport 免检分支（requestedTeleportPos 匹配即 return，跳过一切校验），
			// 我们每次 clamp 后 requestTeleport 开的确认窗口会被客户端越界包逐个"确认"，形成穿壳通道。
			// HEAD 先于免检分支执行，领域校验无法被绕过；贴墙坐标经 clampToBoundary 计算。
			Vec3d clamped = DomainManager.clampToBoundary(player, next);
			requestTeleport(clamped.x, clamped.y, clamped.z, packet.getYaw(player.getYaw()), packet.getPitch(player.getPitch()));
			ci.cancel();
		}
	}

	@Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
	private void ssca$domainRide(VehicleMoveC2SPacket packet, CallbackInfo ci) {
		var vehicle = player.getRootVehicle();
		Vec3d next = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
		if (DomainManager.blocksTeleport(vehicle, player.getWorld(), next)) {
			// 载具同款钳制（HEAD 先于免检分支，防穿壳；服务器权威旧位置为起点）
			Vec3d clamped = DomainManager.clampToBoundary(vehicle, next);
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