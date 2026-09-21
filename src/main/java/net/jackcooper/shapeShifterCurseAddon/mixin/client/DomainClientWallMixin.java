package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * 领域客户端预测墙（jackcooper，2026-09-21 需求）：本地玩家与所有客户端实体在
 * 领域壳边界处就地撞墙减速，不再走出壳外再被服务器拉回（消除卡边/橡皮筋/第三人称
 * 视角下被顶出球壳的表现）。
 *
 * <p>与服务器（{@code DomainEntityBoundaryMixin} → {@code DomainManager.limitMovement}）
 * 使用同一套 {@link DomainRules} 几何与同一二分缩放算法、同一 padding 公式
 * （max(width,height)/2），客户端先行减速后，服务器 onPlayerMove 校验自然通过，
 * 拉回仅作同步表过期/边角的兜底。</p>
 *
 * <p>扩张期壳同样单向：客户端从外向内不拦（可走进），从内向外拦（走不出）。
 * 数据源 DomainRenderer.clientShells()：40t 未收到同步表即视为无壳（断流兜底），
 * 服务器侧拦截不受影响。</p>
 */
@Mixin(Entity.class)
public abstract class DomainClientWallMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3d ssca$domainClientWall(Vec3d movement) {
		Entity entity = (Entity) (Object) this;
		if (!entity.getWorld().isClient || movement.lengthSquared() < 1.0e-12) return movement;
		List<DomainRenderer.Shell> shells = DomainRenderer.clientShells();
		if (shells == null) return movement;
		// 2026-09-22：与服务端同步改用脚部锚点 + 半宽 padding（防中心垂直偏移穿壳 + 两端公式一致防抖动）
		Vec3d feet = entity.getPos();
		double padding = entity.getWidth() * 0.5;
		Vec3d result = null;
		for (DomainRenderer.Shell shell : shells) {
			double wall = Math.max(0.1, shell.radius() - padding);
			Vec3d startRel = feet.subtract(shell.center());
			Vec3d end = startRel.add(movement);
			boolean cross = shell.complete()
					? DomainRules.crosses(startRel.x, startRel.y, startRel.z, end.x, end.y, end.z, padding)
					: DomainRules.crossesOutward(startRel.x, startRel.y, startRel.z, end.x, end.y, end.z, shell.radius());
			if (!cross) continue;
			// 滑行优先（起点在壳内），否则整体二分兜底
			Vec3d slid = DomainRules.slideInside(startRel, movement, wall);
			result = (result == null ? movement : result)
					.add(slid).subtract(movement);
		}
		return result == null ? movement : result;
	}
}
