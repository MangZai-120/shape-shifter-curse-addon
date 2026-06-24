package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
 * 契灵 - 次要技能：瞬移
 *
 * 两种模式（由客户端配置 mancianimaTeleportMode 决定）：
 * - RAYCAST：朝准星方向直接传送，最远 8 格，遇到方块停止
 * - PLATFORM：锁定准星方向最近的可站立平台，落点紫色粒子仅自己可见，
 *   按下时显示预览，松开按键时传送；前方无可传送平台则拒绝（不消耗CD/法力）
 *
 * CD: 5 秒（100 tick）   法力消耗: 15
 * 仅自身瞬移，不需要白名单。
 */
public final class MancianimaTeleport {

	public static final int COOLDOWN_TICKS = 100; // 5s
	public static final int RED_KILL_NO_KILL_CD_TICKS = 200; // 10s 成功CD
	public static final int RED_FAIL_CD_TICKS = 100; // 5s 失败CD
	public static final int MANA_COST = 15;
	public static final int RED_MARK_MANA_COST = 20;
	public static final double MAX_RANGE = 8.0;
	public static final double RED_MARK_TARGET_RANGE = 32.0;
	public static final int RED_MARK_CHANNEL_TICKS = 20; // 1s
	public static final float RED_MARK_DAMAGE_CAP = 35.0f;
	public static final double RED_MARK_DAMAGE_PERCENT = 0.50;
	/** 传送后冻结自然回蓝的 tick 数（5 秒） */
	public static final int MANA_REGEN_PAUSE_TICKS = 100;
	/** sp_mana_regen 的暂停计时子资源（apoli:multiple 子键 → power_id + "_" + sub_key） */
	private static final net.minecraft.util.Identifier MANA_REGEN_PAUSE_RES =
			new net.minecraft.util.Identifier("my_addon", "form_familiar_fox_sp_mana_regen_regen_pause_timer");

	private MancianimaTeleport() {
	}

	/** 模式 0 = RAYCAST，1 = PLATFORM。客户端发送，服务端只信任模式（看向矢量自行从玩家状态读取）。 */
	public static boolean execute(ServerPlayerEntity player, byte mode) {
		if (!isMancianima(player)) return false;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return false;

		// 红标联动：如果准星在某个被本玩家红标的生物上，启动 1s 引导
		net.minecraft.entity.LivingEntity redTarget = tryFindRedMarkedInCrosshair(player);
		if (redTarget != null) {
			if (net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerMana(player) < RED_MARK_MANA_COST) {
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.no_mana"), true);
				return false;
			}
			if (MancianimaMarkManager.CHANNELING.containsKey(player.getUuid())) return false;
			long now = ((ServerWorld) player.getWorld()).getTime();
			MancianimaMarkManager.CHANNELING.put(player.getUuid(),
					new MancianimaMarkManager.ChannelState(redTarget.getUuid(), now + RED_MARK_CHANNEL_TICKS, 2));
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.channeling"), true);
			return true;
		}

		if (ManaUtils.getPlayerMana(player) < MANA_COST) {
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.no_mana"), true);
			return false;
		}

		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();
		ServerWorld world = (ServerWorld) player.getWorld();

		Vec3d targetFeet;
		if (mode == 1) {
			targetFeet = computePlatformLanding(world, eye, look, player);
			if (targetFeet == null) {
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.no_platform"), true);
				return false; // 无平台 → 拒绝传送、不消耗CD/法力
			}
		} else {
			targetFeet = computeRaycastLanding(world, eye, look, player);
			if (targetFeet == null) return false;
		}

		// 传送落点判定：① 落点本身能容纳玩家；② 平台模式要求落点在玩家视野内（眼睛→落点腰部无方块遮挡）。
		// 用「视野内」替代旧的整条路径碰撞采样——后者会把"传上比自己高的平台"误判为穿墙（脚→台顶直线必经平台实体）而拒绝。
		// 视野判定既能挡隔栅栏/隔墙传送（落点腰部被遮挡），又允许正常登高/跳跃落点（#6）。
		if (!isSafeLanding(world, player, targetFeet)
				|| (mode == 1 && !isLandingVisible(world, player, targetFeet))) {
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.no_platform"), true);
			return false;
		}

		// 出发点粒子 + 音效（对周围所有玩家可见/可听）
		ParticleUtils.spawnParticles(world, ParticleTypes.PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.3, 0.8, 0.3, 0.6);
		ParticleUtils.spawnParticles(world, ParticleTypes.REVERSE_PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(),
				20, 0.3, 0.5, 0.3, 0.05);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);

