package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SnowFoxEvolutionClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class SnowFoxRollingInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void ssca$steer(boolean slow,float multiplier,CallbackInfo ci) {
        SnowFoxEvolutionClient.movement((Input)(Object)this);
    }
}
