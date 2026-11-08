package net.jackcooper.shapeShifterCurseAddon.client.colorpicker;

import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;

/** Read the working palette without writing preview values to player components. */
public interface FormColorPreviewState {
    boolean ssca$colorEnabled();
    boolean ssca$keepOriginalSkin();
    FormTextureUtils.ColorSetting ssca$previewColors();
}
