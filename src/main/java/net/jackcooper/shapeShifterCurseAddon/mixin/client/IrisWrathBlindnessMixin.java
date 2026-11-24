package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.WrathMarkClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
abstract class IrisWrathBlindnessMixin {
    @Inject(method = "getBlindness", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ssca$wrathBlindness(CallbackInfoReturnable<Float> cir) {
        float strength = WrathMarkClient.addonBlindnessStrength(MinecraftClient.getInstance().getCameraEntity());
        if (strength > cir.getReturnValueF()) cir.setReturnValue(strength);
    }
}

