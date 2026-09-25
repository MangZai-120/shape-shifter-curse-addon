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

import java.util.List;

/**
 * 烈焰新星（火系，绿色基底，jackcooper）：以自身为圆心爆发火环，范围内造成伤害 + 击退 + 点燃 2s。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/flame_nova.json}：
 * 基准 4 伤 / 半径 4 格 / cd 12s / 耗蓝 20 逐级 ×1.25（复利，20/25/31/39/49）；
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
			// 公共命中结算（白名单豁免 → 法术伤害 → 经验补发；FIRE 系钩子为聚合语义，循环外调）：见 SpellHitHelper
			var hit = net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.hitRaw(
					caster, target, power, solo ? 0 : ssc_addon$takePendingExp());
			if (hit != net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.HitResult.HIT) {
				continue;
			}
			lastHitTarget = target;
			hitBurningTarget |= target.getFireTicks() > 0;
			if (!target.isAlive()) killedTarget = target;
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
		net.jackcooper.shapeShifterCurseAddon.util.SpellFxUtils.sphere(serverWorld, caster,
				caster.getX(), caster.getY() + 1.0, caster.getZ(), radius,
				ParticleTypes.FLAME, ParticleTypes.LAVA, ParticleTypes.CAMPFIRE_COSY_SMOKE, 6);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.7f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.9f);
	}
}
