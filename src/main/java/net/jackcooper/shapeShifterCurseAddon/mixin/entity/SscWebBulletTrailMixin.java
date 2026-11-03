package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only replaces the per-tick trail calls; SSC movement, sounds, collisions and web building remain. */
@Mixin(value = WebBullet.class, remap = false)
public abstract class SscWebBulletTrailMixin {
    @WrapOperation(method = {"tick", "method_5773"}, remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"),
            require = 3)
    private <T extends ParticleEffect> int ssca$trail(ServerWorld world, T particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed,
            Operation<Integer> original) {
        WebBullet bullet = (WebBullet) (Object) this;
        if (bullet.isRemoved()) return 0;
        ParametricVisuals.touch(bullet, bullet.getUuid(), ParametricVisuals.Kind.SSC_WEB_TRAIL,
                null, bullet.getPos(), true, world.getTime() - bullet.age, 0, 0, bullet.Tier);
        return 0;
    }
}
