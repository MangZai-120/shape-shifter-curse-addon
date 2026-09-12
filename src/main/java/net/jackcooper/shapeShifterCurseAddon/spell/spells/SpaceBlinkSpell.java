package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 空间跳跃（空间系，绿色基底，jackcooper）：按住施法键在准星方向显示落点预览圈
 * （仿契灵平台传送交互 + 陨火按住瞄准管线），松开瞬移。
 *
 * <p><b>落点规则（三级回退）</b>：①沿视线逐格找最远合法落点（有地面 + 碰撞箱空间）；
 * ②无 → 传送到视线命中的第一个表面（方块命中面上表面）；③仍无 → 传到射程尽头
 * （尽头受阻则向起点内收至最后无阻挡处）。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/space_blink.json}：
 * 基准距离 8 格（每级 +25%）/ cd 16s（每级 -10%）/ 耗蓝 30（每级 +25%）；
 * 吃空间法阵距离加成。</p>
 */
public class SpaceBlinkSpell extends Spell {

	/** 基础瞬移距离（格），实际 = 基础 × speed_multiplier(level) × 空间法阵距离加成。 */
	private static final double BASE_RANGE = 8.0;

	public SpaceBlinkSpell() {
		super(new Identifier("ssc_addon", "space_blink"), SpellRarity.GREEN);
	}

	/** 当前有效瞬移距离（客户端预览/服务端结算共用）。 */
	public double getBlinkRange(int level) {
		return BASE_RANGE * getSpeedMultiplier(level);
	}

	/** 按住瞄准型：最大施法距离（客户端按住显示落点预览，松开施放）。 */
	@Override
	public double getAimMaxRange() {
		return BASE_RANGE; // 预览基准；等级缩放由 effectiveRange 乘 speed_multiplier
	}

	/** 预览圈半径：小圈标记落点（0.5 格落地标记，同契灵平台传送视觉语言）。 */
	@Override
	public double getAimRadius(int level) {
		return 0.5;
	}

	/** 实际有效射程（含等级缩放）。 */
	private double effectiveRange(int level) {
		return getBlinkRange(level);
	}

	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		// 空间跳跃永远可施放（无合法落点会回退到表面/尽头，不再拒绝）
		return true;
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
		Vec3d dest = computeBlinkDestination(caster, level);
		if (dest == null) {
			return; // 理论不可达（回退链已兜底），防御性保留
		}
		// 起点消散演出
		serverWorld.spawnParticles(ParticleTypes.PORTAL,
				caster.getX(), caster.getBodyY(0.5), caster.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
		caster.teleport(dest.x, dest.y, dest.z);
		// 终点汇聚演出
		serverWorld.spawnParticles(ParticleTypes.END_ROD,
				dest.x, dest.y + 1.0, dest.z, 16, 0.3, 0.5, 0.3, 0.05);
		serverWorld.playSound(null, dest.x, dest.y, dest.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.4f);
	}

	/**
	 * 计算瞬移落点（三级回退）：
	 * ①沿视线逐格找最远合法落点（有地面 + 碰撞箱空间）；
	 * ②无 → 视线命中的第一个表面（方块命中面上表面）；
	 * ③仍无 → 射程尽头点（受阻则从尽头向起点内收到最后无阻挡处）。
	 */
	private Vec3d computeBlinkDestination(ServerPlayerEntity caster, int level) {
		Vec3d start = caster.getPos();
		Vec3d dir = caster.getRotationVec(1.0F).normalize();
		double range = effectiveRange(level);
		float width = caster.getDimensions(net.minecraft.entity.EntityPose.STANDING).width;
		float height = caster.getDimensions(net.minecraft.entity.EntityPose.STANDING).height;

		// ① 首选：最远合法落点（有地面 + 空间无阻挡）
		Vec3d best = null;
		for (double d = 1.5; d <= range; d += 0.5) {
			Vec3d p = start.add(dir.multiply(d));
			BlockPos feet = BlockPos.ofFloored(p.x, p.y, p.z);
			if (caster.getWorld().isSpaceEmpty(caster, boxAt(p, width, height))
					&& !caster.getWorld().getBlockState(feet.down()).isAir()) {
				best = p;
			}
		}
		if (best != null) {
			return best;
		}

		// ② 回退：视线命中的第一个表面（脚部贴命中方块上表面）
		Vec3d eye = caster.getEyePos();
		Vec3d end = eye.add(dir.multiply(range));
		BlockHitResult hit = caster.getWorld().raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3d surface = Vec3d.ofBottomCenter(hit.getBlockPos());
			if (caster.getWorld().isSpaceEmpty(caster, boxAt(surface, width, height))) {
				return surface;
			}
		}

		// ③ 兜底：射程尽头（受阻则向起点内收至最后无阻挡点）
		for (double d = range; d >= 1.0; d -= 0.5) {
			Vec3d p = start.add(dir.multiply(d));
			if (caster.getWorld().isSpaceEmpty(caster, boxAt(p, width, height))) {
				return p;
			}
		}
		return null;
	}

	/** 构造落点判定盒（玩家碰撞箱，脚部对齐 p）。 */
	private static Box boxAt(Vec3d p, float width, float height) {
		return new Box(
				p.x - width / 2.0, p.y, p.z - width / 2.0,
				p.x + width / 2.0, p.y + height, p.z + width / 2.0);
	}
}