		// 执行传送（保留视角方向）
		player.teleport(targetFeet.x, targetFeet.y, targetFeet.z);

		// 落点粒子 + 音效（对周围所有玩家可见/可听）
		ParticleUtils.spawnParticles(world, ParticleTypes.PORTAL,
				targetFeet.x, targetFeet.y + 1.0, targetFeet.z,
				40, 0.3, 0.8, 0.3, 0.6);
		ParticleUtils.spawnParticles(world, ParticleTypes.REVERSE_PORTAL,
				targetFeet.x, targetFeet.y + 1.0, targetFeet.z,
				20, 0.3, 0.5, 0.3, 0.05);
		world.playSound(null, targetFeet.x, targetFeet.y, targetFeet.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

		// 扣除法力 + 设置CD
		ManaComponent mana = ManaUtils.getManaComponent(player);
		if (mana != null) {
			mana.setMana(Math.max(0.0, mana.getMana() - MANA_COST));
		}
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, COOLDOWN_TICKS);
		// 传送后冻结自然回蓝 3 秒（不影响主动回蓝技能/消耗）
		PowerUtils.setResourceValueAndSync(player, MANA_REGEN_PAUSE_RES, MANA_REGEN_PAUSE_TICKS);
		return true;
	}

	private static boolean isMancianima(PlayerEntity player) {
		IForm form = FormUtils.getCurrentForm(player);
		return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID());
	}

	/** 准星上是否有被本玩家红标的目标？返回该目标，否则 null。 */
	private static net.minecraft.entity.LivingEntity tryFindRedMarkedInCrosshair(ServerPlayerEntity player) {
		MancianimaMarkManager.Mark m = MancianimaMarkManager.getMark(player.getUuid());
		if (m == null || m.color != MancianimaMarkManager.MarkColor.RED) return null;
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();
		Vec3d end = eye.add(look.multiply(RED_MARK_TARGET_RANGE));
		ServerWorld world = (ServerWorld) player.getWorld();
		net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(eye, end).expand(2.0);
		double bestDist = Double.MAX_VALUE;
		net.minecraft.entity.LivingEntity best = null;
		for (net.minecraft.entity.Entity e : world.getOtherEntities(player, searchBox,
				net.minecraft.predicate.entity.EntityPredicates.EXCEPT_SPECTATOR)) {
			if (!(e instanceof net.minecraft.entity.LivingEntity le) || !le.isAlive()) continue;
			if (!le.getUuid().equals(m.targetUuid)) continue;
			net.minecraft.util.math.Box box = e.getBoundingBox().expand(1.0); // "大致对准"放宽
			java.util.Optional<Vec3d> hit = box.raycast(eye, end);
			if (hit.isEmpty()) continue;
			double d = eye.squaredDistanceTo(hit.get());
			if (d < bestDist) { bestDist = d; best = le; }
		}
		return best;
	}

	/** MancianimaMarkManager 引导 tick 末尾调用：执行红标瞬移斩杀。 */
	public static void executeRedMarkChannelComplete(ServerPlayerEntity marker, net.minecraft.entity.LivingEntity target) {
		if (target == null || !target.isAlive()) {
			PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.SP_SECONDARY_CD, RED_FAIL_CD_TICKS);
			return;
		}
		// 扣 mana
		ManaComponent mana = ManaUtils.getManaComponent(marker);
		if (mana != null) mana.setMana(Math.max(0.0, mana.getMana() - RED_MARK_MANA_COST));
		ServerWorld world = (ServerWorld) marker.getWorld();
		// 计算落点：目标身后1格地面
		Vec3d targetPos = target.getPos();
		Vec3d targetLook = target.getRotationVector().normalize();
		Vec3d behind = targetPos.subtract(targetLook.x, 0, targetLook.z).add(0, 0, 0); // 1 格后方
		Vec3d landing = adjustBehindTarget(world, target, behind);
		// 出发粒子
		ParticleUtils.spawnParticles(world, ParticleTypes.PORTAL,
				marker.getX(), marker.getY() + 1.0, marker.getZ(),
				40, 0.3, 0.8, 0.3, 0.6);
		// 传送
		marker.teleport(landing.x, landing.y, landing.z);
		// 让契灵看向目标
		double dx = target.getX() - landing.x;
		double dz = target.getZ() - landing.z;
		float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		marker.setYaw(yaw);
		marker.setPitch(0);
		marker.networkHandler.requestTeleport(landing.x, landing.y, landing.z, yaw, 0);
		// 落点粒子
		ParticleUtils.spawnParticles(world, ParticleTypes.PORTAL,
				landing.x, landing.y + 1.0, landing.z,
				40, 0.3, 0.8, 0.3, 0.6);
		// 50% 缺失血伤害（上限35），无视护甲（用 OUT_OF_WORLD）
		float missing = target.getMaxHealth() - target.getHealth();
		float dmg = (float) Math.min(RED_MARK_DAMAGE_CAP, missing * RED_MARK_DAMAGE_PERCENT);
		boolean wasAlive = target.isAlive();
		target.damage(world.getDamageSources().playerAttack(marker), dmg);
		// 广播暴击音效
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		// 设置 CD + 暂停回蓝
		PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.SP_SECONDARY_CD, RED_KILL_NO_KILL_CD_TICKS);
		PowerUtils.setResourceValueAndSync(marker, MANA_REGEN_PAUSE_RES, MANA_REGEN_PAUSE_TICKS);
		// 击杀奖励：刷新两个 CD + 抗伤补满
		if (wasAlive && !target.isAlive()) {
			PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.SP_PRIMARY_CD, 0);
			PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.SP_SECONDARY_CD, 0);
			int max = PowerUtils.getResourceMax(marker, FormIdentifiers.MANCIANIMA_RESISTANCE);
			if (max <= 0) max = 2;
			PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.MANCIANIMA_RESISTANCE, max);
			// 斩杀回满魔力，与主技能保持一致
			ManaUtils.setPlayerMana(marker, ManaUtils.getPlayerMaxMana(marker));
			marker.sendMessage(Text.translatable("message.ssc_addon.mancianima.teleport.kill_bonus"), true);
		}
		// 红标使命达成 → 清除
		MancianimaMarkManager.clearMark(world.getServer(), marker.getUuid());
	}

	/** 计算红标瞬移落点：优先目标后方1格地面；后方>2格悬崖→旁侧；旁侧无地→后方1格悬空。 */
	private static Vec3d adjustBehindTarget(ServerWorld world, net.minecraft.entity.LivingEntity target, Vec3d desired) {
		// 落点 Y：从 desired Y 向下扫最多 3 格寻找地面
		double baseY = target.getY();
		BlockPos basePos = BlockPos.ofFloored(desired.x, baseY, desired.z);
		// 检查后方1格地面是否存在（脚下方块实心）
		BlockPos floorBelow = basePos.down();
		boolean hasGround = !world.getBlockState(floorBelow).getCollisionShape(world, floorBelow).isEmpty();
		if (hasGround) return new Vec3d(desired.x, baseY, desired.z);
		// 没地面 → 检查目标旁侧
		Vec3d targetLook = target.getRotationVector().normalize();
		Vec3d sideRight = new Vec3d(-targetLook.z, 0, targetLook.x).multiply(1.0);
		Vec3d sidePos = target.getPos().add(sideRight);
		BlockPos sideBelow = BlockPos.ofFloored(sidePos.x, baseY, sidePos.z).down();
		if (!world.getBlockState(sideBelow).getCollisionShape(world, sideBelow).isEmpty()) {
			return new Vec3d(sidePos.x, baseY, sidePos.z);
		}
		// 都没有 → 仍传后方1格（空中）
		return new Vec3d(desired.x, baseY, desired.z);
	}

	/**
	 * RAYCAST 模式：沿视线最远 8 格，碰到方块时回退一点防卡墙。
	 * 共享算法（客户端可调用同一份逻辑做预览）。
	 */
	public static Vec3d computeRaycastLanding(World world, Vec3d eye, Vec3d look, PlayerEntity player) {
		Vec3d end = eye.add(look.multiply(MAX_RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(
				eye, end,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));
		Vec3d landingEye;
		if (hit.getType() == HitResult.Type.MISS) {
			landingEye = end;
		} else {
			// 从碰撞点回退 0.4 格，避免脚部嵌入墙体
			landingEye = hit.getPos().add(look.multiply(-0.4));
		}
		// 转换为脚部坐标（玩家视高约 1.62）
		double feetY = landingEye.y - (player.getEyeY() - player.getY());
		// 防止 feetY 嵌入地面：若脚下方块为实心则向上抬一格
		BlockPos feetPos = BlockPos.ofFloored(landingEye.x, feetY, landingEye.z);
		if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty()) {
			feetY = feetPos.getY() + 1.0;
		}
		return new Vec3d(landingEye.x, feetY, landingEye.z);
	}

	/**
	 * PLATFORM 模式：人性化策略。
	 * <ul>
	 *   <li>使用<b>精确射线 XZ</b>作为落点（不卡格中心）；落点 Y 取方块碰撞箱顶面（楼梯/台阶适配）。</li>
	 *   <li>射线先做一次精确 raycast：命中 → 落到命中点（顶面命中=直接站；侧/底命中=该列向下扫）。</li>
	 *   <li>射线未命中 → 沿射线 march，所有列内有效平台中取<b>3D 距离离射线终点最近</b>的一个，
	 *       使得仰角越平→落点越远；仰角向上→优先选高处壁架而非脚下地面。</li>
	 *   <li>每列扫描窗口 [refY-3, refY+1]，绝不会从射线很低处的列取到地面，从而避免低瞄高时落到脚下。</li>
	 * </ul>
	 * 站立条件只要求：方块顶面非空 + 上方有约 1.8 格空气；不限制平台尺寸。
	 */
	public static Vec3d computePlatformLanding(World world, Vec3d eye, Vec3d look, PlayerEntity player) {
		Vec3d end = eye.add(look.multiply(MAX_RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(
				eye, end,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));

		if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3d hitPos = hit.getPos();
			// Case 1：命中方块顶面 → 直接以命中点 XZ 落脚（瞄哪站哪）
			if (hit.getSide() == Direction.UP) {
				BlockPos solid = hit.getBlockPos();
				Double topY = collisionTopY(world, solid);
				if (topY != null && hasHeadroom(world, hitPos.x, topY, hitPos.z)
						&& isSafeLanding(world, player, new Vec3d(hitPos.x, topY, hitPos.z))) {
					Vec3d landing = new Vec3d(hitPos.x, topY, hitPos.z);
					if (withinRange(eye, landing)) return landing;
				}
			}
			// Case 2：命中侧面/底面 → 在命中点前方的空气列里找最贴近命中 Y 的平台（用命中点 XZ）
			BlockPos airCol = hit.getBlockPos().offset(hit.getSide());
			Vec3d landing = findBestInColumn(world, player, hitPos.x, hitPos.z, airCol.getX(), airCol.getZ(), hitPos.y, eye, hitPos);
			if (landing != null) return landing;
		}

		// Case 3：射线无阻挡 → 沿射线 march，取离"射线终点"3D 距离最近的平台（远偏置）
		Vec3d best = null;
		double bestSqToEnd = Double.MAX_VALUE;
		double step = 0.5;
		for (double d = 1.0; d <= MAX_RANGE; d += step) {
			Vec3d sample = eye.add(look.multiply(d));
			Vec3d landing = findBestInColumn(world, player, sample.x, sample.z,
					MathHelper.floor(sample.x), MathHelper.floor(sample.z), sample.y, eye, end);
			if (landing == null) continue;
			double sq = landing.squaredDistanceTo(end);
			if (sq < bestSqToEnd) {
				bestSqToEnd = sq;
				best = landing;
			}
		}
		return best;
	}

	/**
	 * 在指定方块列 (bx,bz) 内寻找最佳落脚平台。
	 * 落点 XZ 使用 exactX/exactZ（保留小数精度）；Y 使用方块碰撞箱实际顶面高度。
	 * 候选窗口为 [refY-3, refY+1]，避免从远处低高度射线样本误取脚下地面。
	 * 在窗口内全部候选中取离 anchor（射线终点/命中点）3D 距离最近的一个。
	 */
	private static Vec3d findBestInColumn(World world, PlayerEntity player, double exactX, double exactZ,
	                                       int bx, int bz, double refY, Vec3d eye, Vec3d anchor) {
		int topY = MathHelper.floor(refY) + 1;
		int bottomY = MathHelper.floor(refY) - 3;
		Vec3d best = null;
		double bestSq = Double.MAX_VALUE;
		for (int y = topY; y >= bottomY; y--) {
			BlockPos pos = new BlockPos(bx, y, bz);
			Double topYExact = collisionTopY(world, pos);
			if (topYExact == null) continue;
			if (!hasHeadroom(world, exactX, topYExact, exactZ)) continue;
			Vec3d candidate = new Vec3d(exactX, topYExact, exactZ);
			if (!isSafeLanding(world, player, candidate)) continue;
			if (!withinRange(eye, candidate)) continue;
			double sq = candidate.squaredDistanceTo(anchor);
			if (sq < bestSq) {
				bestSq = sq;
				best = candidate;
			}
		}
		return best;
	}

	/** 返回方块碰撞箱顶面世界 Y；空碰撞箱（空气/植物等）返回 null。支持楼梯/台阶。 */
	private static Double collisionTopY(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(world, pos);
		if (shape.isEmpty()) return null;
		double maxY = shape.getMax(Direction.Axis.Y);
		if (maxY <= 0) return null;
		return pos.getY() + maxY;
	}

	/**
	 * 检查 (x,z) 处脚位 feetY 上方约 1.8 格空气（玩家身高）。
	 * 用 floor(feetY + 0.5) 作为起点跳过平台本身（包括台阶/楼梯顶面）。
	 */
	private static boolean hasHeadroom(World world, double x, double feetY, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int startY = MathHelper.floor(feetY + 0.5);
		for (int dy = 0; dy < 2; dy++) {
			BlockPos p = new BlockPos(bx, startY + dy, bz);
			VoxelShape shape = world.getBlockState(p).getCollisionShape(world, p);
			if (!shape.isEmpty()) return false;
		}
		return true;
	}

	/** 落点距离硬限制（眼睛到落点直线距离）。 */
	private static boolean withinRange(Vec3d eye, Vec3d landing) {
		return eye.squaredDistanceTo(landing) <= (MAX_RANGE + 0.5) * (MAX_RANGE + 0.5);
	}

	/** 检查落点处玩家完整碰撞箱是否为空。 */
	private static boolean isSafeLanding(World world, PlayerEntity player, Vec3d feet) {
		Box box = playerBoxAt(player, feet.x, feet.y, feet.z);
		return world.isSpaceEmpty(player, box);
	}

	/** 落点必须在玩家视野内：眼睛→落点腰部的视线不被方块（含栅栏/墙）遮挡。用于杜绝平台模式隔障传送（#6）。 */
	private static boolean isLandingVisible(World world, PlayerEntity player, Vec3d targetFeet) {
		Vec3d eye = player.getEyePos();
		// 取落点身体中心高度作为可见性参考点（约 0.5~0.9 格高），避免只检查脚底被台阶误判
		Vec3d targetCenter = targetFeet.add(0, Math.min(0.9, Math.max(0.5, player.getHeight() / 2.0)), 0);
		BlockHitResult hit = world.raycast(new RaycastContext(
				eye, targetCenter,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));
		if (hit.getType() != HitResult.Type.BLOCK) return true;
		// 命中方块点比落点更近（留 0.25 容差）→ 视线被遮挡 → 落点不可见 → 拒绝传送
		return hit.getPos().squaredDistanceTo(eye) + 0.25 >= targetCenter.squaredDistanceTo(eye);
	}

	private static Box playerBoxAt(PlayerEntity player, double x, double y, double z) {
		double halfWidth = Math.max(0.3, player.getWidth() / 2.0);
		double height = Math.max(1.8, player.getHeight());
		return new Box(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth).contract(1.0E-7);
	}
}
