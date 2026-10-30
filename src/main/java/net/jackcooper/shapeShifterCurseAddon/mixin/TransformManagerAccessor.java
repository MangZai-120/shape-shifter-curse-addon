package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.HashMap;
import java.util.UUID;

@Mixin(value = TransformManager.class, remap = false)
public interface TransformManagerAccessor {
    @Accessor(value = "playerData", remap = false)
    static HashMap<UUID, TransformManager.PlayerTransformData> ssca$playerData() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
