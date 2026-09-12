package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * 虚空吞噬（虚无系，蓝色基底，jackcooper）：按住施法键在准星落点显示瞄准圈（最远 16 格，
 * 同陨火术交互），松开在落点爆发虚无吞噬——落点 AOE 伤害 + 失明，准星未命中方块时拒绝施放。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/void_devour.json}：
 * 基准 6 伤 + 失明 3s（每两级 +1s）/ 射程 16 格（按 speed_multiplier 缩放）/ cd 8s / 耗蓝 22。</p>
 */
public class VoidDevourSpell extends Spell {

	/** 最大施法距离（格），实际 = 基础 × speed_multiplier(level)——按住瞄准预览与施法共用。 */
	private static final double BASE_RANGE = 16.0;
	/** 落点 AOE 半径（格）：中心直射判定，预览圈同步显示。 */
	private static final double IMPACT_RADIUS = 2.0;
	/** 基础失明时长（tick）：3s，每两级 +1s（L1/L2=3s、L3/L4=4s、L5=5s）。 */
	private static final int BASE_BLINDNESS_TICKS = 60;

	public VoidDevourSpell() {
		super(new Identifier("ssc_addon", "void_devour"), SpellRarity.BLUE);
	}

	/** 按住瞄准型：最大施法距离（含等级缩放；客户端按住施法键显示落点预览圈，松开施放）。 */
	@Override
	public double getAimMaxRange() {
		return BASE_RANGE; // 预览距离基准；等级缩放在 getBlinkRange 式调用点乘 speed_multiplier
	}

	/** 预览圈半径 = 落点 AOE 半径（与服务端实际伤害范围一致）。 */
	@Override
	public double getAimRadius(int level) {
		return IMPACT_RADIUS;
	}

	/** 实际有效射程（含等级缩放）。 */
	private double effectiveRange(int level) {
		return BASE_RANGE * getSpeedMultiplier(level);
	}

	/** 施法前置校验：落点必须命中方块（指天/超距 → 拒绝，不耗法力/CD）。 */
	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		return Spell.computeAimImpact(caster, effectiveRange(1)) != null;
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
		// 落点求取（与客户端按住预览同一几何）：必须命中方块；null → 中止施放
		net.minecraft.util.math.Vec3d impact = Spell.computeAimImpact(caster, effectiveRange(level));
		if (impact == null) {
			return;
		}
		// 落点 AOE：半径 IMPACT_RADIUS 内敌人受伤害 + 失明（白名单免伤）
		var entities = serverWorld.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
				caster.getBoundingBox().expand(effectiveRange(level) + IMPACT_RADIUS),
				e -> e != caster && e.isAlive());
		int hitCount = 0;
		for (var target : entities) {
			if (target.getPos().add(0, target.getHeight() / 2, 0).distanceTo(impact) > IMPACT_RADIUS) {
				continue;
			}
			if (net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			target.damage(serverWorld.getDamageSources().indirectMagic(caster, caster), power);
			target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
					net.minecraft.entity.effect.StatusEffects.BLINDNESS,
					BASE_BLINDNESS_TICKS + ((level - 1) / 2) * 20, 0));
			hitCount++;
			// 命中演出：虚空爆裂
			serverWorld.spawnParticles(ParticleTypes.SMOKE,
					target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		}
		// 落点演出：暗紫湮灭圈（双圈 + 中心聚集）
		spawnRing(serverWorld, ParticleTypes.PORTAL, impact.x, impact.y + 0.2, impact.z, IMPACT_RADIUS * 0.6, 16);
		spawnRing(serverWorld, ParticleTypes.PORTAL, impact.x, impact.y + 0.4, impact.z, IMPACT_RADIUS, 24);
		serverWorld.spawnParticles(ParticleTypes.PORTAL,
				impact.x, impact.y + 0.5, impact.z, 20, 0.3, 0.5, 0.3, 0.15);
		serverWorld.playSound(null, impact.x, impact.y, impact.z,
				SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.PLAYERS, 0.8f, 0.7f);
		if (hitCount > 0) {
			serverWorld.playSound(null, impact.x, impact.y, impact.z,
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5f, 1.6f);
		}
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
