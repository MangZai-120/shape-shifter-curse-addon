package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The rope supplies vertical movement while attached; wall-holding must not cancel it. */
@Mixin(LivingEntity.class)
public abstract class SwingClimbingMixin {
    @ModifyExpressionValue(method = {"applyClimbingSpeed", "applyMovementInput", "travel"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isClimbing()Z"))
    private boolean ssca$ropeControlsClimbing(boolean climbing) {
        if ((Object) this != MinecraftClient.getInstance().player) return climbing;
        var swing = SpiderMoonWeaverSwingClient.getLocalSwing();
        return climbing && !(swing != null && swing.active
                && swing.state == SpiderMoonWeaverSwingClient.STATE_SWINGING);
    }
}
