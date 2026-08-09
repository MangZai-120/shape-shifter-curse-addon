package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient;
import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient.LocalSwing;
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
 *   <li><b>HEAD</b>：摆荡中缓存本 tick 的空格/潜行/WASD 输入，并清零 {@code input.jumping}（空格改作「收绳」，不跳跃）。</li>
 *   <li><b>TAIL</b>：本 tick 原版移动（含重力、WASD air-control）完成后，施加：
 *     ① 收放绳（空格收 / 空中潜行放，受服务端 canExtend 限制，改本地 ropeLen）；
 *     ② WASD 世界方向增强推力（增强空中方向控制）；
 *     ③ 绳约束（距销点超绳长时拉回球面 + 去径向远离速度 → 单摆）。
 *   </li>
 * </ul>
 *
 * <p><b>地面松弛</b>：脚踩地且距销点 ≤ 24 格时不施加牵引（自由移动/破坏方块），绳长跟随玩家距离增长；
 * 仅主动按空格收绳时给朝销点水平拉力。走到 24 格外才进入张紧摆荡。
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

	@Inject(method = "tickMovement", at = @At("HEAD"))
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

	@Inject(method = "tickMovement", at = @At("TAIL"))
	private void ssca$swingTail(CallbackInfo ci) {
		LocalSwing sw = SpiderMoonWeaverSwingClient.getLocalSwing();
		if (sw == null || !sw.active) {
			// 非摆荡：恢复重力（防 noGravity 泄漏）
			ClientPlayerEntity self0 = (ClientPlayerEntity) (Object) this;
			self0.setNoGravity(false);
			return;
		}

		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (sw.state == SpiderMoonWeaverSwingClient.STATE_TETHER) {
			ssca$tetherPhysics(self, sw);
			return;
		}
		if (sw.state != SpiderMoonWeaverSwingClient.STATE_SWINGING) return;

		final double MIN = net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.MIN_ROPE_LEN;
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
			// ---- 地面松弛期 ----
			if (ssca$jumpHeld && sw.ropeLen < dist - 0.1) {
				// 主动收绳：给朝销点的水平拉力
				Vec3d horiz = new Vec3d(anchor.x - torso.x, 0, anchor.z - torso.z);
				double hl = horiz.length();
				if (hl > 0.01) {
					double strength = Math.min((dist - sw.ropeLen) * 0.18, 0.55);
					vel = vel.add(horiz.multiply(strength / hl));
					self.setVelocity(vel);
				}
			} else if (dist > sw.ropeLen) {
				// 被动松弛：绳长跟随，无牵引
				sw.ropeLen = dist;
			}
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
		double radialVel = v.dotProduct(dir); // 朝销点>0 / 远离<0

		// 绳子偏离「正下方」的摆角（dir 指向销点，dir.y=1 即玩家在销点正下方→摆角0°）
		double swingAngle = Math.acos(MathHelper.clamp(dir.y, -1.0, 1.0));
		boolean canClimb = swingAngle <= Math.toRadians(70.0);

		if (ssca$jumpHeld && canClimb) {
			// 收绳线性上升：径向速度设为恒定 CLIMB（切向保留）→ 匀速上升；绳长跟随缩短
			// 摆角 > 70°时不收绳（玩家已荡到接近销点水平面，再拉不自然；牵生物走独立 tether 方法不受此限）
			double CLIMB = 0.169;
			v = v.add(dir.multiply(CLIMB - radialVel));
			sw.ropeLen = MathHelper.clamp(anchorDist - CLIMB, MIN, MAX);
		} else if (ssca$sneakHeld) {
			// 放绳线性下降：径向速度设为恒定 -DESCEND（远离销点，切向保留）→ 匀速下降；
			// 绳长实时跟随增长（ropeLen 始终 ≥ anchorDist）→ 绳约束永不触发 → 不卡顿
			if (sw.canExtend) {
				double DESCEND = 0.15;
				v = v.add(dir.multiply(-DESCEND - radialVel));
				sw.ropeLen = MathHelper.clamp(anchorDist + DESCEND, MIN, MAX);
			}
		}
		// 松手：ropeLen 固定不变，下面绳约束消除径向远离 → 重力被绳吊住悬停（重力仍在，切向可摆）

		// WASD 世界方向增强推力（增强空中方向控制，小量叠加原版水平移动，不破坏流畅）
		if (ssca$moveForward != 0f || ssca$moveSideways != 0f) {
			float yaw = self.getYaw();
			Vec3d forward = Vec3d.fromPolar(0f, yaw);
			Vec3d right = Vec3d.fromPolar(0f, yaw + 90f);
			Vec3d push = forward.multiply(ssca$moveForward).add(right.multiply(-ssca$moveSideways));
			double pl = Math.sqrt(push.x * push.x + push.z * push.z);
			if (pl > 0.01) {
				v = v.add(push.x / pl * 0.004, 0, push.z / pl * 0.004);
			}
		}

		// 绳约束（核心）：超过绳长 → 消除「径向远离」速度 + 柔性回拉超出部分（纯 velocity，不 setPosition）
		// 摆角 > 70°（丝线挂高角度方块）时放宽限位至丝线最大长度 MAX：玩家自由移动/下落，直到拉满 MAX 才被拽住
		double ropeLen = Math.max(sw.ropeLen, MIN);
		double effectiveRopeLen = canClimb ? ropeLen : MAX;
		if (anchorDist > effectiveRopeLen) {
			double rv = v.dotProduct(dir);
			if (rv < 0) {
				v = v.subtract(dir.multiply(rv)); // 消除全部径向远离 → 绳绷紧吊住（重力径向被抵消）
			}
			double over = anchorDist - effectiveRopeLen;
			v = v.add(dir.multiply(Math.min(over * 0.2, 0.3))); // 柔性回拉超出部分（速度非位置，防伸长）
		}

		// 速度上限（防失控/穿墙；封顶惯性摆动最高速）
		double sp = v.length();
		if (sp > 0.72) v = v.multiply(0.72 / sp);

		self.setVelocity(v);
		sw.swingVel = v; // 记录惯性供下 tick 自积分承接（不被原版空气阻力衰减）
	}

	@Unique
	private double ssca$prevTetherDist = -1;

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

		// 拉玩家向目标（抗性越高玩家被拉越多 = 大型生物拉不动就把自己拉过去）
		// 分轴阻挡检测：前进路上有方块的轴分量去掉，只保留能通过的方向沿障碍滑动，避免被拉进方块卡死
		// 玩家主动移动(WASD)时不拉，让玩家优先脱困
		double ropeLen = Math.max(sw.ropeLen, MIN);
		if (dist > ropeLen + 0.5 && !playerMoving) {
			double resist = 0.0;
			if (target instanceof net.minecraft.entity.LivingEntity living) {
				net.minecraft.entity.attribute.EntityAttributeInstance inst =
						living.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
				if (inst != null) resist = MathHelper.clamp(inst.getValue(), 0.0, 1.0);
			}
			Vec3d dir = tPos.subtract(pPos).normalize();
			// 地面时去掉向下拉力分量（防被拉进地面）
			if (self.isOnGround() && dir.y < 0) {
				dir = new Vec3d(dir.x, 0, dir.z);
				double h = dir.length();
				dir = h > 0.01 ? dir.multiply(1.0 / h) : Vec3d.ZERO;
			}
			// 分轴阻挡检测：某轴方向紧贴方块则去掉该轴分量，只保留能走的方向（沿障碍滑动）
			net.minecraft.util.math.Box box = self.getBoundingBox();
			double ddx = dir.x, ddy = dir.y, ddz = dir.z;
			if (ddx != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(Math.signum(ddx) * 0.25, 0, 0))) ddx = 0;
			if (ddy != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(0, Math.signum(ddy) * 0.25, 0))) ddy = 0;
			if (ddz != 0 && !self.getWorld().isSpaceEmpty(self, box.offset(0, 0, Math.signum(ddz) * 0.25))) ddz = 0;
			Vec3d validDir = new Vec3d(ddx, ddy, ddz);
			double vl = validDir.length();
			if (vl > 0.01) {
				validDir = validDir.multiply(1.0 / vl); // 重新归一化能通过的方向
				double over = dist - ropeLen;
				double desired = Math.min(over * 0.2, 0.45) * (0.2 + 0.8 * resist);
				Vec3d vel = self.getVelocity();
				double toward = vel.dotProduct(validDir);
				if (toward < desired) {
					vel = vel.add(validDir.multiply(desired - toward));
				}
				double sp = vel.length();
				if (sp > 0.6) {
					vel = vel.multiply(0.6 / sp); // 硬速度上限防爆冲
				}
				self.setVelocity(vel);
				self.velocityModified = true;
			}
			// validDir≈0（三轴全被挡，完全卡死方向）→ 不施力
		}
	}
}
