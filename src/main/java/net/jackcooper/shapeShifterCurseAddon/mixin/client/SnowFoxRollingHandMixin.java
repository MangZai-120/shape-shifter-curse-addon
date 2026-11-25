package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.client.SnowFoxEvolutionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class SnowFoxRollingHandMixin {
    // The saved perspective is already first person while the camera is still returning to the eyes.
    @ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean ssca$showHandWhenCameraReturns(boolean firstPerson) {
        var mc = MinecraftClient.getInstance();
        return firstPerson && !(SnowFoxEvolutionClient.controlsCamera(mc.player) && mc.gameRenderer.getCamera().isThirdPerson());
    }
}
