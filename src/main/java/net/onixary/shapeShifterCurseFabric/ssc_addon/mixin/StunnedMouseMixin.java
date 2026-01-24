package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class StunnedMouseMixin {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.hasStatusEffect(SscAddon.STUN)) {
            // Prevent camera movement
            ci.cancel();
        }
    }
}
