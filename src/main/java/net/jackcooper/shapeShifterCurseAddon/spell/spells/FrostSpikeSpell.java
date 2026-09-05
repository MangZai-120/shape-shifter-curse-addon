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
 * 冰锥（白色，jackcooper）。朝准星射出一枚冰锥，命中造成魔法伤害。
 *
 * <p>装书内基准：6 伤 / cd 3 秒 / 无施法时间 / 耗书法力 15。
 * 单独使用：默认固定裸用惩罚（伤害 ×0.5、冷却 ×2、施法时间 ×2）。</p>
 */
public class FrostSpikeSpell extends Spell {

	public FrostSpikeSpell() {
		super(new Identifier("ssc_addon", "frost_spike"), SpellRarity.WHITE);
	}

	@Override
	public float getBaseDamage() {
		return 6.0f;
	}

	@Override
	public int getBaseCooldownTicks() {
		return 60; // 3 秒
	}

	@Override
	public int getBaseCastTimeTicks() {
		return 0; // 无施法时间
	}

	@Override
	public int getManaCost() {
		return 15;
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		SpellFrostSpikeEntity spike = new SpellFrostSpikeEntity(caster.getWorld(), caster);
		spike.setDamage(power);
		Vec3d look = caster.getRotationVec(1.0F);
		spike.setDirection(look);
		caster.getWorld().spawnEntity(spike);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
	}
}
