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
 * 冰锥齐射（冰系，绿色基底，jackcooper）：朝准星扇形散射 3 枚小冰锥，各自独立命中结算。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/ice_barrage.json}：
 * 基准每枚 2 伤 / cd 5s / 耗蓝 18；三枚呈 ±12° 扇形，速度按等级倍率缩放。
 * 复用 {@link SpellFrostSpikeEntity}（伤害即 power，单发小冰锥），不新建实体。</p>
 */
public class IceBarrageSpell extends Spell {

	/** 散射枚数。 */
	private static final int COUNT = 3;
	/** 扇形半角（度）：三枚 = 中心 1 枚 + 两侧各 1 枚偏转此角度。 */
	private static final float SPREAD_DEG = 12.0f;

	public IceBarrageSpell() {
		super(new Identifier("ssc_addon", "ice_barrage"), SpellRarity.GREEN);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		Vec3d look = caster.getRotationVec(1.0F);
		float speedMul = getSpeedMultiplier(level);
		// 按 COUNT 均匀铺开扇形：COUNT=3 时即中心 1 枚 + 两侧各 1 枚偏 SPREAD_DEG
		for (int i = 0; i < COUNT; i++) {
			float yawOffset = (i - (COUNT - 1) * 0.5f) * SPREAD_DEG;
			SpellFrostSpikeEntity spike = new SpellFrostSpikeEntity(caster.getWorld(), caster);
			spike.setDamage(power);
			spike.setLevel(Math.max(1, level - 2)); // 齐射单发按低两档外观（视觉上小一号）
			Vec3d dir = rotateYaw(look, yawOffset);
			spike.setDirection(dir, speedMul);
			caster.getWorld().spawnEntity(spike);
		}
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 1.3f);
	}

	/** 把方向向量绕 Y 轴旋转 deg 度（水平偏转，保持准星俯仰）。 */
	private static Vec3d rotateYaw(Vec3d v, float deg) {
		if (deg == 0f) {
			return v;
		}
		double rad = Math.toRadians(deg);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return new Vec3d(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
	}
}
