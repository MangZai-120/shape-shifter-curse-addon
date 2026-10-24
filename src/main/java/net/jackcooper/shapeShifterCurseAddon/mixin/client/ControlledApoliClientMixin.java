package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import io.github.apace100.apoli.ApoliClient;
import io.github.apace100.apoli.power.Power;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(ApoliClient.class)
public abstract class ControlledApoliClientMixin {
    @Inject(method = "performActivePowers", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$blockControlledDispatch(List<Power> powers, CallbackInfo ci) {
        if (AbilityUseGuard.blocked(MinecraftClient.getInstance().player)) ci.cancel();
    }
}
