package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorAccess;
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
 * <p>独立霜甲效果计时，吸收池补至 {@code max(current, power)}，重复施放不叠加。
 * 精确记录本法术补入的份额，受伤先消耗霜甲；到期或效果被移除时只移除霜甲剩余的份额。
 * 不改写金苹果等其他吸收效果的等级和持续时间。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/frost_armor.json}：
 * 基准 8 吸收（4 黄心）/ 持续 20s / cd 25s 每级 -2.5s / 耗蓝 25 每级 ×1.2（复利，与月辉诅咒系 buff 类一致）；
 * L1-L5 = 吸收 8/12/16/20/24 点（4/6/8/10/12 黄心），耗蓝 25/30/36/43/52，cd 25/22.5/20/17.5/15s。
 * 重复施放刷新时长（吸收量取 max，不叠加）。</p>
 */
public class FrostArmorSpell extends Spell {

	/** 吸收持续时间（tick）：20s。默认值；运行时从 balance 快照读取。 */
	private static final int DURATION_TICKS = 400;

	// 阶段 5：运行时快照读取（spells.frost_armor；快照未初始化回退上方默认常量。
	// 持续时间和吸收量均不借用原版吸收效果的整数档位）
	private static final BalanceReader BAL = new BalanceReader("spells.frost_armor");

	public FrostArmorSpell() {
		super(new Identifier("ssc_addon", "frost_armor"), SpellRarity.BLUE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		caster.addStatusEffect(new StatusEffectInstance(SscAddon.FROST_ARMOR,
				RuneCastContext.current().duration(BAL.i("duration_ticks", DURATION_TICKS),RuneModifiers.Stat.SHIELD_DURATION), 0));
		if (caster.hasStatusEffect(SscAddon.FROST_ARMOR)) ((FrostArmorAccess) caster).ssca$frostArmor().grant(caster, power);
		// 演出：寒气缠绕 + 冰晶盾碎裂音效
		if (caster.getWorld() instanceof ServerWorld serverWorld) {
			net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(serverWorld, caster, ParticleTypes.SNOWFLAKE,
					caster.getX(), caster.getY() + 1.0, caster.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
			serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.6f);
			serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.8f, 1.4f);
		}
	}

}
