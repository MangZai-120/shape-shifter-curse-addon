package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellFrostSpikeEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 冰锥（白色基底，jackcooper）：朝准星射出一枚冰锥，命中造成魔法伤害。
 *
 * <p><b>本类只含行为</b>（投射物生成、音效、等级缩放应用）；全部数值已外移到
 * {@code data/ssc_addon/spells/frost_spike.json}（数据包可覆盖）：</p>
 * <ul>
 *   <li>基准：6 伤 / cd 3 秒 / 无前摇 / 耗书法力 15 / 单独使用惩罚 0.5×伤 2×cd 2×施法时间；</li>
 *   <li>levels[5]：L2 +25% 伤速、L3 再 -25% cd、L4 再 +25% 伤速（合计 +50%）、L5 再 -25% cd（合计 -50%）；
 *       品质 1白/2绿/3蓝/4紫/5橙（决定单独使用次数 8/6/4/2/1）；L4+ 投射物换 3D 冰锥模型。</li>
 * </ul>
 */
public class FrostSpikeSpell extends Spell {

	public FrostSpikeSpell() {
		super(new Identifier("ssc_addon", "frost_spike"), SpellRarity.WHITE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	/** 带等级施法：速度与投射物外观（L4+ 换 3D 冰锥模型）按卷轴等级缩放。 */
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		SpellFrostSpikeEntity spike = new SpellFrostSpikeEntity(caster.getWorld(), caster);
		spike.setDamage(power);
		spike.setLevel(level);
		Vec3d look = caster.getRotationVec(1.0F);
		spike.setDirection(look, getSpeedMultiplier(level));
		caster.getWorld().spawnEntity(spike);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
	}
}
