package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellFireBoltEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 火球术（火系，白色基底，jackcooper）：朝准星射出一枚火球，命中造成魔法伤害并点燃目标。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/fire_bolt.json}：
 * 基准 5 伤 + 点燃 3s（L3+ 5s）/ cd 4s / 耗蓝 15 / 单独惩罚 0.5×伤 2×cd；
 * 点燃时长不随伤害缩放（固定附加效果），L3 起提升到 5s。</p>
 */
public class FireBoltSpell extends Spell {

	/** L1-2 点燃 3s；L3+ 点燃 5s。 */
	private static final int FIRE_TICKS_LOW = 60;
	private static final int FIRE_TICKS_HIGH = 100;

	public FireBoltSpell() {
		super(new Identifier("ssc_addon", "fire_bolt"), SpellRarity.WHITE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		SpellFireBoltEntity bolt = new SpellFireBoltEntity(caster.getWorld(), caster);
		bolt.setDamage(power);
		bolt.setLevel(level);
		bolt.setFireTicks(level >= 3 ? FIRE_TICKS_HIGH : FIRE_TICKS_LOW);
		Vec3d look = caster.getRotationVec(1.0F);
		bolt.setDirection(look, getSpeedMultiplier(level));
		caster.getWorld().spawnEntity(bolt);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}
}
