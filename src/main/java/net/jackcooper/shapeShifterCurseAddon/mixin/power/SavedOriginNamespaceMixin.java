package net.jackcooper.shapeShifterCurseAddon.mixin.power;

import net.onixary.shapeShifterCurseFabric.integration.origins.component.PlayerOriginComponent;
import net.jackcooper.shapeShifterCurseAddon.util.NamespaceMigration;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PlayerOriginComponent.class, remap = false)
public abstract class SavedOriginNamespaceMixin {
    @ModifyVariable(method = "readFromNbt", at = @At("HEAD"), argsOnly = true)
    private NbtCompound ssc_addon$readPreviousNamespace(NbtCompound saved) {
        return NamespaceMigration.origins(saved);
    }
}
