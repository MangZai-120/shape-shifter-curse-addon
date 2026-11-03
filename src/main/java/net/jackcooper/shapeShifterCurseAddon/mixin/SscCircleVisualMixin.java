package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.additional_power.SpawnParticlesInCircleAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/** Converts SSC's regular circle into one parametric event without changing the SSC JAR. */
@Mixin(value = SpawnParticlesInCircleAction.class, remap = false)
public abstract class SscCircleVisualMixin {
    @Inject(method = "action", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$circle(SerializableData.Instance data, Entity actor, CallbackInfo ci) {
        if (!(actor.getWorld() instanceof ServerWorld)) return;
        Predicate<Pair<Entity, Entity>> condition = data.get("bientity_condition");
        // Third-party predicates may depend on per-sample evaluations; leave that path untouched.
        if (condition != null) return;
        ParticleEffect particle = data.get("particle");
        Vec3d spread = data.<Vec3d>get("spread").multiply(
                actor.getWidth(), actor.getEyeHeight(actor.getPose()), actor.getWidth());
        Vec3d offset = new Vec3d(data.getDouble("offset_x"), data.getDouble("offset_y"),
                data.getDouble("offset_z"));
        Entity decoration = DecorationParticleScope.isProtected() ? null : DecorationParticleScope.owner();
        ParametricVisuals.circle(actor, particle, data.getBoolean("force"), Math.max(0, data.getInt("count")),
                data.getFloat("speed"), spread, offset, data.getDouble("radius"),
                Math.max(1, data.getInt("sample_count")), null,
                decoration == null ? null : decoration.getUuid());
        ci.cancel();
    }
}
