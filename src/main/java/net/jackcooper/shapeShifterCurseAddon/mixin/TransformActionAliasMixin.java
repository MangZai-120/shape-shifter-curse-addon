package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.additional_power.TransformAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Legacy data packs may still name a form before its registry ID was renamed. */
@Mixin(TransformAction.class)
public abstract class TransformActionAliasMixin {
    @ModifyVariable(method = {"TransformToFormAction", "GiveCustomTransformEffect"},
            at = @At("STORE"), ordinal = 0, remap = false, require = 2)
    private static Identifier ssc_addon$resolveLegacyTransformTarget(Identifier formId) {
        return FormIdMigration.canonical(formId);
    }
}
