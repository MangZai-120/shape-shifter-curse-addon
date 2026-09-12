package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.List;

/**
 * 烈焰新星（火系，绿色基底，jackcooper）：以自身为圆心爆发火环，范围内造成伤害 + 击退 + 点燃 2s。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/flame_nova.json}：
 * 基准 4 伤 / 半径 4 格 / cd 6s / 耗蓝 20；半径按 speed_multiplier 缩放（每级 +0.5 格），
 * 稀有度为蓝/橙时额外 +25%。
 * 白名单：主人在线且目标受保护 → 免伤；施法者本人不受影响。</p>
 */
public class FlameNovaSpell extends Spell {

	/** 基础半径（格），实际半径 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 4.0;
	/** 点燃时长（tick）。 */
	private static final int FIRE_TICKS = 40;

	public FlameNovaSpell() {
		super(new Identifier("ssc_addon", "flame_nova"), SpellRarity.GREEN);
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
			target.setFireTicks(FIRE_TICKS);
			// 击退：远离施法者
			Vec3d knock = new Vec3d(target.getX() - caster.getX(), 0.1, target.getZ() - caster.getZ())
					.normalize().multiply(0.8);
			target.addVelocity(knock.x, knock.y, knock.z);
			target.velocityModified = true;
		}
		// 演出：环形火焰粒子（双圈）+ 音效
		spawnRing(serverWorld, ParticleTypes.FLAME, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius * 0.6, 24);
		spawnRing(serverWorld, ParticleTypes.FLAME, caster.getX(), caster.getY() + 0.2, caster.getZ(), radius, 32);
		spawnRing(serverWorld, ParticleTypes.LARGE_SMOKE, caster.getX(), caster.getY() + 0.4, caster.getZ(), radius * 0.8, 16);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.7f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.9f);
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
