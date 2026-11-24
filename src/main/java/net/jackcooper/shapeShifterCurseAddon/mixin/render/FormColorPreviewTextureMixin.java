package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.ColorPreviewTextures;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Supply SSC's local-player preview processor without locking its global editor state. */
@Mixin(value = FormModel.class, remap = false)
public class FormColorPreviewTextureMixin {
    @ModifyExpressionValue(method = {"getTextureResource", "getOverlayTextureResource", "getEmissiveTextureResource", "getFullBrightTextureResource"},
            at = @At(value = "FIELD", target = "Lnet/onixary/shapeShifterCurseFabric/util/FormTextureUtils;useTempFormTexture:Z"))
    private boolean ssca$previewEnabled(boolean original) {
        return original || ColorPreviewTextures.active() != null;
    }

    @ModifyExpressionValue(method = {"getTextureResource", "getOverlayTextureResource", "getEmissiveTextureResource", "getFullBrightTextureResource"},
            at = @At(value = "FIELD", target = "Lnet/onixary/shapeShifterCurseFabric/util/FormTextureUtils;tempFormTextureProcessor:Lnet/onixary/shapeShifterCurseFabric/util/FormTextureUtils$TempFormTextureProcessor;"))
    private FormTextureUtils.TempFormTextureProcessor ssca$previewProcessor(FormTextureUtils.TempFormTextureProcessor original) {
        var session = ColorPreviewTextures.active();
        return session != null && !FormTextureUtils.useTempFormTexture ? session : original;
    }
}
