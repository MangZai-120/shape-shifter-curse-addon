package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.compat.MoistureHudRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Forge uses FluidType and a separate air overlay instead of vanilla renderStatusBars. */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", remap = false)
public abstract class ConnectorMoistureHudVisibilityMixin {
    // These are Forge runtime names. No Forge classes are linked on native Fabric.
    @ModifyExpressionValue(method = "renderAir", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluidType(Lnet/minecraftforge/fluids/FluidType;)Z"))
    private boolean ssca$showFullMoistureForge(boolean submerged) {
        return submerged || MinecraftClient.getInstance().getCameraEntity() instanceof PlayerEntity player
                && MoistureHudRules.requiresBar(player);
    }
}
