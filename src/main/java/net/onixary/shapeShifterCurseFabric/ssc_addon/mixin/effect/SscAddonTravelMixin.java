package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.effect;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.AdaptiveSwimmingPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntity.class)
public abstract class SscAddonTravelMixin {

	@Shadow
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

		if (!entity.isTouchingWater() || !(entity instanceof PlayerEntity player)) return;

		// Check for power
		PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
		List<AdaptiveSwimmingPower> powers = component.getPowers(AdaptiveSwimmingPower.class);
		if (powers.isEmpty()) return;

		AdaptiveSwimmingPower power = powers.get(0); // Use first active
		if (!power.isActive()) return;

		// Implementation of 3D swimming with inertia

		// Check Inputs for Vertical Intent
		// movementInput.y is usually 0 in survival, so we check jump/sneak flags explicitly
		double verticalInput = movementInput.y;
		if (Math.abs(verticalInput) < 1.0E-5) {
			if (this.jumping) verticalInput += 1.0; // Space
			if (player.isSneaking()) verticalInput -= 1.0; // Shift
		}

		Vec3d lookVec = entity.getRotationVec(1.0F);
		// Total desired vertical drive = Direct Up/Down keys + Looking Up/Down while moving Forward/Back
		double verticalDrive = verticalInput + (lookVec.y * movementInput.z);

		// 1. Friction / Inertia
		Vec3d velocity = entity.getVelocity();

		float friction = power.getFriction();
		float horizontalFriction = friction;

		// If applying vertical force, reduce horizontal friction to preserve horizontal momentum
		if (Math.abs(verticalDrive) > 0.1) {
			horizontalFriction = friction + (1.0f - friction) * 0.8f;
		}

		velocity = new Vec3d(
				velocity.x * horizontalFriction,
				velocity.y * friction,
				velocity.z * horizontalFriction
		);

		// 2. Acceleration based on Input
		if (movementInput.lengthSquared() > 1.0E-7D || Math.abs(verticalInput) > 1.0E-5) {

			// Separate Horizontal and Vertical logic to preserve horizontal momentum when looking up/down

			// A. Horizontal Logic (Yaw based)
			float yaw = entity.getYaw() * 0.017453292F;
			Vec3d horizontalForward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
			Vec3d horizontalRight = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw)); // Right is Forward rotated -90 deg (or cross up)

			Vec3d horizontalDriveVec = new Vec3d(0, 0, 0);
			horizontalDriveVec = horizontalDriveVec.add(horizontalForward.multiply(movementInput.z));
			horizontalDriveVec = horizontalDriveVec.add(horizontalRight.multiply(movementInput.x));

			if (horizontalDriveVec.lengthSquared() > 0) {
				// Normalize horizontal input to ensure standard acceleration speed regardless of strafe+forward combo
				// And apply full acceleration (user requested: pitch shouldn't reduce horizontal speed)
				horizontalDriveVec = horizontalDriveVec.normalize().multiply(power.getAcceleration());
				velocity = velocity.add(horizontalDriveVec);
			}

			// B. Vertical Logic
			if (Math.abs(verticalDrive) > 0) {
				float verticalAccel = power.getAcceleration();
				// If not sprinting, reduce vertical acceleration significantly for better control
				if (!entity.isSprinting()) {
					verticalAccel *= 0.25f;
				}
				velocity = velocity.add(0, verticalDrive * verticalAccel, 0);
			}
		}

		// 3. Max Vertical Speed Cap (Non-sprinting only)
		if (!entity.isSprinting()) {
			double maxVerticalSpeed = 0.25; // Cap normal vertical speed
			if (Math.abs(velocity.y) > maxVerticalSpeed) {
				velocity = new Vec3d(velocity.x, Math.signum(velocity.y) * maxVerticalSpeed, velocity.z);
			}
		}

		entity.setVelocity(velocity);
		entity.move(MovementType.SELF, velocity);

		// Cancel vanilla travel to prevent gravity and default water physics
		ci.cancel();
	}
}
