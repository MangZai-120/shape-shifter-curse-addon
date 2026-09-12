package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * 月华治愈（月辉系，白色基底，jackcooper）：净化自身——回复生命 + 移除一个负面状态效果。
 * 无伤害魔法，power 语义 = 回复生命值。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/lunar_mend.json}：
 * 基准回 5 HP / cd 12s / 耗蓝 20；L3 起额外移除第二个负面效果。</p>
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
		// 回复生命（不含再生，即时治疗语义）
		caster.heal(power);
		// 移除负面效果：每级 +1 个（L1=1 … L5=5）
		int removeCount = level;
		int removed = 0;
		for (StatusEffectInstance instance : caster.getStatusEffects()) {
			if (removed >= removeCount) {
				break;
			}
			if (instance.getEffectType().getCategory() == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL) {
				caster.removeStatusEffect(instance.getEffectType());
				removed++;
			}
		}
		// 演出：月光洒落 + 治愈音效
		if (caster.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					caster.getX(), caster.getY() + 1.2, caster.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
			serverWorld.spawnParticles(ParticleTypes.HEART,
					caster.getX(), caster.getY() + 1.0, caster.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
					SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
		}
	}

	@Override
	public String getInBookTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_in_book_heal";
	}

	@Override
	public String getSoloTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_solo_heal";
	}
}
