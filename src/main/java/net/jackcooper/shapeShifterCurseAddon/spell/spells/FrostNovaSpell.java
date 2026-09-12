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
 * 冰霜新星（冰系，绿色基底，jackcooper）：以自身为圆心爆发寒气，范围内造成伤害 + 缓速 II 4s。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/frost_nova.json}：
 * 基准 3 伤 / 半径 4 格 / cd 5s / 耗蓝 15；半径按 speed_multiplier 缩放（每级 +0.5 格），
 * 稀有度为蓝/橙时额外 +25%。
 * 纯控制向：不点燃（对比烈焰新星），缓速时长随等级微涨（L1 4s → L5 6s）。
 * 白名单：主人在线且目标受保护 → 免伤；施法者本人不受影响。</p>
 */
public class FrostNovaSpell extends Spell {

	/** 基础半径（格），实际半径 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 4.0;

	public FrostNovaSpell() {
		super(new Identifier("ssc_addon", "frost_nova"), SpellRarity.GREEN);
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
		// 稀有度为蓝/橙时，生效范围额外 +25%（独立于等级缩放，数据包改 rarity 自动跟随）
		SpellRarity rarity = getRarity(level);
		if (rarity == SpellRarity.BLUE || rarity == SpellRarity.ORANGE) {
			radius *= 1.25;
		}
		// 缓速 II，时长 4s（L1-2）/ 5s（L3-4）/ 6s（L5）
		int slowTicks = 80 + (level >= 3 ? 20 : 0) + (level >= 5 ? 20 : 0);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(radius), e -> e != caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > radius) {
				continue;
			}
			// 默认白名单：受保护目标免伤
			if (WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			target.damage(serverWorld.getDamageSources().indirectMagic(caster, caster), power);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, 1));
		}
		// 演出：雪粒环 + 寒气音效
		spawnRing(serverWorld, ParticleTypes.SNOWFLAKE, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius * 0.6, 24);
		spawnRing(serverWorld, ParticleTypes.SNOWFLAKE, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius, 32);
		spawnRing(serverWorld, ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.3, caster.getZ(), radius * 0.8, 12);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 0.6f);
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
