package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 契灵远程削弱：
 * - 持有弓 / 弩 / 三叉戟（PersistentProjectileEntity 子类）发射的弹体在第 1 tick 缩减 20% 速度
 * - 弹体造成的伤害 -20%
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class MancianimaProjectileMixin {

	@Unique
	private boolean ssc_addon$mancianimaScaled = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void ssc_addon$scaleVelocity(CallbackInfo ci) {
		if (ssc_addon$mancianimaScaled) return;
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (self.getWorld().isClient()) {
			ssc_addon$mancianimaScaled = true;
			return;
		}
		Entity owner = self.getOwner();
		if (owner instanceof PlayerEntity p && FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			Vec3d v = self.getVelocity();
			self.setVelocity(v.multiply(0.8));
		}
		ssc_addon$mancianimaScaled = true;
	}

	@Inject(method = "getDamage", at = @At("RETURN"), cancellable = true)
	private void ssc_addon$scaleDamage(CallbackInfoReturnable<Double> cir) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		Entity owner = self.getOwner();
		if (owner instanceof LivingEntity le && le instanceof PlayerEntity p
				&& FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			cir.setReturnValue(cir.getReturnValueD() * 0.8);
		}
	}
}
