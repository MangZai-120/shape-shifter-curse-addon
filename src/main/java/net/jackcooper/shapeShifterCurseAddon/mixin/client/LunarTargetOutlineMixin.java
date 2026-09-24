package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local targeting feedback, independent of entity glow flags and server effects. */
@Mixin(MinecraftClient.class)
public class LunarTargetOutlineMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void ssca$lunarOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpellcastClient.isLunarTarget(entity)) cir.setReturnValue(true);
    }
}
