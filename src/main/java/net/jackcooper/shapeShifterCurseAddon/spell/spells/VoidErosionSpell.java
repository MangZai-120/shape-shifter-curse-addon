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

import java.util.List;

/**
 * 虚空侵蚀（虚无系，绿色基底，jackcooper）：以自身为圆心爆发虚无波，
 * 范围内敌人挖掘疲劳 + 虚弱 8s（对怪物对玩家均生效，走白名单）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/void_erosion.json}：
 * 基准半径 3 格 / 8s / cd 16s / 耗蓝 20；半径按 speed_multiplier 缩放。</p>
 */
public class VoidErosionSpell extends Spell {

	/** 基础半径（格），实际半径 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 3.0;
	/** 减益时长（tick）：8s。 */
	private static final int DURATION_TICKS = 160;

	public VoidErosionSpell() {
		super(new Identifier("ssc_addon", "void_erosion"), SpellRarity.GREEN);
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
		double radius = BASE_RADIUS * getSpeedMultiplier(level);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(radius), e -> e != caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > radius) {
				continue;
			}
			// 默认白名单：受保护目标免受减益
			if (WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			// 减益每两级 +1 级：L1/L2=疲劳 I + 虚弱 II、L3/L4=II + III、L5=III + IV
			int debuffAmplifier = (level - 1) / 2;
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, DURATION_TICKS, debuffAmplifier));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, DURATION_TICKS, 1 + debuffAmplifier));
		}
		// 演出：暗紫侵蚀波纹（双圈）+ 低沉音效
		spawnRing(serverWorld, ParticleTypes.PORTAL, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius * 0.6, 20);
		spawnRing(serverWorld, ParticleTypes.PORTAL, caster.getX(), caster.getY() + 0.4, caster.getZ(), radius, 28);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.6f);
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
