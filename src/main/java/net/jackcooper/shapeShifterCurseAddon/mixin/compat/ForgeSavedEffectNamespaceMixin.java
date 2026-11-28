package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import net.jackcooper.shapeShifterCurseAddon.compat.StatusEffectIdMigration;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge prefers the stored effect name over its numeric ID, including in suspicious stew. */
@Pseudo
@Mixin(targets = "net.minecraftforge.common.ForgeHooks", remap = false)
public abstract class ForgeSavedEffectNamespaceMixin {
    @Inject(method = "loadMobEffect", at = @At("HEAD"), cancellable = true)
    private static void ssc_addon$readOldEffect(NbtCompound tag, String key, StatusEffect fallback,
                                               CallbackInfoReturnable<StatusEffect> callback) {
        String value = tag.getString(key);
        if (!value.startsWith("ssca_neomagic:")) return;
        Identifier original = Identifier.tryParse(value);
        Identifier current = StatusEffectIdMigration.canonical(original);
        if (current != null && !current.equals(original)) {
            Registries.STATUS_EFFECT.getOrEmpty(current).ifPresent(callback::setReturnValue);
        }
    }
}
