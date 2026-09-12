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
 * 空间漫步（空间系，白色基底，jackcooper）：探索向群体增益——缓降 20s +
 * 跳跃提升 II 10s（自己；随等级延长）。纯机动辅助，无伤害。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/space_stride.json}：
 * 基准缓降 20s / 跳升 II 10s / cd 20s / 耗蓝 12；时长按等级递增。</p>
 */
public class SpaceStrideSpell extends Spell {

	/** 基础缓降时长（tick）：20s。 */
	private static final int SLOW_FALL_TICKS = 400;
	/** 跳升时长（tick）：10s。 */
	private static final int JUMP_TICKS = 200;

	public SpaceStrideSpell() {
		super(new Identifier("ssc_addon", "space_stride"), SpellRarity.WHITE);
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
		int slowFall = SLOW_FALL_TICKS + (level - 1) * 100;
		int jump = JUMP_TICKS + (level - 1) * 50;
		// 跳升每两级 +1 级：L1/L2=I、L3/L4=II、L5=III（amplifier = 级数-1）
		int jumpAmplifier = (level - 1) / 2;
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, slowFall, 0));
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, jump, jumpAmplifier));
		// 演出：脚下星尘环绕
		serverWorld.spawnParticles(ParticleTypes.CLOUD,
				caster.getX(), caster.getY() + 0.2, caster.getZ(), 8, 0.4, 0.1, 0.4, 0.02);
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				caster.getX(), caster.getY() + 1.0, caster.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_RABBIT_JUMP, SoundCategory.PLAYERS, 0.8f, 1.2f);
	}

	@Override
	public String getInBookTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_in_book_buff";
	}

	@Override
	public String getSoloTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_solo_buff";
	}
}
