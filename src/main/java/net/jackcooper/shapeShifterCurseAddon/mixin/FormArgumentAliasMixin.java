package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.command.FormArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** SSC's command argument resolves its ID directly from the registry map. */
@Mixin(FormArgumentType.class)
public abstract class FormArgumentAliasMixin {
    @ModifyVariable(method = "getForm", at = @At("STORE"), ordinal = 0, remap = false)
    private static Identifier ssc_addon$resolveLegacyFormArgument(Identifier formId) {
        return FormIdMigration.canonical(formId);
    }
}
