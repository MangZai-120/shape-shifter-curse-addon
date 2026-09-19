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
 * <p>实现：先加原版 {@code AbsorptionStatusEffect}（取不超过目标量的档位，供图标/到期语义），
 * 再直接 {@code setAbsorptionAmount(max(current, power))} 精确写入黄心量（原版档位只有 4×2ⁿ，
 * 无法表达 20/24 等任意值）；用 max 防止低级刷新把已有更高黄心覆盖拉低。
 * 吸收量到期后残留至受击扣光是原版金苹果同款行为。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/frost_armor.json}：
 * 基准 8 吸收（4 黄心）/ 持续 20s / cd 30s 每级 -2s / 耗蓝 25 每级 ×1.2（复利，与月辉诅咒系 buff 类一致）；
 * L1-L5 = 吸收 8/12/16/20/24 点（4/6/8/10/12 黄心），耗蓝 25/30/36/43/52，cd 30/28/26/24/22s。
 * 重复施放刷新时长（吸收量取 max，不叠加）。</p>
 */
public class FrostArmorSpell extends Spell {

	/** 吸收持续时间（tick）：20s。 */
	private static final int DURATION_TICKS = 400;
	/** amplifier 上限（防数据包写飞）。 */
	private static final int MAX_ABSORPTION_AMPLIFIER = 4;

	public FrostArmorSpell() {
		super(new Identifier("ssc_addon", "frost_armor"), SpellRarity.BLUE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		// 档位 = 不超过目标量的最大 2 幂档（onApplied 提到 4<<amp，不会超过 power）；
		// power<4 时 log 为负 → clamp 0。随后精确直写吸收量（max 防低级拉低已有高黄心）。
		int amplifier = Math.max(0, Math.min(MAX_ABSORPTION_AMPLIFIER,
				(int) Math.floor(Math.log(power / 4.0f) / Math.log(2.0))));
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, DURATION_TICKS, amplifier));
		caster.setAbsorptionAmount(Math.max(caster.getAbsorptionAmount(), power));
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
