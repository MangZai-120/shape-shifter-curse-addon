package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** 方块碰撞、潜行和自动上台阶处理后的球壳终检，客户端与服务端共用。 */
public final class DomainCollision {
	private DomainCollision() {}

	public static Vec3d finishMovement(Entity entity, Vec3d movement, List<DomainRules.Shell> shells) {
		Vec3d clipped = DomainRules.clipMovement(entity.getPos(), movement, entity.getWidth() * 0.5, shells);
		if (clipped.equals(movement)) return movement;
		// 缩短上台阶位移也会降低落点；不能因此把碰撞箱放进台阶侧面。
		if (!entity.getWorld().isSpaceEmpty(entity, entity.getBoundingBox().offset(clipped))) return Vec3d.ZERO;
		return clipped;
	}
}
