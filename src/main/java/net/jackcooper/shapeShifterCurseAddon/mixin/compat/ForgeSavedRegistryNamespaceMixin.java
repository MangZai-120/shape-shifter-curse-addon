package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import net.jackcooper.shapeShifterCurseAddon.compat.ForgeSavedRegistryMigration;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Forge reads these snapshots from level.dat before injecting them into the live registries. */
@Pseudo
@Mixin(targets = "net.minecraftforge.common.ForgeHooks", remap = false)
public abstract class ForgeSavedRegistryNamespaceMixin {
    @ModifyVariable(method = "readAdditionalLevelSaveData", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static NbtCompound ssc_addon$migrateSavedIds(NbtCompound root) {
        return ForgeSavedRegistryMigration.migrate(root);
    }
}
