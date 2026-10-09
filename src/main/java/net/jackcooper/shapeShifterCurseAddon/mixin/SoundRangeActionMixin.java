package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;

/** Scope inherited/data-driven powers too, including nested actor/target actions and playsound. */
@Mixin(value = ActionFactory.Instance.class, remap = false)
public abstract class SoundRangeActionMixin {
    @WrapOperation(method = "accept", at = @At(value = "INVOKE",
            target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void ssca$scopeFormAction(BiConsumer<Object, Object> effect, Object data, Object target,
                                      Operation<Void> original) {
        if (SoundRangeRules.inActionScope()) {
            original.call(effect, data, target);
        } else if (AddonSoundRange.isAddonActionTarget(target)) {
            SoundRangeRules.withActionScope(() -> original.call(effect, data, target));
        } else {
            original.call(effect, data, target);
        }
    }
}
