package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Comparator;

/**
 * 月华治愈（月辉系，白色基底，jackcooper）：回复生命，按法术等级净化自身负面效果。
 * 无伤害魔法，power 语义 = 回复生命值。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/lunar_mend.json}：
 * 基准回 5 HP / cd 12s / 耗蓝 20。每解除一级负面效果消耗一点净化预算。</p>
 */
public class LunarMendSpell extends Spell {

	public LunarMendSpell() {
		super(new Identifier("ssc_addon", "lunar_mend"), SpellRarity.WHITE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		// 蝙蝠「血契」配套：月华治愈效果 +25%（吸血蝙蝠主题，2026-09-17）
		power = net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.lunarMendBonus(caster, power);
		// 回复生命（不含再生，即时治疗语义）
		if (net.jackcooper.shapeShifterCurseAddon.spell.NeoLegacyCasting.reversed()) {
            caster.damage(caster.getDamageSources().magic(), power);
            return;
        }
        net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.heal(caster,caster,power);
		// 净化预算制（2026-09-17）：等级 = 预算点数（L1=1 … L5=5）；
		// 移除一个 amplifier=n 的负面效果消耗 n+1 点（一级 debuff 消耗 1，二级消耗 2，以此类推）；
		// 预算不足以移除下一个（如剩 1 点遇到二级）则跳过继续找可移除的，预算耗尽停止。
		int budget = level;
		// 不同游戏进程的效果集合迭代顺序可能不同。用注册标识固定预算的分配顺序。
		var harmful = caster.getStatusEffects().stream()
				.filter(instance -> instance.getEffectType().getCategory() == StatusEffectCategory.HARMFUL)
				.sorted(Comparator.comparing(instance -> Registries.STATUS_EFFECT.getId(instance.getEffectType())))
				.toList();
		for (StatusEffectInstance instance : harmful) {
			if (budget <= 0) {
				break;
			}
			int cost = instance.getAmplifier() + 1;
			if (cost <= budget) {
				caster.removeStatusEffect(instance.getEffectType());
				budget -= cost;
			}
		}
		// 演出：月光洒落 + 治愈音效
		if (caster.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					caster.getX(), caster.getY() + 1.2, caster.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
			serverWorld.spawnParticles(ParticleTypes.HEART,
					caster.getX(), caster.getY() + 1.0, caster.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			AddonSoundRange.play(serverWorld, null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
		}
	}

}
