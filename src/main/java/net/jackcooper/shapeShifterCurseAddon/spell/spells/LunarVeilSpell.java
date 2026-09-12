package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
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
 * 月幕（月辉系，绿色基底，jackcooper）：以自身为圆心展开月光辉幕，范围内友方
 * （自己 + 未受白名单保护的玩家/驯服宠物）获得抗性 + 缓降，持续一段时间的即时群体增益。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/lunar_veil.json}：
 * 基准半径 3 格 / 抗性 I 8s + 缓降 I 8s / cd 20s / 耗蓝 25；
 * 半径按 speed_multiplier 缩放（与新星家族一致）。</p>
 *
 * <p>白名单语义：受保护目标（白名单内玩家与宠物）同样受益——增益类法术不做排除。</p>
 */
public class LunarVeilSpell extends Spell {

	/** 基础半径（格），实际半径 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 3.0;
	/** 增益时长（tick）：8s。 */
	private static final int DURATION_TICKS = 160;

	public LunarVeilSpell() {
		super(new Identifier("ssc_addon", "lunar_veil"), SpellRarity.GREEN);
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
		// 增益时长：每级 +1s（L1=9s … L5=13s）；L4+ 增益升 II 级（抗性 II + 缓降 II）
		int duration = DURATION_TICKS + (level - 1) * 20;
		int amplifier = level >= 4 ? 1 : 0;
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(radius), e -> e != caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > radius) {
				continue;
			}
			// 增益目标筛选：玩家（含白名单）与驯服宠物；敌对怪物不受益
			boolean isPlayer = target instanceof net.minecraft.entity.player.PlayerEntity;
			boolean isTamed = target instanceof net.minecraft.entity.passive.TameableEntity tameable
					&& tameable.isTamed();
			if (!isPlayer && !isTamed) {
				continue;
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, amplifier));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, duration, amplifier));
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					target.getX(), target.getBodyY(0.8), target.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
		// 自己也享受
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, amplifier));
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, duration, amplifier));
		// 演出：月幕粒子环 + 空灵音效
		spawnRing(serverWorld, ParticleTypes.END_ROD, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius, 24);
		spawnRing(serverWorld, ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.5, caster.getZ(), radius * 0.7, 12);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.6f);
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
