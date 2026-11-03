package net.jackcooper.shapeShifterCurseAddon.mixin;

import java.util.Map;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Explosion.class)
public abstract class FrozenExplosionKnockbackMixin {
    @WrapOperation(method = "collectBlocksAndDamageEntities",at = @At(value = "INVOKE",target = "Lnet/minecraft/entity/Entity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void ssca$ignoreBlastImpulse(Entity target,Vec3d velocity,Operation<Void> original) {
        if (!(target instanceof LivingEntity living) || !FrozenControl.blocked(living)) original.call(target,velocity);
    }
    @WrapOperation(method = "collectBlocksAndDamageEntities",at = @At(value = "INVOKE",target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object ssca$removeClientBlastImpulse(Map<PlayerEntity,Vec3d> affected,Object key,Object value,Operation<Object> original) {
        return original.call(affected,key,key instanceof LivingEntity living && FrozenControl.blocked(living) ? Vec3d.ZERO : value);
    }
}
