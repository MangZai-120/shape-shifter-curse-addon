package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.FormColorPreviewState;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep SSCA's eye preview in the same A/B order as the saved server palette. */
@Pseudo
@Mixin(targets = "net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenuV2", remap = false)
public abstract class FormColorSelectMenuV2Mixin implements FormColorPreviewState {
    @Shadow public abstract IForm getForm();
    @Shadow public abstract FormTextureUtils.ColorSetting getColorSetting(boolean isABGR);
    @Shadow private boolean enableFormColorSystem;
    @Shadow private boolean keepCustomSkin;

    @Override public boolean ssca$colorEnabled() { return enableFormColorSystem; }
    @Override public boolean ssca$keepOriginalSkin() { return keepCustomSkin; }
    @Override public FormTextureUtils.ColorSetting ssca$previewColors() { return getColorSetting(true); }

    @Inject(method = "getColorSetting", at = @At("RETURN"), cancellable = true)
    private void ssca$correctEyePreview(boolean isABGR,
                                       CallbackInfoReturnable<FormTextureUtils.ColorSetting> cir) {
        if (!isABGR) return;
        IForm form = getForm();
        if (form == null || !form.getFormID().getNamespace().equals("my_addon")
                || form.getFormID().equals(FormIdentifiers.AXOLOTL_ALING)
                || form.getFormID().equals(FormIdentifiers.FAMILIAR_FOX_RED)
                || form.getFormID().equals(FormIdentifiers.SHEEP_FORM)) return;
        FormTextureUtils.ColorSetting argb = getColorSetting(false);
        cir.setReturnValue(new FormTextureUtils.ColorSetting(
                FormTextureUtils.ARGB2ABGR(argb.primaryColor()),
                FormTextureUtils.ARGB2ABGR(argb.accentColor1()),
                FormTextureUtils.ARGB2ABGR(argb.accentColor2()),
                FormTextureUtils.ARGB2ABGR(argb.eyeColorA()),
                FormTextureUtils.ARGB2ABGR(argb.eyeColorB()),
                argb.primaryGreyReverse(), argb.accent1GreyReverse(), argb.accent2GreyReverse()));
    }
}
