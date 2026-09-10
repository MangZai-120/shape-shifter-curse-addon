package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * 霜甲术（冰系，蓝色基底，jackcooper）：给自己施加「伤害吸收」（黄心）——
 * 无伤害魔法，power 语义 = 期望吸收点数。
 *
 * <p>实现走原版 {@code AbsorptionStatusEffect} 的 amplifier 档位（amp n = 4×2ⁿ 点吸收，
 * onApplied/onRemoved 对称加减，无泄漏）：power 换算 amp = round(power/4)−1（clamp 0-4），
 * 即 L1 基准 4 点（2 黄心）、高等级/法阵加成后可上探档位。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/frost_armor.json}：
 * 基准 4 吸收 / 持续 20s / cd 15s / 耗蓝 25。重复施放刷新时长（同类效果取新实例）。</p>
 */
public class FrostArmorSpell extends Spell {

	/** 吸收持续时间（tick）：20s。 */
	private static final int DURATION_TICKS = 400;
	/** amplifier 上限（amp4 = 64 点 = 32 黄心，防数据包写飞）。 */
	private static final int MAX_ABSORPTION_AMPLIFIER = 4;

	public FrostArmorSpell() {
		super(new Identifier("ssc_addon", "frost_armor"), SpellRarity.BLUE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		// power → 原版吸收档位：amp = round(power/4) - 1（4点=amp0 2黄心、8点=amp1 4黄心…）
		int amplifier = Math.max(0, Math.min(MAX_ABSORPTION_AMPLIFIER,
				Math.round(power / 4.0f) - 1));
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, DURATION_TICKS, amplifier));
		// 演出：寒气缠绕 + 冰晶盾碎裂音效
		if (caster.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE,
					caster.getX(), caster.getY() + 1.0, caster.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
			serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.6f);
			serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.8f, 1.4f);
		}
	}

	@Override
	public String getInBookTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_in_book_absorb";
	}

	@Override
	public String getSoloTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_solo_absorb";
	}
}
