package net.jackcooper.shapeShifterCurseAddon.mixin.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SscAddonTravelMixin {

	// aliases 兼容 intermediary(field_6362)/yarn(jumping) 运行时映射，防「jumping was not located」崩溃
	@Shadow(aliases = {"field_6362", "jumping"})
	protected boolean jumping;

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void adaptiveSwimming(Vec3d movementInput, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;

		// 幽雾化形飞行（悦灵同款）：仅客户端本地玩家接管移动，服务端靠位置同步 + setNoGravity 豁免悬空踢人。
		// WASD 控制水平移动并严格锁速 4.5 格/s（0.225 格/tick）；跳跃键上浮、潜行键下降、无输入缓降。
		if (entity.getWorld().isClient && entity instanceof PlayerEntity mistPlayer
				&& entity.hasStatusEffect(SscAddon.MIST_FORM)) {
			// 凝聚爆破蓄力期间整体减速 50%（带 MIST_CHARGING 标记时）
			double maxH = entity.hasStatusEffect(SscAddon.MIST_CHARGING) ? 0.1125 : 0.225; // 0.225 格/tick = 4.5 格/s

			// 水平：按朝向(yaw)将 WASD 输入转为世界方向，归一化后严格锁定为 maxH（前后左右斜向同速）
			float yaw = entity.getYaw() * 0.017453292F;
			Vec3d fwd = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
			Vec3d right = new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw));
			Vec3d horiz = fwd.multiply(movementInput.z).add(right.multiply(movementInput.x));
			if (horiz.lengthSquared() > 1.0E-6) {
				horiz = horiz.normalize().multiply(maxH);
			} else {
				horiz = Vec3d.ZERO;
			}

			// 垂直：跳跃键上浮，潜行键下降，否则缓降（营造悦灵漂浮下沉感）
			double vy;
			if (this.jumping) {
				vy = maxH;
			} else if (mistPlayer.isSneaking()) {
				vy = -maxH;
			} else {
				vy = Math.max(entity.getVelocity().y - 0.02, -0.10);
			}

			Vec3d v = new Vec3d(horiz.x, vy, horiz.z);
			entity.setVelocity(v);
			entity.move(MovementType.SELF, v);
			entity.fallDistance = 0f;
			ci.cancel();
			return;
		}
	}
}
