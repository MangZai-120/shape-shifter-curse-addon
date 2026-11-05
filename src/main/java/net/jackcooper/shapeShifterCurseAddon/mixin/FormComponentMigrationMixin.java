package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Migrate before SSC resolves forms, avoiding a temporary reset to its default player form. */
@Mixin(value = PlayerFormComponent.class, remap = false)
public abstract class FormComponentMigrationMixin {
    @ModifyVariable(method = "readFromNbt", at = @At("HEAD"), argsOnly = true, remap = false)
    private NbtCompound ssca$migrateSavedForms(NbtCompound tag) {
        return FormIdMigration.migrateComponentNbt(tag);
    }

    @ModifyVariable(target = {@Desc(value = "setForm", args = Identifier.class),
            @Desc(value = "setFallbackForm", args = Identifier.class)},
            at = @At("HEAD"), argsOnly = true, remap = false, require = 2)
    private Identifier ssca$canonicalAssignedForm(Identifier id) {
        return FormIdMigration.canonical(id);
    }
}
