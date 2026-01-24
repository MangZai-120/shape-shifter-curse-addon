package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginalFurClient;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurModel;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurAnimatable;
import mod.azure.azurelib.model.GeoModel;
import mod.azure.azurelib.renderer.GeoObjectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to call OriginFurModel.setPlayer when OriginFur.setPlayer is called.
 * This is needed because the parent class doesn't propagate the player to the model.
 */
@Mixin(OriginalFurClient.OriginFur.class)
public abstract class OriginFurMixin extends GeoObjectRenderer<OriginFurAnimatable> {

    // Dummy constructor for mixin - never actually called
    private OriginFurMixin() {
        super(null);
    }

    @Inject(method = "setPlayer", at = @At("RETURN"), remap = false)
    private void ssc_addon$setPlayer(PlayerEntity e, CallbackInfo ci) {
        // Use inherited getGeoModel() from GeoObjectRenderer
        GeoModel<OriginFurAnimatable> model = this.getGeoModel();
        if (model instanceof OriginFurModel) {
            ((OriginFurModel)model).setPlayer(e);
        }
    }

    @Override
    public void render(MatrixStack poseStack, OriginFurAnimatable animatable, VertexConsumerProvider bufferSource, RenderLayer renderType, VertexConsumer buffer, int packedLight) {
        if (animatable.e != null) {
            // Check for True Invisibility Effect
            if (animatable.e.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
                return;
            }

            try {
                PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(animatable.e);
                PlayerFormBase currentForm = component.getCurrentForm();
                
                if (currentForm != null && currentForm.FormID != null) {
                    String formPath = currentForm.FormID.getPath();
                    boolean playerIsShifted = !formPath.contains("original");

                    if (playerIsShifted) {
                        // We are in a shifted form (e.g. Fox)
                        // Access the Origin associated with THIS specific renderer
                        OriginalFurClient.OriginFur thisRenderer = (OriginalFurClient.OriginFur)(Object)this;
                        Origin myOrigin = thisRenderer.currentAssociatedOrigin;
                        
                        if (myOrigin != null) {
                            Identifier originId = myOrigin.getIdentifier();
                            if (originId != null && originId.getPath().contains("original")) {
                                // This is the "Original" form renderer (Human parts), but we are shifted.
                                // So hide this one.
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore errors during rendering check to prevent crash
            }
        }
        super.render(poseStack, animatable, bufferSource, renderType, buffer, packedLight);
    }
}
