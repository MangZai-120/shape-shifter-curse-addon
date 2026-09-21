package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectileEntity.class)
public abstract class DomainProjectileBoundaryMixin {
	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
	private void ssca$domainTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
		ProjectileEntity projectile = (ProjectileEntity) (Object) this;
		if (projectile.isRemoved() || DomainManager.blocksPath(projectile.getWorld(), projectile.getPos(),
				target.getBoundingBox().getCenter(), 0)) cir.setReturnValue(false);
	}

	@Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
	private void ssca$domainCollision(HitResult hit, CallbackInfo ci) {
		ProjectileEntity projectile = (ProjectileEntity) (Object) this;
		if (projectile.isRemoved() || DomainManager.blocksPath(projectile.getWorld(), projectile.getPos(), hit.getPos(), 0)) {
			if (!projectile.getWorld().isClient) projectile.discard();
			ci.cancel();
		}
	}
}