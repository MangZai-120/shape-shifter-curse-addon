package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.jackcooper.shapeShifterCurseAddon.client.FrostSpikeClient;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 荧光幼灵法阵激光期间的视角控制（<b>带加速度的平滑限速</b>，与鼠标灵敏度/DPI 无关）。
 *
 * <p>模型：维护一个「当前角速度向量」，每帧根据「是否有鼠标输入」加速/减速：
 * <ul>
 *   <li>有输入：在 10 tick(0.5s) 内从 0 线性加速到当前限速（默认 25°/s）；多次快速移动叠加加速度，但速度模长不超过限速。</li>
 *   <li>无输入：在 1.5s 内线性减速到 0。</li>
 *   <li>限速曲线：蓄力 0~5s=25°/s，5~7s 线性递减到 5°/s，释放/消退=5°/s。</li>
 * </ul>
 * 鼠标原始位移只用于「判断输入方向」，不直接驱动视角；视角由内部角速度逐帧推进，保证流畅线性。
 * 仅在本地玩家有活跃激光实体时限速。
 */
@Mixin(Entity.class)
public abstract class ViewRateLimitMixin {

	@Shadow
	public float prevYaw;
	@Shadow
	public float prevPitch;

	// ===== 限速曲线（度/秒）=====
	private static final double RATE_START = 25.0;
	private static final double RATE_END = 5.0;
	// 寒棘狐凝棘蓄力：视角限速同荧光幼灵机制，最大角速度 ×2（荧光幼灵基础 25 → 50°/s）
	private static final double FROSTSPINE_LIMIT = 50.0;
	private static final int CHARGE_5S_TICK = 100;
	private static final int CHARGE_7S_TICK = 140;

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

	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
	private void ssca$smoothView(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || self != mc.player) return;
		ClientPlayerEntity player = mc.player;
		LaserBeamEntity laser = LaserBeamEntity.getActiveForClient(player);
		// 寒棘狐凝棘（次技能）蓄力期同样平滑限速（角速度 ×2）
		boolean frostForge = laser == null && FrostSpikeClient.isSecondaryCharging()
				&& FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_FROSTSPINE);
		if (laser == null && !frostForge) {
			// 无激光 / 无凝棘蓄力（含海晶荧光坮增强激光——已在 getActiveForClient 排除，不限视角）：重置状态，放行原版
			ssca$velYaw = 0.0;
			ssca$velPitch = 0.0;
			ssca$lastNanos = 0L;
			return;
		}

		// 当前限速：激光走相位曲线；凝棘蓄力用固定 ×2 限速
		double limit = frostForge ? FROSTSPINE_LIMIT : currentLimit(laser);

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
		if (Math.abs(dYaw) < 1.0e-4 && Math.abs(dPitch) < 1.0e-4) {
			ci.cancel();
			return;
		}
		player.setYaw(player.getYaw() + (float) dYaw);
		player.setPitch(MathHelper.clamp(player.getPitch() + (float) dPitch, -90.0F, 90.0F));
		this.prevYaw += (float) dYaw;
		this.prevPitch = MathHelper.clamp(this.prevPitch + (float) dPitch, -90.0F, 90.0F);
		ci.cancel();
	}

	/** 当前限速（度/秒），按激光阶段/时间计算。 */
	private static double currentLimit(LaserBeamEntity laser) {
		int phaseId = laser.getPhaseId();
		int pt = laser.getPhaseTick();
		if (phaseId == 0) {
			if (pt < CHARGE_5S_TICK) return RATE_START;
			if (pt < CHARGE_7S_TICK) {
				double p = (pt - CHARGE_5S_TICK) / (double) (CHARGE_7S_TICK - CHARGE_5S_TICK);
				return RATE_START + (RATE_END - RATE_START) * p;
			}
			return RATE_END;
		}
		return RATE_END;
	}

	/** 将 current 以每步最多 maxStep 的速度向 target 逼近（线性）。 */
	private static double approach(double current, double target, double maxStep) {
		double diff = target - current;
		if (Math.abs(diff) <= maxStep) return target;
		return current + Math.signum(diff) * maxStep;
	}
}
