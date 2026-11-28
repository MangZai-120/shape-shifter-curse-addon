package net.jackcooper.shapeShifterCurseAddon.mixin.power;

import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import net.jackcooper.shapeShifterCurseAddon.util.NamespaceMigration;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PowerHolderComponentImpl.class, remap = false)
public abstract class SavedPowerNamespaceMixin {
    @ModifyVariable(method = "fromTag", at = @At("HEAD"), argsOnly = true)
    private NbtCompound ssc_addon$readPreviousNamespace(NbtCompound saved) {
        return NamespaceMigration.powers(saved);
    }
}
