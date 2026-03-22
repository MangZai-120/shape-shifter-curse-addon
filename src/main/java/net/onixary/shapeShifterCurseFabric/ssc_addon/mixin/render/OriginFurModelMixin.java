package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent NullPointerException in OriginFurModel.setRotationForTailBones
 * when entity is null (can happen during Iris shadow rendering or other edge cases).
 * Instead of cancelling the method, we set entity to the client player.
 */
@Mixin(OriginFurModel.class)
public abstract class OriginFurModelMixin {

    @Shadow
    PlayerEntity entity;

    /**
     * If entity is null, try to set it to the client player to prevent NPE
     * while still allowing the method to execute properly.
     */
    @Inject(method = "setRotationForTailBones", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssc_addon$setEntityIfNull(CallbackInfo ci) {
        if (this.entity == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                this.entity = client.player;
            } else {
                // If we still can't get a player, cancel to prevent NPE
                ci.cancel();
            }
        }
    }
}
