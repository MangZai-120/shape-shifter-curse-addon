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
 * 冰锥齐射（冰系，绿色基底，jackcooper）：朝准星扇形散射 3 枚可穿刺冰锥，各自独立命中结算。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/ice_barrage.json}：
 * 基准每枚 4 伤 / cd 15s（L5 8s） / 耗蓝 30；三枚呈 ±12° 扇形，速度按等级倍率缩放（Lv5 ×1.75）。
 * <b>穿刺</b>：每枚最多可命中 2 + (等级-1) 个敌人（Lv1=2 → Lv5=6），命中后不碎裂继续飞行，
 * 达上限或撞方块/超距才消失（穿刺机制在实体内实现，单发冰锥不受影响）。
 * 复用 {@link SpellFrostSpikeEntity}（伤害即 power），不新建实体。</p>
 */
public class IceBarrageSpell extends Spell {

	/** 散射枚数。 */
	private static final int COUNT = 3;
	/** 扇形半角（度）：三枚 = 中心 1 枚 + 两侧各 1 枚偏转此角度。 */
	private static final float SPREAD_DEG = 12.0f;
	/** 基础穿刺数（单枚最多命中敌人数），每级 +1。 */
	private static final int PIERCE_BASE = 2;

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
		// exp 挂起经验均分给三枚（bounty 为绝对值，cast 时一次性取走除以枚数）
		int bountyTen = solo ? 0 : ssc_addon$takePendingExp();
		int eachBounty = bountyTen / COUNT;
		java.util.UUID castId = solo ? null : ssc_addon$getRefundCastId();
		// 按 COUNT 均匀铺开扇形：COUNT=3 时即中心 1 枚 + 两侧各 1 枚偏 SPREAD_DEG
		for (int i = 0; i < COUNT; i++) {
			float yawOffset = (i - (COUNT - 1) * 0.5f) * SPREAD_DEG;
			SpellFrostSpikeEntity spike = new SpellFrostSpikeEntity(caster.getWorld(), caster);
			spike.setDamage(power);
			spike.setLevel(level); // 真实魔法等级：L4+ 渲染 3D 冰锥模型（与「冰锥」法术同款外观）
			spike.setPierceCount(PIERCE_BASE + (level - 1)); // 穿刺数：Lv1=2 → Lv5=6（单枚最多命中敌人数）
			spike.setExpBountyTen(eachBounty);
			spike.setRefundCastId(castId);
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
