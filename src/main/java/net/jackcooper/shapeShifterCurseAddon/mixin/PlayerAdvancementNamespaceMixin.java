package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.google.gson.JsonElement;
import net.jackcooper.shapeShifterCurseAddon.util.LegacyAdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementNamespaceMixin {
    @ModifyArg(method = "load", index = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/google/gson/TypeAdapter;fromJsonTree(Lcom/google/gson/JsonElement;)Ljava/lang/Object;"))
    private JsonElement ssc_addon$migrateAdvancementIds(JsonElement entries) {
        return LegacyAdvancementProgress.migrate(entries);
    }
}
