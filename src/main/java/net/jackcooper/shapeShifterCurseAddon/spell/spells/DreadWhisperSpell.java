package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 恐惧低语（诅咒系，绿色基底，jackcooper）：向准星方向发出锥形恐惧波，
 * 范围内敌人被虚弱 + 缓速 + 击退，零伤害纯控场。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/dread_whisper.json}：
 * 基准锥长 6 格 / 锥角 60° / 虚弱 I + 缓速 II 6s / cd 20s（L5 10s，每级 -2.5s）/ 耗蓝 18；
 * 锥长按 speed_multiplier 缩放。</p>
 */
public class DreadWhisperSpell extends Spell {

	/** 基础锥长（格），实际 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RANGE = 6.0;
	/** 锥形半角（度）。 */
	private static final double HALF_ANGLE_DEG = 30.0;
	/** 控场时长（tick）：6s。 */
	private static final int DURATION_TICKS = 120;

	public DreadWhisperSpell() {
		super(new Identifier("ssc_addon", "dread_whisper"), SpellRarity.GREEN);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		if (!(caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		double range = BASE_RANGE * getSpeedMultiplier(level);
		// 控场时长：每级 +1s（L1=7s … L5=11s）；L3+ 升级为虚弱 II + 缓速 III
		int duration = DURATION_TICKS + (level - 1) * 20;
		if (!solo) duration = net.jackcooper.shapeShifterCurseAddon.spell.FormAffinity.curseDurationTicks(caster, duration);
		int weaknessAmp = level >= 3 ? 1 : 0;
		int slownessAmp = level >= 3 ? 2 : 1;
		Vec3d look = caster.getRotationVec(1.0F).normalize();
		Vec3d origin = caster.getEyePos();
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(range), e -> e != caster && e.isAlive());
		for (LivingEntity target : targets) {
			// 锥形判定：距离 + 与视线夹角 ≤ 半角
			Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0).subtract(origin);
			if (toTarget.length() > range) {
				continue;
			}
			double cosAngle = look.dotProduct(toTarget.normalize());
			if (cosAngle < Math.cos(Math.toRadians(HALF_ANGLE_DEG))) {
				continue;
			}
			// 默认白名单：受保护目标免受控场
			if (WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			boolean hadHarmfulEffect = net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.hasHarmfulEffect(target);
			boolean applied = target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, weaknessAmp));
			applied |= target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, slownessAmp));
			if (applied) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(caster, target,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.CURSE,
						solo ? null : ssc_addon$getRefundCastId(), hadHarmfulEffect);
			}
			// 击退：远离施法者
			Vec3d knock = new Vec3d(target.getX() - caster.getX(), 0.1, target.getZ() - caster.getZ())
					.normalize().multiply(0.6);
			target.addVelocity(knock.x, knock.y, knock.z);
			target.velocityModified = true;
			// 命中演出：暗紫烟雾
			serverWorld.spawnParticles(ParticleTypes.SMOKE,
					target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		}
		// 演出：锥形低语波——沿视线方向的锥面螺旋采样（贴合真实锥形判定几何：
		// 距离 d 处锥面半径 = tan(半角)×d，点随距离扩散；随视线俯仰，不再是水平环）
		spawnCone(serverWorld, origin, look, range, HALF_ANGLE_DEG);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 2.0f, 0.7f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 1.4f);
	}

	/**
	 * 锥面螺旋粒子：沿视线分段（每 0.6 格一段），每段在锥面上绕行撒点；
	 * 段半径 = tan(半角)×段距，角度随段号旋转（螺旋感）+ 每段多点均匀铺开锥面。
	 * 微速度让粒子略向外飘（近似扩散感；ServerWorld.spawnParticles 无定向初速重载）。
	 */
	private static void spawnCone(ServerWorld world, Vec3d origin, Vec3d look, double range, double halfAngleDeg) {
		double tanHalf = Math.tan(Math.toRadians(halfAngleDeg));
		// 视线的正交基（右/上），用于把锥面参数化到世界空间
		Vec3d right = new Vec3d(-look.z, 0, look.x).normalize(); // 水平垂直向量（已去除俯仰分量）
		if (right.lengthSquared() < 1.0e-4) {
			right = new Vec3d(1, 0, 0); // 正上/正下看时兑底
		}
		Vec3d up = right.crossProduct(look).normalize();
		int segments = Math.max(6, (int) (range / 0.6));
		for (int s = 1; s <= segments; s++) {
			double dist = range * s / segments;
			double ringR = tanHalf * dist;
			int perRing = 4 + s / 2; // 近处稀、远处密（锥面展开面积变大）
			double spin = s * 0.7;    // 段间旋转，螺旋感
			for (int i = 0; i < perRing; i++) {
				double ang = spin + 2 * Math.PI * i / perRing;
				double cosA = Math.cos(ang), sinA = Math.sin(ang);
				// 锥面点 = 轴上点 + 半径方向偏移（right·cos + up·sin）
				Vec3d point = origin.add(look.multiply(dist))
						.add(right.multiply(cosA * ringR)).add(up.multiply(sinA * ringR));
				world.spawnParticles(ParticleTypes.SCULK_SOUL,
						point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.005);
			}
			// 锥轴中心粒子（灵魂粒子沿轴前飞的引导感）
			Vec3d axisPoint = origin.add(look.multiply(dist));
			world.spawnParticles(ParticleTypes.SOUL,
					axisPoint.x, axisPoint.y, axisPoint.z, 1, 0.02, 0.02, 0.02, 0.01);
		}
	}
}
