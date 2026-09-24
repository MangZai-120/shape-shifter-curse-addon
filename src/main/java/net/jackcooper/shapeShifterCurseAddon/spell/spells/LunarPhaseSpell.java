package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 月相（月辉系，蓝色基底，jackcooper，2026-09-24 用户两轮定稿）：
 * <b>两段交互</b>（同爆裂/领域规格）——按住施法键期间仅做本地预览：准星射线命中的实体
 * 实时紫色描边（零网络，跟随准星换目标）；<b>松手锁定目标后才开始 1.5 秒蓄力</b>
 * （custom 档 30t 禁动），蓄力期间施法者与目标周围各围一圈浅紫+灵魂沙鬼魂粒子小圈、
 * 两人之间连一根紫色粒子线（服务端每 2t 广播，代表「正在替换」）。
 * 蓄满自动完成按<b>百分比</b>交换当前生命——各自按自身最大生命换算，双方保底 1 点不致死。
 *
 * <p>无白名单（玩家/宠物/Boss 全部生效，用户定稿）。按住时准星无目标 → 松手释放失败
 * （不耗法力不进 CD）；蓄力期间目标死亡/远走 → 统一读条安全终止（不产生效果）。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/lunar_phase.json}：
 * 0 直伤 / 25 蓝 / 20s CD（400t）/ custom 档 30t 蓄力（目标锁定后起算）/ interrupt 3。</p>
 */
public class LunarPhaseSpell extends Spell {

	/** 实体瞄准最大距离（格）。 */
	public static final double AIM_RANGE = 24.0;
	public static final double AIM_TOLERANCE = 0.5;
	/** 描边颜色（紫色 RGB）。 */
	public static final int HIGHLIGHT_COLOR = 0xB26BD9;
	/** 蓄力时长（tick，1.5 秒）。 */
	public static final int CHANNEL_TICKS = 30;

	/** 蓄力中的目标表（施法者 UUID → 目标实体 UUID）：服务端权威，蓄力演出与结算共用。 */
	private static final java.util.Map<UUID, UUID> CHANNEL_TARGETS = new java.util.HashMap<>();

	public LunarPhaseSpell() {
		super(new Identifier("ssc_addon", "lunar_phase"), SpellRarity.WHITE); // NORMAL 五级品质走 JSON levels[].rarity（白→橙），基底回退白色
	}

	/** 两段交互：松手（锁定目标）后才创建施法会话开始蓄力（同爆裂 requiresTargetBeforeChannel）。 */
	@Override
	public boolean requiresTargetBeforeChannel() {
		return true;
	}

	@Override
	public net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.Mode getCastingMode() {
		return net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.Mode.RELEASE;
	}

	/** 蓄力阶段禁移动（custom 档：0 移速 + 禁跳，蓄力 1.5 秒「正在替换」仪式感）。 */
	@Override
	protected net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.Profile getCustomCastingProfile(
			ServerPlayerEntity caster, int level, boolean solo) {
		return new net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.Profile(CHANNEL_TICKS, 0, true);
	}

	/** 按住阶段目标捕获（松手时服务端权威实体 raycast，锁定进 Progress）。 */
	@Override
	public Vec3d captureCastTarget(ServerPlayerEntity caster, int level) {
		LivingEntity target = raycastEntity(caster);
		return target == null ? null : target.getPos();
	}

