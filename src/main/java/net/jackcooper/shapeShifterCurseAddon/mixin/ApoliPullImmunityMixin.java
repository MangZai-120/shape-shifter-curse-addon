package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.power.factory.action.BiEntityActions;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.entity.RegAddonEntities;
import net.minecraft.entity.Entity;
import net.minecraft.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BiEntityActions.class, remap = false)
public abstract class ApoliPullImmunityMixin {
    // Apoli 2.9.2 registers bientity add_velocity here. Negative local Z pulls the target toward the actor.
    @Inject(method = "lambda$register$7", at = @At("HEAD"), cancellable = true)
    private static void ssca$ignorePull(SerializableData.Instance data, Pair<Entity, Entity> entities, CallbackInfo ci) {
        Entity target = entities.getRight();
        if (target != null && target.getType().isIn(RegAddonEntities.PULL_IMMUNE) && data.getFloat("z") < 0) ci.cancel();
    }
}
