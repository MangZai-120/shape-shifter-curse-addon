package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

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
 */
@Mixin(OriginFurModel.class)
public abstract class OriginFurModelMixin {

    @Shadow
    PlayerEntity entity;

    /**
     * Cancel setRotationForTailBones if entity is null to prevent NPE.
     */
    @Inject(method = "setRotationForTailBones", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssc_addon$preventNpeInSetRotationForTailBones(CallbackInfo ci) {
        if (this.entity == null) {
            ci.cancel();
        }
    }
}
