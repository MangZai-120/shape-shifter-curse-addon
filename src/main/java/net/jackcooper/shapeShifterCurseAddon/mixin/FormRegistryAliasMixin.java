package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Read old IDs without exposing duplicate forms in SSC's selection screen and suggestions. */
@Mixin(value = RegPlayerForms.class, remap = false)
public abstract class FormRegistryAliasMixin {
    @ModifyVariable(target = @Desc(value = "getPlayerForm", args = Identifier.class, ret = IForm.class),
            at = @At("HEAD"), argsOnly = true, remap = false)
    private static Identifier ssca$canonicalFormId(Identifier id) {
        return FormIdMigration.canonical(id);
    }

    @ModifyVariable(target = @Desc(value = "getPlayerForm", args = String.class, ret = IForm.class),
            at = @At("HEAD"), argsOnly = true, remap = false)
    private static String ssca$canonicalFormString(String id) {
        return FormIdMigration.canonical(id);
    }
}
