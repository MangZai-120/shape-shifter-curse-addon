package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 跳蛛「跳杀（Jump Kill）」主技能（sp_primary）—— 服务端状态机。
 *
 * <p><b>蓄力</b>（长按 sp_primary）：蓄力越久索敌距离越大（基础 {@value #BASE_DIST} 格 /
 * 满蓄 {@value #CHARGE_MAX}t=3 秒 → {@value #MAX_DIST} 格）；蓄力期移速减半、转视角瞄准；
 * 每 tick 扫描前方视锥内可扑目标：全部<b>红色描边</b>、准星最接近者<b>绿色描边</b>（仅本人可见）。</p>
 *
 * <p><b>跳杀</b>（松开）：以绿色锁定目标（无则按准星落点）向前跳扑——{@code setNoGravity} + 每 tick
 * 施指向目标的「拉力」被拽过去（第六条），距离越远起跳越高（第五条）。命中造成 {@value #DAMAGE}
 * 物理 + 中毒 II {@value #POISON_DURATION}t(8s) + 定身 {@value #STUN_DURATION}t(0.35s)。</p>
 *
 * <p><b>空中追踪（第七条）</b>：追踪时扑向目标<b>提前量拦截点</b>（当前位置 + 速度×{@value #LEAD_TICKS}），
 * 加大更正范围；脱锁容差以「起跳时锁定位置 lockPos0」为圆心、半径 = {@value #LEASH_K}×跳跃距离
 * （跳越远容差越大、越近越小）；目标移出容差即脱锁、按最后方向直飞扑空。</p>
 *
 * <p><b>CD</b>：扑中 {@value #CD_HIT}t(12s)、扑空 {@value #CD_MISS}t(8s)，走 SP_PRIMARY_CD。
 * 白名单：默认白名单（护玩家 + 宠物/召唤物）。全判定服务端，跳跃每 tick 补发速度包给客机。</p>
 */
public final class JumpKillManager {

	private static final int CHARGE_MAX = 60;        // 满蓄 3 秒
	private static final double BASE_DIST = 3.0;      // 基础索敌 3 格
	private static final double MAX_DIST = 16.0;      // 满蓄索敌 16 格
	private static final float DAMAGE = 8.0f;
	private static final int POISON_DURATION = 160;  // 中毒 II 8 秒
	private static final int POISON_AMPLIFIER = 1;   // 中毒 II
	private static final int STUN_DURATION = 7;      // 定身 0.35 秒
	private static final int CD_HIT = 240;           // 扑中 12 秒
	private static final int CD_MISS = 160;          // 扑空 8 秒
	private static final double CHARGE_SLOW = -0.5;  // 蓄力移速 ×0.5
	private static final double LEASH_K = 0.4;       // 脱锁容差系数（半径 = LEASH_K × 跳跃距离）
	private static final int LEAD_TICKS = 6;         // 提前量：扑向 目标位置 + 速度 × LEAD_TICKS
	private static final double LEAP_SPEED = 0.85;   // 跳跃水平拉力速度
	private static final int MAX_LEAP_TICKS = 40;    // 跳跃硬上限 2 秒
	private static final double HIT_RADIUS = 1.3;    // 命中判定距离
	private static final double SCAN_COS = 0.978;    // 锁定视锥半角 ≈ 12°（只锁准星附近目标）
	private static final double GRAVITY_STEP = 0.08; // 跳跃期手动抛物：每 tick 竖直速度衰减（= 重力加速度）
	private static final double TURN_LERP = 0.3;     // 水平转向平滑系数（防接近目标时左右摆动）
	private static final int HIGHLIGHT_TICKS = 15;   // 描边时长（留刷新余量防闪烁）
	private static final int CHARGE_CRY_COUNT = 5;   // 蓄力期蜘蛛嘶鸣总次数（仅自己听）
	private static final int COLOR_RED = 0xFF3030;   // 候选目标红边
	private static final int COLOR_GREEN = 0x30FF30; // 锁定目标绿边
	// ===== 安全丝（保命拉回） =====
	private static final double SILK_MAX = 48.0;     // 丝线最长 48 格，超过即断
	private static final int RECALL_WINDOW = 160;    // 跳杀结束后 8 秒内可拉回
	private static final double RECALL_SPEED = 1.15; // 拉回速度（比跳跃快，快速拽回）
	private static final double RECALL_ARRIVE = 1.6; // 拉回到达阈值（距锚点小于即停）
	private static final int RECALL_STALL_TICKS = 5; // 拉回连续 5t 距离不拉近 → 外力过强，丝断
	private static final double OBSCURE_BREAK_BLOCKS = 1.0; // 丝线被实心方块遮挡累计阈值（同月织蛛，超过即断）

	private static final UUID SLOW_UUID = UUID.fromString("b7e2c9a4-3f81-4d6e-9a25-7c1e0f4d82ab");

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	/** 安全丝拉回窗口：跳杀结束后锚点保留的时段（超时即失效）。 */
	private static final Map<UUID, SilkAnchor> ANCHORS = new ConcurrentHashMap<>();

	private static final class SilkAnchor {
		final Vec3d pos;        // 丝线锚点（起跳点）
		final long validUntil;  // 世界时间：超过即失效
		double lastDist = -1;   // 拉回期：上 tick 到锚点距离（断丝判定用）
		int stall = 0;          // 拉回期：距离未拉近连续 tick 数
		SilkAnchor(Vec3d pos, long validUntil) { this.pos = pos; this.validUntil = validUntil; }
	};

	private static final class State {
		int phase = 0;        // 0=蓄力 1=跳跃 2=拉回
		int chargeTick = 0;
		// 跳跃期
		int leapTick = 0;
		UUID lockTarget;      // 跳跃锁定目标（null=纯位移）
		Vec3d lockPos0;       // 起跳时锁定目标位置（脱锁容差圆心）
		double jumpDist;      // 起跳时到目标的距离（决定跳高 + 容差）
		double flightTime;    // 预计飞行 tick（协调抛物线落点）
		Vec3d lastDir = new Vec3d(0, 0, 1); // 水平拉力方向（单位向量，y=0；平滑插值追踪）
		double vy0;           // 起跳竖直初速（协调抛物：flightTime 后竖直到目标高度）
	}

	private JumpKillManager() {}

	private static boolean isSalticidae(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE);
	}

	/** 客户端按下 sp_primary：优先判「安全丝拉回」（6s 窗口内，绕过 CD——跳杀 CD 8~12s 必然覆盖窗口）；否则开始蓄力。 */
	public static void startCharge(ServerPlayerEntity player) {
		if (!isSalticidae(player)) return;
		// 安全丝拉回：跳杀结束后 6s 内再按主键 → 快速拽回锚点（不进蓄力、不受 CD 阻挡）
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		if (anchor != null) {
			if (player.getServerWorld().getTime() > anchor.validUntil
					|| player.getPos().distanceTo(anchor.pos) > SILK_MAX) {
				// 窗口超时 / 距锦点过远：丝已断（带断丝特效）
				if (player.getWorld() instanceof ServerWorld sw0) snapSilk(player, sw0, anchor);
				ANCHORS.remove(player.getUuid());
				SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0);
			} else {
				startRecall(player, anchor);
				return;
			}
		}
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // CD 中
		if (STATES.containsKey(player.getUuid())) return; // 施法中不可重入
		State s = new State();
		STATES.put(player.getUuid(), s);
		applyChargeSlow(player);
		if (player.getWorld() instanceof ServerWorld sw) {
			// 蓄力起手：低沉蜘蛛嘶鸣（蓄势）
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.6f);
		}
	}

	/** 客户端松开 sp_primary → 跳杀。 */
	public static void release(ServerPlayerEntity player) {
		State s = STATES.get(player.getUuid());
		if (s == null || s.phase != 0) return;
		removeChargeSlow(player);
		if (!(player.getWorld() instanceof ServerWorld sw)) { STATES.remove(player.getUuid()); return; }

		double ratio = Math.min(1.0, (double) s.chargeTick / CHARGE_MAX);
		double scanDist = BASE_DIST + ratio * (MAX_DIST - BASE_DIST);

		// 松开瞬间按当前准星重新锁定（准星最接近的候选）
		LivingEntity lock = pickLockTarget(player, sw, scanDist);

		s.phase = 1;
		s.leapTick = 0;
		Vec3d look = player.getRotationVector().normalize();
		s.lastDir = look;

		// 安全丝：锚点必须落在实地——向下检测 1.5 格内的首个实心方块顶面；空中（无地）不生成蛛丝
		Vec3d anchorPos = groundAnchor(player);
		if (anchorPos != null) {
			SilkAnchor anchor = new SilkAnchor(anchorPos, player.getServerWorld().getTime() + RECALL_WINDOW);
			ANCHORS.put(player.getUuid(), anchor);
			SscAddonNetworking.syncJumpKillSilk(player, true, anchorPos.x, anchorPos.y, anchorPos.z);
		}

		if (lock != null) {
			s.lockTarget = lock.getUuid();
			s.lockPos0 = bodyCenter(lock);
			s.jumpDist = player.getEyePos().distanceTo(bodyCenter(lock));
		} else {
			s.lockTarget = null;
			s.lockPos0 = null;
			s.jumpDist = scanDist;
		}

		player.setNoGravity(true);
		player.fallDistance = 0.0f;
		// 协调抛物线起跳：计算使抛物线 flightTime 后水平+竖直同时抵达落点（轨迹流畅，距离越远抛物越高）
		Vec3d aim = (lock != null) ? bodyCenter(lock) : player.getEyePos().add(look.multiply(s.jumpDist));
		Vec3d horiz = new Vec3d(aim.x - player.getX(), 0, aim.z - player.getZ());
		double horizDist = horiz.length();
		if (horizDist < 1.0e-4) { horiz = new Vec3d(look.x, 0, look.z); horizDist = Math.max(1.0e-4, horiz.length()); }
		horiz = horiz.normalize();
		double dy = aim.y - player.getY();
		double T = Math.max(6.0, horizDist / LEAP_SPEED);
		s.flightTime = T;
		s.vy0 = dy / T + 0.5 * GRAVITY_STEP * T; // T tick 后竖直到达 aim.y；T 越大（距离越远）起跳越高
		s.lastDir = horiz;
		player.setVelocity(horiz.x * LEAP_SPEED, s.vy0, horiz.z * LEAP_SPEED);
		player.velocityModified = true;
		pushVelocity(player);

		// 起跳音效：蜘蛛扑击嘶鸣 + 破空
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.5f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 0.8f);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		State s = STATES.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !isSalticidae(player)) { cancel(player); return; }
		if (!(player.getWorld() instanceof ServerWorld sw)) return;

		if (s.phase == 0) {
			tickCharge(player, sw, s);
		} else if (s.phase == 1) {
			checkSilk(player); // 跳跃期丝线检查：超 32 格 / 被遮挡 → 丝断（拉回窗口作废）
			if (STATES.containsKey(player.getUuid())) tickLeap(player, sw, s); // 丝断不清跳跃态，仅作废锦点
		} else {
			tickRecall(player, sw, s);
		}
	}

	/** 锦点存在 tick：纯锦点（无活动状态）也要推进 8 秒倒计时——到时丝线断裂消失（带特效）。 */
	public static void tickAnchors(ServerPlayerEntity player) {
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		if (anchor == null) return;
		State s = STATES.get(player.getUuid());
		if (s != null && (s.phase == 1 || s.phase == 2)) return; // 跳跃/拉回中：窗口由 finishRecall 重置计时
		if (player.getServerWorld().getTime() > anchor.validUntil) {
			if (player.getWorld() instanceof ServerWorld sw) snapSilk(player, sw, anchor);
			ANCHORS.remove(player.getUuid());
			SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0); // 丝线到时断裂（客户端同步移除）
		}
	}

	/** 开始安全丝拉回：锚点从窗口表转入活动状态，phase=2。 */
	private static void startRecall(ServerPlayerEntity player, SilkAnchor anchor) {
		State s = new State();
		s.phase = 2;
		STATES.put(player.getUuid(), s);
		player.setNoGravity(true);
		player.fallDistance = 0.0f;
		if (player.getWorld() instanceof ServerWorld sw) {
			// 拉回起手：蛛鸣 + 三叉戟拖拽声
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.9f, 1.3f);
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.9f, 1.5f);
		}
	}

	/** 拉回推进：每 tick 向锦点直线拽（三维直线 + 距离比例减速），被外力压过拉不近（连续 5t 距离不缩短）或撞墙 → 丝断放弃。 */
	private static void tickRecall(ServerPlayerEntity player, ServerWorld sw, State s) {
		s.leapTick++;
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		if (anchor == null) { finishRecall(player, false); return; }
		if (s.leapTick > 60) { finishRecall(player, true); return; } // 硬上限 3s

		double dist = player.getPos().distanceTo(anchor.pos);
		// 断丝判定：撞墙卡住 / 连续多 tick 距离不缩短（外力过强拽不回来）/ 丝线被遮挡
		if (s.leapTick > 3 && player.horizontalCollision) { snapSilk(player, sw, anchor); finishRecall(player, false); return; }
		if (computeObscuration(player, bodyCenter(player), anchor.pos) > OBSCURE_BREAK_BLOCKS) {
			snapSilk(player, sw, anchor); finishRecall(player, false); return;
		}
		if (anchor.lastDist >= 0) {
			if (dist >= anchor.lastDist - 0.01) {
				anchor.stall++;
				if (anchor.stall >= RECALL_STALL_TICKS) { snapSilk(player, sw, anchor); finishRecall(player, false); return; }
			} else {
				anchor.stall = 0;
			}
		}
		anchor.lastDist = dist;

		// 到达锦点附近 → 拉回成功（清水平速度防冲过头乱晃）
		if (dist < RECALL_ARRIVE) {
			Vec3d v = player.getVelocity();
			player.setVelocity(v.x * 0.1, v.y, v.z * 0.1);
			player.velocityModified = true;
			finishRecall(player, true);
			return;
		}

		// 拽向锦点：三维直线 + 距离比例减速（远处快、临近自动刹车，不冲过锦点不乱晃）
		Vec3d to = anchor.pos.subtract(player.getPos());
		Vec3d dir3 = to.normalize();
		double speed = Math.min(RECALL_SPEED, dist / 3.0 + 0.25); // 临近自动降速（剩 1 格 ≈ 0.58/t）
		speed = Math.max(0.3, speed);
		player.setVelocity(dir3.x * speed, Math.max(dir3.y * speed, -0.5), dir3.z * speed);
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		pushVelocity(player);

		// 丝线视觉：沿线每 2 格一个蛛网粒子（拉回过程短暂，开销可控）+ 尾迹
		Vec3d dir = anchor.pos.subtract(player.getPos());
		double len = dir.length();
		if (len > 1.0) {
			dir = dir.normalize();
			int segs = (int) (len / 2.0);
			for (int i = 1; i <= segs; i++) {
				Vec3d p = player.getPos().add(dir.multiply(i * 2.0));
				sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, p.x, p.y + 0.4, p.z, 1, 0, 0, 0, 0);
			}
		}
	}

	/** 丝断反馈：绳结断裂音 + 白色丝线粒子。 */
	private static void snapSilk(ServerPlayerEntity player, ServerWorld sw, SilkAnchor anchor) {
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.PLAYERS, 0.9f, 1.4f);
		sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.5), player.getZ(),
				10, 0.2, 0.2, 0.2, 0.02);
	}

	/**
	 * 落地锚点：从玩家脚部向下扫 1.5 格，返回首个「可站立方块」（有外形轮廓即可——
	 * 栏杆/栅栏/半砖/台阶/门等非实心但可碰撞均可）的坐标；
	 * 空中（脚下无地）返回 null = 不生成蛛丝。
	 */
	private static Vec3d groundAnchor(ServerPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		for (int dy = 0; dy <= 1; dy++) { // 0（脚部）、-1（下一格）两档：覆盖 1.5 格检测
			BlockPos check = feet.down(dy);
			if (isStandable(player, check)) {
				return new Vec3d(check.getX() + 0.5, check.getY() + 1.0, check.getZ() + 0.5); // 方块顶面中心
			}
		}
		// 脚部与下一格都空：再精确检测半格（玩家可能站在方块边缘，脚部 y 偏上）
		BlockPos half = BlockPos.ofFloored(player.getX(), player.getY() - 1.5, player.getZ());
		if (isStandable(player, half)) {
			return new Vec3d(half.getX() + 0.5, half.getY() + 1.0, half.getZ() + 0.5);
		}
		return null; // 空中：无地可锚，不生成丝
	}

	/** 可站立判定：方块外形轮廓非空（栏杆/栅栏/半砖/台阶/门等有碰撞面即算，空气/草丛不算）。 */
	private static boolean isStandable(ServerPlayerEntity player, BlockPos pos) {
		net.minecraft.block.BlockState state = player.getWorld().getBlockState(pos);
		return !state.getOutlineShape(player.getWorld(), pos).isEmpty();
	}

	/** 拉回结束：恢复重力、清锦点（无论成败，拉回机会只用一次；丝线耗尽也播断丝特效）。 */
	private static void finishRecall(ServerPlayerEntity player, boolean arrived) {
		player.setNoGravity(false);
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		if (player.getWorld() instanceof ServerWorld sw) {
			if (anchor != null) snapSilk(player, sw, anchor); // 丝线拉完耗尽：同样断裂反馈
			if (arrived) {
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 0.7f, 1.2f);
			}
		}
		ANCHORS.remove(player.getUuid());
		SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0);
		STATES.remove(player.getUuid());
	}

	private static void tickCharge(ServerPlayerEntity player, ServerWorld sw, State s) {
		boolean justReachedMax = s.chargeTick == CHARGE_MAX - 1; // 下一行递增后恰好满蓄（一次性边沿，防满蓄后每 tick 重响）
		if (s.chargeTick < CHARGE_MAX) s.chargeTick++;
		double ratio = Math.min(1.0, (double) s.chargeTick / CHARGE_MAX);
		double scanDist = BASE_DIST + ratio * (MAX_DIST - BASE_DIST);

		// 扫描候选：索敌距离内全部可扑目标（无遮挡才显示/可锁）——红边；准星 12° 锥内最近者绿边锁定
		List<LivingEntity> cands = scanCandidates(player, sw, scanDist);
		LivingEntity best = pickInCrosshair(player, cands);

		for (LivingEntity e : cands) {
			int color = (e == best) ? COLOR_GREEN : COLOR_RED;
			SscAddonNetworking.sendWebHighlight(player, e.getId(), HIGHLIGHT_TICKS, color);
		}

		// 蓄力音效：全程共 5 声上升嘶鸣（仅自己听），间隔均分；满蓄瞬间额外一声经验叮声（边沿触发，防满蓄后循环响）
		int interval = CHARGE_MAX / CHARGE_CRY_COUNT;
		boolean cryNow = s.chargeTick < CHARGE_MAX && s.chargeTick % interval == 0 && s.chargeTick > 0;
		if (cryNow) {
			float pitch = 0.6f + (float) ratio * 0.8f;
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager
					.playSoundToPlayer(player, SoundEvents.ENTITY_SPIDER_AMBIENT, 0.5f, pitch);
		}
		if (justReachedMax) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager
					.playSoundToPlayer(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
		}
	}

	private static void tickLeap(ServerPlayerEntity player, ServerWorld sw, State s) {
		s.leapTick++;
		// 结束条件：超预计飞行时间 / 硬上限 / 落地扑空 / 撞墙
		if (s.leapTick > s.flightTime + 15 || s.leapTick > MAX_LEAP_TICKS
				|| (s.leapTick > 5 && player.isOnGround())
				|| (s.leapTick > 3 && player.horizontalCollision)) {
			finish(player, false);
			return;
		}

		Vec3d playerPos = bodyCenter(player);
		Vec3d targetPoint;

		LivingEntity target = (s.lockTarget != null) ? resolve(sw, s.lockTarget) : null;
		if (target != null && target.isAlive()) {
			// 脱锁判定：目标偏离起跳锁定位置超过容差（半径随跳跃距离线性）
			double leash = LEASH_K * s.jumpDist;
			if (bodyCenter(target).distanceTo(s.lockPos0) > leash) {
				s.lockTarget = null; // 脱锁 → 沿最后方向直飞扑空
				targetPoint = playerPos.add(s.lastDir.multiply(10));
			} else {
				// 提前量拦截点（第七条）：扑向 目标当前位置 + 速度 × LEAD_TICKS
				targetPoint = bodyCenter(target).add(target.getVelocity().multiply(LEAD_TICKS));
				// 命中判定
				if (bodyCenter(target).distanceTo(playerPos) < HIT_RADIUS) {
					onHit(player, sw, target);
					finish(player, true);
					return;
				}
			}
		} else {
			s.lockTarget = null;
			targetPoint = playerPos.add(s.lastDir.multiply(10));
		}

		// 施力：水平方向平滑插值转向（防接近目标时左右摆动）+ 协调抛物竖直（流畅曲线）
		Vec3d desired = new Vec3d(targetPoint.x - playerPos.x, 0, targetPoint.z - playerPos.z);
		if (desired.lengthSquared() > 1.0e-4) {
			desired = desired.normalize();
			s.lastDir = s.lastDir.lerp(desired, TURN_LERP);
			if (s.lastDir.lengthSquared() > 1.0e-4) s.lastDir = s.lastDir.normalize();
		}
		double vy = s.vy0 - GRAVITY_STEP * s.leapTick; // 抛物竖直（不重算，保持平滑）
		player.setVelocity(s.lastDir.x * LEAP_SPEED, vy, s.lastDir.z * LEAP_SPEED);
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		pushVelocity(player);

		// 跳跃拖尾：蛛丝微粒
		if (s.leapTick % 2 == 0) {
			sw.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getBodyY(0.5), player.getZ(),
					2, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** 命中结算：8 物理 + 中毒 II 8s + 定身 0.35s + 音效粒子。 */
	private static void onHit(ServerPlayerEntity player, ServerWorld sw, LivingEntity target) {
		if (WhitelistUtils.isProtected(player, target)) return;
		DamageSource src = player.getDamageSources().playerAttack(player);
		target.damage(src, DAMAGE);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, POISON_DURATION, POISON_AMPLIFIER, false, true, true), player);
		target.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, STUN_DURATION, 0, false, false, false), player);
		sw.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.9f);
		sw.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.7f);
		sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(),
				18, 0.3, 0.3, 0.3, 0.4);
	}

	/** 跳跃结束：恢复重力、进 CD、清状态。 */
	private static void finish(ServerPlayerEntity player, boolean hit) {
		player.setNoGravity(false);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, hit ? CD_HIT : CD_MISS);
		STATES.remove(player.getUuid());
	}

	/** 跳跃期丝线检查：离锚点超 32 格 / 丝线被实心方块遮挡 → 丝断（锚点失效，无拉回）。 */
	private static void checkSilk(ServerPlayerEntity player) {
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		if (anchor == null) return;
		boolean overstretch = player.getPos().distanceTo(anchor.pos) > SILK_MAX;
		boolean obscured = computeObscuration(player, bodyCenter(player), anchor.pos) > OBSCURE_BREAK_BLOCKS;
		if (overstretch || obscured) {
			if (player.getWorld() instanceof ServerWorld sw) snapSilk(player, sw, anchor);
			ANCHORS.remove(player.getUuid());
			SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0);
		}
	}

	/** 丝线遮挡量：沿丝线采样累计实心方块数（同月织蛛算法，两端各跳过 0.6 格防贴墙误断）。 */
	private static double computeObscuration(ServerPlayerEntity player, Vec3d from, Vec3d to) {
		double totalDist = from.distanceTo(to);
		if (totalDist < 2.0) return 0.0;
		int samples = MathHelper.ceil(totalDist * 2.0);
		double obscured = 0.0;
		Vec3d dir = to.subtract(from).normalize();
		double skip = 0.6 / totalDist;
		for (int i = 0; i < samples; i++) {
			double t = (i + 0.5) / samples;
			if (t < skip || t > 1.0 - skip) continue;
			Vec3d p = from.add(dir.multiply(totalDist * t));
			BlockPos bp = BlockPos.ofFloored(p);
			if (player.getWorld().getBlockState(bp).isSolidBlock(player.getWorld(), bp)) {
				obscured += 0.5;
			}
		}
		return obscured;
	}

	/** 取消（死亡/丢形态/断线）：移除减速、恢复重力、清状态与锦点（带断丝特效），不进 CD。 */
	public static void cancel(ServerPlayerEntity player) {
		State s = STATES.remove(player.getUuid());
		if (s != null) {
			removeChargeSlow(player);
			player.setNoGravity(false);
		}
		SilkAnchor anchor = ANCHORS.remove(player.getUuid());
		if (anchor != null) {
			if (player.getWorld() instanceof ServerWorld sw) snapSilk(player, sw, anchor);
			SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0);
		}
	}

	/** 断线/停服清理：锦点一并清。 */
	public static void clearPlayer(UUID uuid) {
		STATES.remove(uuid);
		ANCHORS.remove(uuid);
	}

	public static void clearAll() {
		STATES.clear();
		ANCHORS.clear();
	}

	/** 跳跃期免疫：玩家正在跳杀且伤害源攻击者是其锁定目标 → 免疫（mixin 查询）。 */
	public static boolean isLeapingAgainst(ServerPlayerEntity player, Entity attacker) {
		if (attacker == null) return false;
		State s = STATES.get(player.getUuid());
		if (s == null || s.phase != 1) return false;
		return attacker.getUuid().equals(s.lockTarget);
	}

	/** 毒液技能用：安全丝锚点是否存在且在有效窗口内（=「有丝线连着自己」）。 */
	public static boolean hasActiveSilk(ServerPlayerEntity player) {
		SilkAnchor anchor = ANCHORS.get(player.getUuid());
		return anchor != null && player.getServerWorld().getTime() <= anchor.validUntil
				&& player.getPos().distanceTo(anchor.pos) <= SILK_MAX;
	}

	/** 毒液技能用：消耗丝线（荡丝冲刺用掉锚点，带断丝特效与客户端同步）。 */
	public static void consumeSilk(ServerPlayerEntity player) {
		SilkAnchor anchor = ANCHORS.remove(player.getUuid());
		if (anchor != null) {
			if (player.getWorld() instanceof ServerWorld sw) snapSilk(player, sw, anchor);
			SscAddonNetworking.syncJumpKillSilk(player, false, 0, 0, 0);
		}
	}

	/** 松开时按当前准星锁定：从索敌候选（含遮挡过滤）里取准星 12° 锥内最近者。 */
	private static LivingEntity pickLockTarget(ServerPlayerEntity player, ServerWorld sw, double scanDist) {
		List<LivingEntity> cands = scanCandidates(player, sw, scanDist);
		return pickInCrosshair(player, cands);
	}

	/** 索敌候选：scanDist 内全部活体（非自己/非旁观/非白名单），且视线无方块遮挡（遮挡 = 跳不过去 → 不显示不可锁）。 */
	private static List<LivingEntity> scanCandidates(ServerPlayerEntity player, ServerWorld sw, double scanDist) {
		Vec3d eye = player.getEyePos();
		Box box = player.getBoundingBox().expand(scanDist);
		List<LivingEntity> all = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator()
						&& eye.distanceTo(bodyCenter(e)) <= scanDist
						&& !WhitelistUtils.isProtected(player, e));
		List<LivingEntity> visible = new java.util.ArrayList<>(all.size());
		for (LivingEntity e : all) {
			if (hasLineOfSight(player, e)) visible.add(e);
		}
		return visible;
	}

	/** 准星锁定：候选中与准星方向夹角在 12° 锥内、且与准星最接近者。 */
	private static LivingEntity pickInCrosshair(ServerPlayerEntity player, List<LivingEntity> cands) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();
		LivingEntity best = null;
		double bestDot = SCAN_COS;
		for (LivingEntity e : cands) {
			double dot = look.dotProduct(bodyCenter(e).subtract(eye).normalize());
			if (dot > bestDot) { bestDot = dot; best = e; }
		}
		return best;
	}

	/** 遮挡检测：玩家眼睛→目标身体中心射线（碰撞箱形状，忽略流体）不命中任何方块 = 无遮挡。 */
	private static boolean hasLineOfSight(ServerPlayerEntity player, LivingEntity target) {
		Vec3d from = player.getEyePos();
		Vec3d to = bodyCenter(target);
		net.minecraft.util.hit.BlockHitResult hit = player.getWorld().raycast(new net.minecraft.world.RaycastContext(
				from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
				net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
	}

	private static LivingEntity resolve(ServerWorld sw, UUID uuid) {
		Entity e = sw.getEntity(uuid);
		return (e instanceof LivingEntity le) ? le : null;
	}

	private static Vec3d bodyCenter(LivingEntity e) {
		return new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ());
	}

	private static void pushVelocity(ServerPlayerEntity player) {
		// 玩家移动客户端权威：显式补发速度包，强制主机/客机应用跳跃位移（冲刺技踩过坑）
		if (player.networkHandler != null) {
			player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		}
	}

	private static void applyChargeSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW_UUID);
			speed.addTemporaryModifier(new EntityAttributeModifier(
					SLOW_UUID, "Jump Kill Charge Slow", CHARGE_SLOW, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	private static void removeChargeSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(SLOW_UUID);
	}
}
