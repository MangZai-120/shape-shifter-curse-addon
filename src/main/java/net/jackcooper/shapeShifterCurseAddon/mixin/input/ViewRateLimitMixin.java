package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.ability.FluorescentLaserAim;
import java.lang.ref.WeakReference;
import net.jackcooper.shapeShifterCurseAddon.client.FrostSpikeClient;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 荧光幼灵镜头使用法阵瞄准锥，寒棘狐凝棘仍使用惯性限速。 */
@Mixin(Entity.class)
public abstract class ViewRateLimitMixin {

	@Unique private static final double SSCA_MOUSE_DEGREES = 0.15f;
	@Unique private static final double FROSTSPINE_LIMIT = 50.0;
	@Unique private static WeakReference<ClientPlayerEntity> ssca$checkedPlayer = new WeakReference<>(null);
	@Unique private static int ssca$checkedAge = -1;
	@Unique private static int ssca$beamId = -1;

	// ===== 加速度参数 =====
	private static final double ACCEL_TIME_SEC = 0.5;   // 10 tick 加速到限速
	private static final double DECEL_TIME_SEC = 1.5;   // 1.5s 减速到 0

	// ===== 内部角速度状态（度/秒，yaw/pitch 分量）=====
	@Unique
	private static double ssca$velYaw = 0.0;
	@Unique
	private static double ssca$velPitch = 0.0;
	@Unique
	private static long ssca$lastNanos = 0L;

	@WrapMethod(method = "changeLookDirection")
	private void ssca$smoothView(double cursorDeltaX, double cursorDeltaY, Operation<Void> original) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || (Object)this != mc.player) {
			original.call(cursorDeltaX, cursorDeltaY);
			return;
		}
		ClientPlayerEntity player = mc.player;
		if (net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl.blocked(player)) {
			ssca$resetVelocity();
			return;
		}
		LaserBeamEntity laser = ssca$activeBeam(player);
		boolean frostForge = laser == null && FrostSpikeClient.isSecondaryCharging()
				&& FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_FROSTSPINE);
		if (!frostForge) {
			ssca$resetVelocity();
			if (laser != null) {
				Vec3d aim = laser.getBeamAim(mc.getTickDelta());
				if (aim.lengthSquared() > 0) {
					Vec2f look = FluorescentLaserAim.limitLook(aim, player.getYaw(), player.getPitch(),
							cursorDeltaX * SSCA_MOUSE_DEGREES, cursorDeltaY * SSCA_MOUSE_DEGREES);
					cursorDeltaX = (look.y - (double)player.getYaw()) / SSCA_MOUSE_DEGREES;
					cursorDeltaY = (look.x - (double)player.getPitch()) / SSCA_MOUSE_DEGREES;
				}
			}
			original.call(cursorDeltaX, cursorDeltaY);
			return;
		}

		double limit = FROSTSPINE_LIMIT;
		// 真实时间步长
		long now = System.nanoTime();
		if (ssca$lastNanos == 0L) ssca$lastNanos = now;
		double dt = Math.min(0.1, (now - ssca$lastNanos) / 1.0e9);
		ssca$lastNanos = now;

		// 鼠标输入方向（标准化）——只用方向，不用大小
		double inYaw = cursorDeltaX;
		double inPitch = cursorDeltaY;
		double inMag = Math.sqrt(inYaw * inYaw + inPitch * inPitch);
		boolean hasInput = inMag > 0.5;

		if (hasInput) {
			// 目标速度方向 = 输入方向，目标速度大小 = limit
			double dirYaw = inYaw / inMag;
			double dirPitch = inPitch / inMag;
			double targetVy = dirYaw * limit;
			double targetVp = dirPitch * limit;
			// 10 tick(0.5s) 线性加速到目标：加速度 = limit / ACCEL_TIME_SEC
			double accel = limit / ACCEL_TIME_SEC;
			double maxStep = accel * dt;
			ssca$velYaw = approach(ssca$velYaw, targetVy, maxStep);
			ssca$velPitch = approach(ssca$velPitch, targetVp, maxStep);
		} else {
			// 1.5s 线性减速到 0
			double decel = limit / DECEL_TIME_SEC;
			double maxStep = decel * dt;
			ssca$velYaw = approach(ssca$velYaw, 0.0, maxStep);
			ssca$velPitch = approach(ssca$velPitch, 0.0, maxStep);
		}

		// 限速上限钳制（叠加不超过 limit）
		double spd = Math.sqrt(ssca$velYaw * ssca$velYaw + ssca$velPitch * ssca$velPitch);
		if (spd > limit && spd > 1.0e-6) {
			double s = limit / spd;
			ssca$velYaw *= s;
			ssca$velPitch *= s;
		}

		// 用角速度推进视角
		double dYaw = ssca$velYaw * dt;
		double dPitch = ssca$velPitch * dt;
		if (Math.abs(dYaw) >= 1.0e-4 || Math.abs(dPitch) >= 1.0e-4)
			original.call(dYaw / SSCA_MOUSE_DEGREES, dPitch / SSCA_MOUSE_DEGREES);
	}

	@Unique
	private static void ssca$resetVelocity() {
		ssca$velYaw = ssca$velPitch = 0;
		ssca$lastNanos = 0;
	}

	/** Mouse input may arrive hundreds of times per tick. Cache the owner's normal beam. */
	@Unique
	private static LaserBeamEntity ssca$activeBeam(ClientPlayerEntity player) {
		if (ssca$checkedPlayer.get() == player) {
			if (ssca$beamId >= 0 && player.getWorld().getEntityById(ssca$beamId) instanceof LaserBeamEntity beam
					&& !beam.isRemoved() && !beam.isEnhanced() && beam.getTrackedOwnerId() == player.getId()) return beam;
			if (ssca$checkedAge == player.age) return null;
		} else {
			ssca$checkedPlayer = new WeakReference<>(player);
		}
		ssca$checkedAge = player.age;
		LaserBeamEntity beam = LaserBeamEntity.getActiveForClient(player);
		ssca$beamId = beam == null ? -1 : beam.getId();
		return beam;
	}

	/** 将 current 以每步最多 maxStep 的速度向 target 逼近（线性）。 */
	private static double approach(double current, double target, double maxStep) {
		double diff = target - current;
		if (Math.abs(diff) <= maxStep) return target;
		return current + Math.signum(diff) * maxStep;
	}
}
