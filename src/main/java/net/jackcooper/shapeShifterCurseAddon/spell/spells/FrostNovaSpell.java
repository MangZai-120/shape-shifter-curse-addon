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
			// 公共命中结算（白名单豁免 → 法术伤害 → 经验补发 → 流派钩子逐目标；ICE 系无击杀分支）：见 SpellHitHelper
			var hit = net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.projectileHit(
					caster, target, power, net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.ICE,
					solo ? null : ssc_addon$getRefundCastId(), solo ? 0 : ssc_addon$takePendingExp());
			if (hit != net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper.HitResult.HIT) {
				continue;
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, 1));
			// 霜碎：护甲 -50%，时长与缓速同步（L1=3s → L5=5s）
			target.addStatusEffect(new StatusEffectInstance(
					net.jackcooper.shapeShifterCurseAddon.SscAddon.FROST_SHATTER, slowTicks, 0));
		}
		// 演出：球形寒气粒子（双层雪花球面 + 内部云雾）+ 寒气音效（与烈焰新星同款球形演出）
		net.jackcooper.shapeShifterCurseAddon.util.SpellFxUtils.sphere(serverWorld,
				caster.getX(), caster.getY() + 1.0, caster.getZ(), radius,
				ParticleTypes.SNOWFLAKE, ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD, 8);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0f, 0.8f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 0.6f);
	}
}
