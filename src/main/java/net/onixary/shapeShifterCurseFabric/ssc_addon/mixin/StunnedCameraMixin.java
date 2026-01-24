package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to lock camera movement when player is stunned
 */
@Mixin(Mouse.class)
public class StunnedCameraMixin {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.hasStatusEffect(SscAddon.STUN)) {
            // Cancel all mouse movement updates when stunned - this locks the camera
            ci.cancel();
        }
    }
}
