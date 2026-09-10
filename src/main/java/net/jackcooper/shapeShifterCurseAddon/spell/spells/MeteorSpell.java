package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellMeteorEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 陨火术（火系，紫色基底，jackcooper）：准星落点（最远 16 格）召唤陨火——
 * 0.5s 落点预警圈后火球从天而降，半径 3 格 AOE（伤害 + 点燃 3s + 击退，中心满伤边缘 40%）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/meteor.json}：
 * 基准 8 伤 / 半径 3 格 / cd 10s / 耗蓝 30；半径按 speed_multiplier 缩放（每级 +0.5 格）。
 * 落点用射线检测（含方块），准星指天（无命中）时取 16 格截断点。</p>
 */
public class MeteorSpell extends Spell {

	/** 最大施法距离（格）。 */
	private static final double MAX_RANGE = 16.0;
	/** 基础 AOE 半径（格），实际 = 基础 × speed_multiplier(level)。 */
	private static final double BASE_RADIUS = 3.0;

	public MeteorSpell() {
		super(new Identifier("ssc_addon", "meteor"), SpellRarity.PURPLE);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		// 射线求落点：准星命中的方块/实体位置；无命中取 MAX_RANGE 截断点
		Vec3d eye = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(MAX_RANGE));
		HitResult hit = caster.getWorld().raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		Vec3d impact;
		if (hit.getType() != HitResult.Type.MISS) {
			impact = hit.getPos();
		} else {
			impact = end;
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
