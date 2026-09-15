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
 * 腐蚀之雾（诅咒系，蓝色基底，jackcooper）：以自身为圆心散布腐蚀雾气，范围内敌人
 * 持续每 2 秒受毒伤（中毒 I）+ 缓速，雾气持续 6 秒。即时施放，持续结算走
 * 服务端 tick 管理器（{@code CorruptMistManager}，按 UUID 隔离）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/corrupt_mist.json}：
 * 基准半径 3 格 / 持续 6s（每 2s 一跳中毒 I 4s + 缓速 I 2s）/ cd 18s / 耗蓝 28；
 * 半径按 speed_multiplier 缩放。</p>
 */
public class CorruptMistSpell extends Spell {

	/** 基础半径（格），实际半径 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 3.0;
	/** 雾气持续时间（tick）：6s。 */
	public static final int DURATION_TICKS = 120;
	/** 每跳间隔（tick）：2s。 */
	public static final int INTERVAL_TICKS = 40;
	/** 中毒时长（tick）：4s。 */
	public static final int POISON_TICKS = 80;

	public CorruptMistSpell() {
		super(new Identifier("ssc_addon", "corrupt_mist"), SpellRarity.BLUE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		if (caster.getWorld().isClient() || !(caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		double radius = BASE_RADIUS * getSpeedMultiplier(level);
		// 雾持续时间：每级 +1.25s（L1=6s … L5=11s）
		int duration = DURATION_TICKS + Math.round((level - 1) * 1.25f * 20);
		// 注册持续雾气区域（服务端 tick 结算，见 CorruptMistManager；L4+ 中毒 II）
		net.jackcooper.shapeShifterCurseAddon.ability.CorruptMistManager.start(
				caster, radius, duration, INTERVAL_TICKS, level);
		// 起手演出：紫色雾气扩散 + 酸蚀音效
		for (int layer = 1; layer <= 3; layer++) {
			double r = radius * layer / 3.0;
			spawnRing(serverWorld, ParticleTypes.DRAGON_BREATH, caster.getX(), caster.getY() + 0.15, caster.getZ(), r, 16);
		}
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.PLAYERS, 0.8f, 0.5f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.7f, 0.6f);
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
