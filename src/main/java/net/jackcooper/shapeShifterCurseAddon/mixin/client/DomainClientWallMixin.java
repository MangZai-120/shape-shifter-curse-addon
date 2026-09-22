package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** 客户端预测与服务端共用脚部锚点、半宽余量和球壳滑行算法。 */
@Mixin(Entity.class)
public abstract class DomainClientWallMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3d ssca$domainClientWall(Vec3d movement) {
		Entity entity = (Entity) (Object) this;
		if (!entity.getWorld().isClient || movement.lengthSquared() < 1.0e-12) return movement;
		List<DomainRules.Shell> shells = DomainRenderer.clientShells();
		if (shells == null) return movement;
		return DomainRules.limitMovement(entity.getPos(), movement, entity.getWidth() * 0.5, shells);
	}
}
