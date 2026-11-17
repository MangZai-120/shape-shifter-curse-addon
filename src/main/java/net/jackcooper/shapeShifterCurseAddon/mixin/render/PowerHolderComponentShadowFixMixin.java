package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import io.github.apace100.apoli.power.InvisibilityPower;
import io.github.apace100.apoli.power.PreventFeatureRenderPower;
import net.jackcooper.shapeShifterCurseAddon.util.IrisShadowPassDetector;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collections;
import java.util.List;

/** Only the two feature-visibility queries participate in the Iris workaround. */
@Mixin(value = PowerHolderComponentImpl.class, remap = false)
public class PowerHolderComponentShadowFixMixin {
    @Shadow @Final private LivingEntity owner;

    @Inject(method = "getPowers(Ljava/lang/Class;)Ljava/util/List;", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void ssca$featureQueries(Class<?> powerClass, CallbackInfoReturnable<List<?>> cir) {
        if ((powerClass == InvisibilityPower.class || powerClass == PreventFeatureRenderPower.class)
                && IrisShadowPassDetector.shouldSuppressPowerQuery(owner)) {
            cir.setReturnValue(Collections.emptyList());
        }
    }
}
