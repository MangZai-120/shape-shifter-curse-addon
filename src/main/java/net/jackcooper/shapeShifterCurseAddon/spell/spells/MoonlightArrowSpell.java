package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellMoonlightArrowEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 月光箭（月辉系，绿色基底，jackcooper）：朝准星射出一支月光箭，
 * 命中造成魔法伤害；对亡灵生物额外 +50%。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/moonlight_arrow.json}：
 * 基准 4 伤（对亡灵 6）/ cd 4s / 耗蓝 12；速度按 speed_multiplier 缩放（弹道更快）。</p>
 */
public class MoonlightArrowSpell extends Spell {

	public MoonlightArrowSpell() {
		super(new Identifier("ssc_addon", "moonlight_arrow"), SpellRarity.GREEN);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		SpellMoonlightArrowEntity arrow = new SpellMoonlightArrowEntity(caster.getWorld(), caster);
		arrow.setDamage(power);
		arrow.setLevel(level);
		Vec3d look = caster.getRotationVec(1.0F);
		arrow.setDirection(look, getSpeedMultiplier(level));
		caster.getWorld().spawnEntity(arrow);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.8f);
	}
}
