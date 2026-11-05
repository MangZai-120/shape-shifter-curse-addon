package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient;
import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient.LocalSwing;
import net.jackcooper.shapeShifterCurseAddon.ability.SwingMotion;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 月织蛛「蛛丝荡漾」<b>客户端摆锤物理核心</b>（本地玩家）。
 *
 * <p>Minecraft 移动是客户端权威的，所以摆锤物理必须在客户端改 velocity/position 才能丝滑无卡顿，
 * 且不干扰玩家破坏方块。本 mixin 注入 {@code ClientPlayerEntity.tickMovement}：
 * <ul>
 *   <li><b>Input.tick 后</b>：缓存本 tick 的空格/潜行/WASD，空格改作收绳。</li>
 *   <li><b>原版移动前</b>：施加本 tick 的绳索速度，仍由原版处理移动与方块碰撞：
 *     ① 收放绳（空格收 / 空中潜行放，受服务端 canExtend 限制，改本地 ropeLen）；
 *     ② WASD 世界方向增强推力（增强空中方向控制）；
 *     ③ 绳约束（距销点超绳长时拉回球面 + 去径向远离速度 → 单摆）。
 *   </li>
 * </ul>
 *
 * <p><b>地面松弛</b>：不收绳时自由移动；按空格可从地面开始收绳，
 * 高处锚点允许直接牵引离地，低角度锚点先沿地面接近，始终保留原版碰撞。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SwingPhysicsMixin {

	@Shadow
	public Input input;

	@Unique
	private boolean ssca$jumpHeld;
	@Unique
	private boolean ssca$sneakHeld;
	@Unique
	private float ssca$moveForward;
	@Unique
	private float ssca$moveSideways;

	@Inject(method = "tickMovement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
	private void ssca$swingHead(CallbackInfo ci) {
		// 蛛丝挂着全程（FIRING + SWINGING）都屏蔽空格跳跃，空格改作收绳
		if (!SpiderMoonWeaverSwingClient.isLocalActive() || this.input == null) {
			ssca$jumpHeld = false;
			ssca$sneakHeld = false;
			return;
		}
		ssca$jumpHeld = this.input.jumping;
		ssca$sneakHeld = this.input.sneaking;
		ssca$moveForward = this.input.movementForward;
		ssca$moveSideways = this.input.movementSideways;
		// 空格不触发原版跳跃
		this.input.jumping = false;
	}

	@Inject(method = "tickMovement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V"))
	private void ssca$blockSwingAutoJump(CallbackInfo ci) {
		// Auto-jump is processed after Input.tick; it must not turn reel input into a second jump.
		if (SpiderMoonWeaverSwingClient.isLocalActive() && this.input != null) this.input.jumping = false;
	}

	@Inject(method = "tickMovement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V"))
	private void ssca$swingTail(CallbackInfo ci) {
		LocalSwing sw = SpiderMoonWeaverSwingClient.getLocalSwing();
		if (sw == null || !sw.active) {
			// 非摆荡：不再触碰 noGravity。原光这里曾无条件 setNoGravity(false)，
			// 会踩掉同 tick 其它技能（跳杀腾空/水矛跳跃/雾形态悬停等）刚设置的 noGravity=true，
			// 导致腾空滞空失效（受重力应声下坠）。摆荡期间重力由下方自积分接管，无需在此恢复。
			return;
		}

		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (sw.state == SpiderMoonWeaverSwingClient.STATE_TETHER) {
			return;
		}
		if (sw.state != SpiderMoonWeaverSwingClient.STATE_SWINGING) return;

		final double MAX = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.MAX_ROPE_REACH;

		Vec3d anchor = new Vec3d(sw.anchorX, sw.anchorY, sw.anchorZ);
		Vec3d torso = self.getPos().add(0, 1.0, 0);
		Vec3d rope = torso.subtract(anchor); // 销点 → 玩家
		double dist = rope.length();
		boolean onGround = self.isOnGround();
		Vec3d vel = self.getVelocity();

		// ---- 收放绳意图（仅标记供上报，实际 ropeLen 变化在物理段统一处理）----
		int reelIntent = 0;
		if (ssca$jumpHeld) {
			reelIntent = 1;
		} else if (ssca$sneakHeld && !onGround) {
			if (sw.canExtend) {
				reelIntent = -1;
			}
		}
		sw.reelIntent = reelIntent;

		if (onGround && dist <= MAX) {
			Vec3d direction = dist > 1.0e-6 ? anchor.subtract(torso).multiply(1.0 / dist) : new Vec3d(0, 1, 0);
			SwingMotion.Step step = SwingMotion.groundStep(vel, direction, dist, sw.ropeLen, ssca$jumpHeld);
			sw.ropeLen = step.ropeLength();
			sw.reelIntent = step.reelIntent();
			self.setVelocity(step.velocity());
			sw.physicsStarted = false; // 离地时重新用玩家速度初始化摆锤
			return;
		}

		// ==== 空中摆荡：自积分惯性守恒（swingVel 承接摆动惯性、不被原版空气阻力衰减 → 能持续荡；setVelocity 施加、不 setPosition）====
		self.setNoGravity(false); // 保留重力开关（防 noGravity 泄漏；重力由下方自积分施加）
		final double GRAVITY = 0.075; // 自积分重力=荡的力度（越大摆动越有劲、荡得越高；重力系统始终存在）
		final double DAMPING = 0.99; // 空气阻力（每 tick 保留 99% 惯性 → 摆动缓慢衰减、不会无限摆）

		Vec3d v;
		if (sw.physicsStarted) {
			v = sw.swingVel.multiply(DAMPING); // 承接上 tick 惯性 + 轻微空气阻力（绕开原版水平阻力 *0.91，改用可控阻尼）
			v = v.add(0, -GRAVITY, 0);         // 自己施加重力（保留重力系统）
		} else {
			v = self.getVelocity();    // 首次/离地后用玩家真实速度初始化摆锤
			sw.physicsStarted = true;
		}
		// 撞墙/落地则对应轴改用原版碰撞后的真实速度（避免自积分忽略碰撞导致穿墙/抖动）
		Vec3d realVel = self.getVelocity();
		if (self.horizontalCollision) v = new Vec3d(realVel.x, v.y, realVel.z);
		if (self.verticalCollision) v = new Vec3d(v.x, realVel.y, v.z);

		Vec3d toAnchor = anchor.subtract(torso);
		double anchorDist = toAnchor.length();
		Vec3d dir = anchorDist > 0.001 ? toAnchor.multiply(1.0 / anchorDist) : new Vec3d(0, 1, 0); // 玩家→销点
		// WASD 世界方向增强推力（增强空中方向控制，小量叠加原版水平移动，不破坏流畅）
		double pushX = 0, pushZ = 0;
		if (ssca$moveForward != 0f || ssca$moveSideways != 0f) {
			float yaw = self.getYaw();
			Vec3d forward = Vec3d.fromPolar(0f, yaw);
			Vec3d right = Vec3d.fromPolar(0f, yaw + 90f);
			Vec3d push = forward.multiply(ssca$moveForward).add(right.multiply(-ssca$moveSideways));
			double pl = Math.sqrt(push.x * push.x + push.z * push.z);
			if (pl > 0.01) {
				pushX = push.x / pl * 0.004;
				pushZ = push.z / pl * 0.004;
			}
		}

		SwingMotion.Step step = SwingMotion.step(v, dir, anchorDist, sw.ropeLen,
				ssca$jumpHeld, ssca$sneakHeld, sw.canExtend, pushX, pushZ);
		v = step.velocity();
		sw.ropeLen = step.ropeLength();
		sw.reelIntent = step.reelIntent();

		self.setVelocity(v);
		sw.swingVel = v; // 记录惯性供下 tick 自积分承接（不被原版空气阻力衰减）
	}

	@Unique
	private double ssca$prevTetherDist = -1;

	@Inject(method = "tickMovement", at = @At("TAIL"))
	private void ssca$tetherAfterMovement(CallbackInfo ci) {
		LocalSwing sw = SpiderMoonWeaverSwingClient.getLocalSwing();
		if (sw != null && sw.active && sw.state == SpiderMoonWeaverSwingClient.STATE_TETHER)
			ssca$tetherPhysics((ClientPlayerEntity)(Object)this, sw);
	}

	/** TETHER：拉玩家向目标（按抗性缩放实现「拉不动大型生物就拉自己过去」）+ 变速收放绳。 */
	@Unique
	private void ssca$tetherPhysics(ClientPlayerEntity self, LocalSwing sw) {
		if (self.getWorld() == null) return;
		net.minecraft.entity.Entity target = self.getWorld().getEntityById(sw.tetherEntityId);
		if (target == null) {
			ssca$prevTetherDist = -1;
			return;
		}
		final double MIN = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.MIN_ROPE_LEN;
		final double REEL = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.REEL_SPEED;

		Vec3d pPos = self.getPos().add(0, 1.0, 0);
		Vec3d tPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
		double dist = pPos.distanceTo(tPos);

		// 变速判定：对方靠近 → 收快；逃离 → 收慢
		boolean approaching = ssca$prevTetherDist >= 0 && dist < ssca$prevTetherDist - 0.02;
		boolean fleeing = ssca$prevTetherDist >= 0 && dist > ssca$prevTetherDist + 0.02;
		ssca$prevTetherDist = dist;

		// 卡住判定：撞墙（无法迁移）时不缩短丝线，让玩家能自由绕开
		boolean stuck = self.horizontalCollision;
		// 玩家有主动移动输入时不施 tether 拉力（避免拉力抵消 WASD 导致「走不动=卡死」，按 WASD 即可脱困）
		boolean playerMoving = Math.abs(ssca$moveForward) > 0.01 || Math.abs(ssca$moveSideways) > 0.01;
		int reelIntent = 0;
		if (ssca$jumpHeld) {
			if (!stuck) {
				double factor = approaching ? 1.5 : (fleeing ? 0.5 : 1.0);
				sw.ropeLen = Math.max(MIN, sw.ropeLen - REEL * factor);
				reelIntent = 1;
			}
		}
		// tether 不能延长蛛丝：潜行不再放绳
		sw.reelIntent = reelIntent;

		double ropeLen = Math.max(sw.ropeLen, MIN); // 硬上限（初始 16）
		double softLen = Math.max(MIN, ropeLen - net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.TETHER_SOFT_BUFFER); // 软拉阈值（初始 12）
		final double PULL_GAIN = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.TETHER_PULL_GAIN;
		final double HARD_GAIN = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.TETHER_HARD_GAIN;
		double resist = 0.0;
		if (target instanceof net.minecraft.entity.LivingEntity livingR) {
			net.minecraft.entity.attribute.EntityAttributeInstance instR =
					livingR.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
			if (instR != null) resist = MathHelper.clamp(instR.getValue(), 0.0, 1.0);
		}
		// 牵引玩家向目标：12 起线性加大(over×PULL_GAIN)，超 16 额外大力((dist-16)×HARD_GAIN、线性更陡)靠力防过远。
		// 不硬吸收速度(不卡)；软拉部分 WASD 脱困时豁免，大力部分(16+)始终施加。分轴阻挡防被拉进方块。
		if (dist > softLen) {
			Vec3d dir = tPos.subtract(pPos).normalize();
			if (self.isOnGround() && dir.y < 0) {
				dir = new Vec3d(dir.x, 0, dir.z);
				double h = dir.length();
				dir = h > 0.01 ? dir.multiply(1.0 / h) : Vec3d.ZERO;
			}
			net.minecraft.util.math.Box box = self.getBoundingBox();
			double ddx = dir.x, ddy = dir.y, ddz = dir.z;
			if (ddx != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(Math.signum(ddx) * 0.25, 0, 0))) ddx = 0;
			if (ddy != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(0, Math.signum(ddy) * 0.25, 0))) ddy = 0;
			if (ddz != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(0, 0, Math.signum(ddz) * 0.25))) ddz = 0;
			Vec3d validDir = new Vec3d(ddx, ddy, ddz);
			double vl = validDir.length();
			if (vl > 0.01) {
				validDir = validDir.multiply(1.0 / vl);
				double softPull = (dist - softLen) * PULL_GAIN; // 12 起线性
				double hardPull = (dist > ropeLen) ? (dist - ropeLen) * HARD_GAIN : 0.0; // 16 起额外大力
				double pull = (playerMoving ? hardPull : (softPull + hardPull)) * (0.2 + 0.8 * resist);
				double cap = (dist > ropeLen) ? 0.9 : 0.6;
				Vec3d vel = self.getVelocity();
				double toward = vel.dotProduct(validDir);
				if (toward < pull) vel = vel.add(validDir.multiply(pull - toward));
				double sp = vel.length();
				if (sp > cap) vel = vel.multiply(cap / sp); // 速度上限防爆冲（超 16 放宽给大力）
				self.setVelocity(vel);
				self.velocityModified = true;
			}
			// validDir≈0（三轴全被挡，完全卡死方向）→ 不施力
		}
	}
}
