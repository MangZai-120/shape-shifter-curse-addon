package net.jackcooper.shapeShifterCurseAddon.mixin.compat;

import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** RebornCore replaces the shared recipe's key map while other connections may be encoding it. */
@Pseudo
@Mixin(targets = "reborncore.common.crafting.ShapedRecipeHelper", remap = false)
public abstract class RebornShapedRecipeSnapshotMixin {
    @ModifyVariable(method = "rewriteForNetworkSync", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static JsonObject ssca$copyBeforeNetworkRewrite(JsonObject recipe) {
        // Keep tag-based server data intact and give each serializer its own complete JSON tree.
        return recipe.deepCopy();
    }
}
