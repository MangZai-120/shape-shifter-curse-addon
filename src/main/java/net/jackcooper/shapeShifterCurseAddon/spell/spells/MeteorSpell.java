package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellMeteorEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 陨火术（火系，紫色基底，jackcooper）：按住施法键在准星落点显示瞄准圈（最远 32 格），
 * 松开后召唤陨火——0.5s 落点预警圈后火球从天而降，半径 3 格 AOE（伤害 + 点燃 3s + 击退，中心满伤边缘 40%）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/meteor.json}：
 * 基准 8 伤 / 半径 3 格 / cd 10s / 耗蓝 30；半径按 speed_multiplier 缩放（每级 +0.5 格）。
 * 落点用射线检测（含方块），<b>必须命中方块</b>——准星指天（无方块命中）时拒绝施放、不耗法力/CD（仿契灵传送）；
 * 客户端按住预览与服务端施法共用 {@link Spell#computeAimImpact}，所见即所得。</p>
 */
public class MeteorSpell extends Spell {

	/** 最大施法距离（格）。 */
	private static final double MAX_RANGE = 32.0;
	/** 基础 AOE 半径（格），实际 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 3.0;

	public MeteorSpell() {
		super(new Identifier("ssc_addon", "meteor"), SpellRarity.PURPLE);
	}

	/** 按住瞄准型：最大施法距离 32 格（客户端按住施法键显示落点预览圈，松开施放）。 */
	@Override
	public double getAimMaxRange() {
		return MAX_RANGE;
	}

	/** 预览圈半径 = 实际 AOE 半径（含等级缩放），与服务端预警圈一致。 */
	@Override
	public double getAimRadius(int level) {
		return BASE_RADIUS * getSpeedMultiplier(level);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	/** 施法前置校验：落点必须命中方块（空中拒绝施放；书本路径由 SpellCastManager 预检，不耗法力/CD）。 */
	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		return Spell.computeAimImpact(caster, MAX_RANGE) != null;
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		// 射线求落点（与客户端按住预览同一几何）：必须命中方块；null（指天/超距无方块）→中止施放
		Vec3d impact = Spell.computeAimImpact(caster, MAX_RANGE);
		if (impact == null) {
			return;
		}
		SpellMeteorEntity meteor = new SpellMeteorEntity(caster.getWorld(), caster);
		meteor.setDamage(power);
		meteor.setLevel(level);
		meteor.setRadius(BASE_RADIUS * getSpeedMultiplier(level));
		meteor.setImpactTarget(impact.x, impact.y, impact.z);
		caster.getWorld().spawnEntity(meteor);
		// 施法音效（召唤感）：烈焰人低吼 + 火焰附加
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.5f);
	}
}
