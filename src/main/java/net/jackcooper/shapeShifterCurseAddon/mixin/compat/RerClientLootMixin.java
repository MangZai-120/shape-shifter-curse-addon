package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import com.google.gson.JsonObject;
import net.jackcooper.shapeShifterCurseAddon.compat.rei.RerStoryBookTags;
import net.jackcooper.shapeShifterCurseAddon.loot.StoryBookLoot;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "uk.me.desert_island.rer.RoughlyEnoughResourcesClient", remap = false)
public abstract class RerClientLootMixin {
    // RER 2.9.0 publishes each freshly decoded loot table to ClientLootCache here.
    @ModifyArg(method = "lambda$onInitializeClient$3", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 1, require = 0, remap = false)
    private static Object ssca$shareBookTags(Object key, Object value) {
        if (key instanceof Identifier id && value instanceof JsonObject table && StoryBookLoot.isTargetChest(id)) {
            RerStoryBookTags.shareIn(table);
        }
        return value;
    }
}
