package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.compat.StatusEffectIdMigration;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.RegistryMapSerializer", remap = false)
public abstract class FabricStatusEffectIdMapMixin {
    @Inject(method = "fromNbt", at = @At("RETURN"))
    private static void ssc_addon$migrateSavedEffects(CallbackInfoReturnable<Map<Identifier, Object2IntMap<Identifier>>> cir) {
        StatusEffectIdMigration.migrate(cir.getReturnValue());
    }
}
