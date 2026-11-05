package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.config.FormColorDataMigration;
import net.minecraft.nbt.NbtCompound;
import net.onixary.shapeShifterCurseFabric.util.FormColorData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies before SSC rebuilds the live preset maps, including config reloads. */
@Mixin(value = FormColorData.class, remap = false)
public abstract class FormColorDataMigrationMixin {
    @ModifyVariable(method = "loadCompound", at = @At("HEAD"), argsOnly = true, remap = false)
    private NbtCompound ssca$migrateFormKeys(NbtCompound source) {
        return FormColorDataMigration.migrate(source);
    }
}
