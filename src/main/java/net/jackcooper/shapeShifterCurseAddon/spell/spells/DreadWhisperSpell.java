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
 * 基准锥长 6 格 / 锥角 60° / 虚弱 I + 缓速 II 6s / cd 14s / 耗蓝 18；
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
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, weaknessAmp));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, slownessAmp));
			// 击退：远离施法者
			Vec3d knock = new Vec3d(target.getX() - caster.getX(), 0.1, target.getZ() - caster.getZ())
					.normalize().multiply(0.6);
			target.addVelocity(knock.x, knock.y, knock.z);
			target.velocityModified = true;
			// 命中演出：暗紫烟雾
			serverWorld.spawnParticles(ParticleTypes.SMOKE,
					target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		}
		// 演出：锥形低语波（沿视线撒三层扩散环）+ 阴森音效
		for (int layer = 1; layer <= 3; layer++) {
			double dist = range * layer / 3.0;
			double layerRadius = Math.tan(Math.toRadians(HALF_ANGLE_DEG)) * dist;
			Vec3d center = origin.add(look.multiply(dist));
			spawnRing(serverWorld, ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, layerRadius, 10);
		}
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.2f, 0.7f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.6f, 1.4f);
	}

	/** 沿水平圆周均匀撒粒子（沿半径 radius，count 个）。 */
	private static void spawnRing(ServerWorld world, net.minecraft.particle.ParticleEffect particle,
	                              double x, double y, double z, double radius, int count) {
		for (int i = 0; i < count; i++) {
			double angle = 2 * Math.PI * i / count;
			world.spawnParticles(particle,
					x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
					1, 0.05, 0.05, 0.05, 0.01);
		}
	}
}
