package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the original Origin identity so an upgrade does not remove/recreate saved power state. */
@Mixin(value = NormalForm.class, remap = false)
public abstract class FormOriginIdentityMixin {
    @Inject(method = "getFormLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$keepOriginIdentity(CallbackInfoReturnable<Pair<Identifier, Identifier>> cir) {
        Identifier id = ((NormalForm) (Object) this).getFormID();
        Identifier legacy = FormIdMigration.legacy(id);
        if (!id.equals(legacy)) {
            cir.setReturnValue(new Pair<>(new Identifier("origins", "origin"),
                    new Identifier(legacy.getNamespace(), "form_" + legacy.getPath())));
        }
    }
}
