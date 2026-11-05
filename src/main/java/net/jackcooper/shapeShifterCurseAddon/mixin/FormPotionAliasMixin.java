package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Old potion stacks remain usable; newly assigned and saved target IDs use the renamed form. */
@Mixin(value = CTPUtils.class, remap = false)
public abstract class FormPotionAliasMixin {
    @Inject(method = "getCTPFormIDFromNBT", at = @At("RETURN"), cancellable = true)
    private static void ssca$readLegacyPotionTarget(CallbackInfoReturnable<Identifier> cir) {
        Identifier original = cir.getReturnValue();
        Identifier canonical = FormIdMigration.canonical(original);
        if (canonical != original) cir.setReturnValue(canonical);
    }

    @ModifyVariable(method = {"setTransformativePotionForm", "setCTPFormIDToNBT"},
            at = @At("HEAD"), argsOnly = true, require = 2)
    private static Identifier ssca$saveCanonicalPotionTarget(Identifier id) {
        return FormIdMigration.canonical(id);
    }
}
