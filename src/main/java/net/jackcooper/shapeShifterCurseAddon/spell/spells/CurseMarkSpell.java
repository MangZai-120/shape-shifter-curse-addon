package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellCurseMarkEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 诅咒标记（诅咒系，绿色基底，jackcooper）：朝准星射出诅咒印记，命中目标后
 * 8 秒内受到的伤害 +20%（伤害加深），可被月辉系净化（月华治愈清负面效果时移除）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/curse_mark.json}：
 * 基准 0 直伤 / 8s 标记（受伤 +20%）/ cd 10s / 耗蓝 15；标记时长按等级 +2s/级（L5=16s）。</p>
 */
public class CurseMarkSpell extends Spell {

	public CurseMarkSpell() {
		super(new Identifier("ssc_addon", "curse_mark"), SpellRarity.GREEN);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		// 标记时长：8s + 2s/级（L1=8s、L3=12s、L5=16s）
		int durationTicks = 160 + (level - 1) * 40;
		SpellCurseMarkEntity mark = new SpellCurseMarkEntity(caster.getWorld(), caster);
		mark.setDuration(durationTicks);
		Vec3d look = caster.getRotationVec(1.0F);
		mark.setDirection(look, getSpeedMultiplier(level));
		caster.getWorld().spawnEntity(mark);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.8f, 0.6f);
	}
}
