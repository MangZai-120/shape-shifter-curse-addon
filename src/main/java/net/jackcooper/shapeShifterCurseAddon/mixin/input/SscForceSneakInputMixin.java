package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.jackcooper.shapeShifterCurseAddon.client.SscForceSneakClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SscForceSneakInputMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ssca$validateForcedSneak(boolean slowDown, float factor, CallbackInfo ci) {
        SscForceSneakClient.refresh();
    }
}
