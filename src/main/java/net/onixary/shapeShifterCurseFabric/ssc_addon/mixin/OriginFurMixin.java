package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginalFurClient;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurModel;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurAnimatable;
import mod.azure.azurelib.model.GeoModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OriginalFurClient.OriginFur.class)
public abstract class OriginFurMixin {

    @Shadow
    public abstract GeoModel<OriginFurAnimatable> getGeoModel();

    @Inject(method = "setPlayer", at = @At("RETURN"), remap = false)
    private void ssc_addon$setPlayer(PlayerEntity e, CallbackInfo ci) {
        if (this.getGeoModel() instanceof OriginFurModel) {
            ((OriginFurModel)this.getGeoModel()).setPlayer(e);
        }
    }
}
