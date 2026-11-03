package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class FrozenScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void ssca$blockInventory(Screen screen, CallbackInfo ci) {
        if (screen instanceof HandledScreen<?> && FrozenControl.blocked(((MinecraftClient)(Object)this).player)) ci.cancel();
    }
}
