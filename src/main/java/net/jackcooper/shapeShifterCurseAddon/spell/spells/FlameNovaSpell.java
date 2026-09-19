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
 * 基准 4 伤 / 半径 4 格 / cd 6s / 耗蓝 20 逐级 ×1.25（复利，20/25/31/39/49）；
 * 半径按 speed_multiplier 缩放（每级 +0.5 格），
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
		LivingEntity lastHitTarget = null;
		LivingEntity killedTarget = null;
		boolean hitBurningTarget = false;
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > radius) {
				continue;
			}
			// 默认白名单：受保护目标免伤
			if (WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			// 法术伤害专用类型（ssc_addon:spell_damage）：供法术抗性附魔精确识别（jackcooper）
			if (target.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource
					.of(serverWorld.getDamageSources(), caster, caster), power)) {
				// exp_mode 1/2 命中补发：首个命中目标取全额（后续取 0，幂等），发放后挂起清零
				net.jackcooper.shapeShifterCurseAddon.spell.SpellExpGrant.grant(caster,
						solo ? 0 : ssc_addon$takePendingExp());
				lastHitTarget = target;
				hitBurningTarget |= target.getFireTicks() > 0;
				if (!target.isAlive()) killedTarget = target;
			}
			target.setFireTicks(FIRE_TICKS);
			// 击退：远离施法者
			Vec3d knock = new Vec3d(target.getX() - caster.getX(), 0.1, target.getZ() - caster.getZ())
					.normalize().multiply(0.8);
			target.addVelocity(knock.x, knock.y, knock.z);
			target.velocityModified = true;
		}
		if (lastHitTarget != null) {
			net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(
					caster, killedTarget != null ? killedTarget : lastHitTarget,
					net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.FIRE,
					solo ? null : ssc_addon$getRefundCastId(), hitBurningTarget);
		}
		// 演出：球形火焰粒子（双层球面 + 烟火）+ 音效
		spawnSphere(serverWorld, caster.getX(), caster.getY() + 1.0, caster.getZ(), radius);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.7f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.9f);
	}

	/**
	 * 演出：球形火焰（两层球面 + 内部火花），以头部高度为球心向外爆开。
	 */
	private static void spawnSphere(ServerWorld world, double cx, double cy, double cz, double radius) {
		// 外层球面：FLAME 沿球面均匀分布（按表面积近似均匀采样）
		int outerCount = (int) Math.max(24, radius * radius * 12);
		for (int i = 0; i < outerCount; i++) {
			double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / outerCount);   // 极角均匀
			double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;          // 黄金角方位
			double x = Math.sin(phi) * Math.cos(theta);
			double y = Math.cos(phi);
			double z = Math.sin(phi) * Math.sin(theta);
			world.spawnParticles(ParticleTypes.FLAME,
					cx + x * radius, cy + y * radius, cz + z * radius,
					1, 0.02, 0.02, 0.02, 0.001);
		}
		// 内层球面（0.65 倍半径）：小体积 LAVA 火花，增加厚度感
		int innerCount = outerCount / 2;
		double innerR = radius * 0.65;
		for (int i = 0; i < innerCount; i++) {
			double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / innerCount);
			double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
			double x = Math.sin(phi) * Math.cos(theta);
			double y = Math.cos(phi);
			double z = Math.sin(phi) * Math.sin(theta);
			world.spawnParticles(ParticleTypes.LAVA,
					cx + x * innerR, cy + y * innerR, cz + z * innerR,
					1, 0.02, 0.02, 0.02, 0.001);
		}
		// 中心烟火：中距离蘑菇云状烟尘填充
		world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
				cx, cy + 0.3, cz, 6, radius * 0.3, 0.2, radius * 0.3, 0.01);
	}
}