	/** 施法前置校验：按住松手时准星必须命中活体实体（无目标拒绝施放，不耗蓝不进 CD）。 */
	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		return raycastEntity(caster) != null;
	}

	/** 实体射线（双端一致几何）：眼位出发沿视向，命中最近活体实体；未命中/被墙挡返回 null。
	 *  手动迭代（Box.raycast 逐实体求交，2026-09-24 重写）：不再依赖 ProjectileUtil 重载的
	 *  maxDistance/命中位语义——旧实现把 AIM_RANGE² 传给 float 参数且用 getPos() 判墙距，
	 *  服务端判定可能恒空（「锁不到人」根因）。 */
	public static LivingEntity raycastEntity(PlayerEntity caster) {
		Vec3d eye = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(AIM_RANGE));
		HitResult blockHit = caster.getWorld().raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		double maxDist = blockHit.getType() == HitResult.Type.MISS
				? AIM_RANGE : eye.distanceTo(blockHit.getPos());
		LivingEntity best = null;
		double bestDist = maxDist; // 墙后目标不算：实体交点必须不晚于方块交点
		Box searchBox = caster.getBoundingBox().stretch(look.multiply(AIM_RANGE)).expand(1.0);
		for (Entity entity : caster.getWorld().getOtherEntities(caster, searchBox)) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) continue;
			if (DomainManager.blocksTargeting(caster, living)) continue;
			// Target nearby crosshair marks, but require actual line of sight and range.
			Box bounds = living.getBoundingBox();
			Vec3d visiblePoint = new Vec3d(
					net.minecraft.util.math.MathHelper.clamp(eye.x, bounds.minX, bounds.maxX),
					net.minecraft.util.math.MathHelper.clamp(eye.y, bounds.minY, bounds.maxY),
					net.minecraft.util.math.MathHelper.clamp(eye.z, bounds.minZ, bounds.maxZ));
			if (eye.distanceTo(visiblePoint) > AIM_RANGE || caster.getWorld().raycast(new RaycastContext(
					eye, visiblePoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster))
					.getType() != HitResult.Type.MISS) continue;
			Box aimBox = bounds.expand(AIM_TOLERANCE);
			var hit = aimBox.raycast(eye, end);
			if (hit.isPresent()) {
				double dist = eye.distanceTo(hit.get());
				if (dist <= bestDist) { bestDist = dist; best = living; }
			} else if (aimBox.contains(eye)) {
				// 目标贴脸包住眼位：Box.raycast 返回 empty，按零距离处理
				best = living;
				bestDist = 0.0;
			}
		}
		if (best == null) return null;
		return best;
	}

	/** 蓄力开始（目标已锁定）：登记目标表，起手音（附魔台轻响）。 */
	@Override
	public void onChannelStarted(ServerPlayerEntity caster, Vec3d target) {
		LivingEntity entity = findTargetAt(caster, target);
		if (entity == null) return; // 理论不可达（start 前 canCast 已验）：安全跳过
		CHANNEL_TARGETS.put(caster.getUuid(), entity.getUuid());
		spawnPreparationParticles(caster, 0);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.7f, 1.6f);
	}

	/** 蓄力期间持续校验：目标仍存活且在追踪距离内（死亡/远走 → 统一读条安全终止）。 */
	@Override
	public boolean canContinueCasting(ServerPlayerEntity caster, net.minecraft.item.ItemStack scroll) {
		UUID targetId = CHANNEL_TARGETS.get(caster.getUuid());
		if (targetId == null) return false;
		Entity target = caster.getServerWorld().getEntity(targetId);
		return target instanceof LivingEntity living && living.isAlive()
				&& !living.isSpectator() && !DomainManager.blocksTargeting(caster, living)
				&& caster.getPos().distanceTo(target.getPos()) <= AIM_RANGE + 8; // 容许目标小幅移动
	}

	/** 蓄力期间演出（每 2t）：双方围浅紫+鬼魂粒子小圈 + 两人之间紫色粒子连线。 */
	@Override
	public void tickChannel(ServerPlayerEntity caster, int level,
			net.minecraft.item.ItemStack scroll, int ticks) {
		spawnPreparationParticles(caster, ticks);
	}

	private static void spawnPreparationParticles(ServerPlayerEntity caster, int ticks) {
		if (!(caster.getWorld() instanceof ServerWorld world)) return;
		if (ticks % 2 != 0 || ticks >= CHANNEL_TICKS) return;
		UUID targetId = CHANNEL_TARGETS.get(caster.getUuid());
		Entity target = targetId == null ? null : world.getEntity(targetId);
		if (!(target instanceof LivingEntity living) || !living.isAlive()) return;
		// 浅紫+灵魂沙鬼魂小圈（双方脚下，半径 0.8 格 12 采样点）
		DustParticleEffect lilac = new DustParticleEffect(new Vector3f(0.85f, 0.65f, 0.95f), 1.0f);
		spawnRing(world, caster.getPos(), lilac, caster.age);
		spawnRing(world, target.getPos(), lilac, living.age);
		// 密集紫色粒子连线，随双方位置移动。
		Vec3d from = caster.getPos().add(0, caster.getHeight() * 0.6, 0);
		Vec3d to = target.getPos().add(0, living.getHeight() * 0.6, 0);
		double dist = from.distanceTo(to);
		int points = linkSegments(dist);
		DustParticleEffect link = new DustParticleEffect(new Vector3f(0.70f, 0.42f, 0.85f), 0.9f);
		for (int i = 0; i <= points; i++) {
			Vec3d p = from.lerp(to, i / (double) points);
			world.spawnParticles(link, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
		}
	}

	public static int linkSegments(double distance) {
		return Math.max(2, (int) Math.ceil(distance / 0.25));
	}

	/** 中断立即清理；正常结束由 cast 消费并清理目标。 */
	@Override
	public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {
		// Normal completion calls this BEFORE cast(); retain the target until settlement.
		if (interrupted) {
			CHANNEL_TARGETS.remove(caster.getUuid());
			caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.7f);
		}
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	/** 蓄满释放：与锁定的目标按百分比交换（服务端权威，重验存活）。 */
	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		UUID targetId = CHANNEL_TARGETS.remove(caster.getUuid());
		Entity entity = targetId == null ? null : caster.getServerWorld().getEntity(targetId);
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) return; // 安全终止（不产生效果）
		// 百分比交换：各自按自身最大生命换算，单侧变动上限 ±50 点（2026-09-24 用户定稿），
		// 保底 1 点不致死；50 = 25 颗心，防止对满血 Boss 一口吞掉大量生命
		float selfMax = caster.getMaxHealth(), targetMax = target.getMaxHealth();
		float selfRatio = caster.getHealth() / selfMax;
		float targetRatio = target.getHealth() / targetMax;
		float newSelf = swappedHealth(caster.getHealth(), selfMax, targetRatio);
		float newTarget = swappedHealth(target.getHealth(), targetMax, selfRatio);
		caster.setHealth(newSelf);
		target.setHealth(newTarget);
		// 完成音画：施法者附魔台音 + 目标信标音 + 双方脚下月辉粒子爆发环
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.9f, 1.6f);
		caster.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.8f);
		if (caster.getWorld() instanceof ServerWorld world) {
			spawnBurstRing(world, caster.getPos());
			spawnBurstRing(world, target.getPos());
		}
	}

	public static float swappedHealth(float current, float maximum, float otherRatio) {
		return Math.max(1.0F, Math.min(maximum,
				current + Math.max(-50.0F, Math.min(50.0F, otherRatio * maximum - current))));
	}

	/** 按锁定坐标重找目标（onChannelStarted 时 Progress 只存了位置）。 */
	private static LivingEntity findTargetAt(ServerPlayerEntity caster, Vec3d pos) {
		if (pos == null) return null;
		// Capture and start run in the same server task; never substitute a nearby entity.
		LivingEntity target = raycastEntity(caster);
		return target != null && target.getPos().squaredDistanceTo(pos) < 1.0e-6 ? target : null;
	}

	private static void spawnRing(ServerWorld world, Vec3d pos, DustParticleEffect dust, int seedBase) {
		for (int i = 0; i < 12; i++) {
			double angle = i * 2 * Math.PI / 12 + (seedBase % 20) * 0.1;
			world.spawnParticles(dust,
					pos.x + Math.cos(angle) * 0.8, pos.y + 0.1, pos.z + Math.sin(angle) * 0.8,
					1, 0.0, 0.03, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.SOUL, // 灵魂沙鬼魂粒子（间隔采样点缀）
					pos.x + Math.cos(angle) * 0.8, pos.y + 0.4, pos.z + Math.sin(angle) * 0.8,
					i % 3 == 0 ? 1 : 0, 0.0, 0.02, 0.0, 0.005);
		}
	}

	private static void spawnBurstRing(ServerWorld world, Vec3d pos) {
		var dust = new DustParticleEffect(new Vector3f(0.95f, 0.92f, 0.75f), 1.2f);
		for (int i = 0; i < 24; i++) {
			double angle = i * 2 * Math.PI / 24;
			world.spawnParticles(dust,
					pos.x + Math.cos(angle) * 0.9, pos.y + 0.1, pos.z + Math.sin(angle) * 0.9,
					1, 0.0, 0.05, 0.0, 0.0);
		}
	}
}
