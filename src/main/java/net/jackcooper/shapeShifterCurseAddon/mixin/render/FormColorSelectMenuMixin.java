package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.FormColorPreviewState;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exposes preview state and accepts short hexadecimal colors in SSC's color picker. */
@Pseudo
@Mixin(targets = "net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenu", priority = 1100)
public abstract class FormColorSelectMenuMixin extends Screen implements FormColorPreviewState {
    @Shadow(remap = false) private boolean enableFormColorSystem;
    @Shadow(remap = false) private boolean keepOriginalSkin;
    @Shadow(remap = false) public abstract FormTextureUtils.ColorSetting getColorSetting(boolean isABGR);

    @Override public boolean ssca$colorEnabled() { return enableFormColorSystem; }
    @Override public boolean ssca$keepOriginalSkin() { return keepOriginalSkin; }
    @Override public FormTextureUtils.ColorSetting ssca$previewColors() { return getColorSetting(true); }

    protected FormColorSelectMenuMixin(Text title) {
        super(title);
    }

    @Inject(
        method = "decodeColor(Ljava/lang/String;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void sscAddon$normalizeShortHex(String color, CallbackInfoReturnable<Integer> cir) {
        if (color == null || color.isEmpty()) {
            return;
        }
        String s = color.trim();
        if (!s.startsWith("#")) {
            return;
        }
        String hex = s.substring(1);
        // 仅处理需要补齐的短 hex；标准 8 位 (#AARRGGBB) 和非法长度都留给原方法
        String normalized;
        if (hex.length() == 3) {
            // #RGB → #FFRRGGBB
            char r = hex.charAt(0);
            char g = hex.charAt(1);
            char b = hex.charAt(2);
            normalized = "FF" + r + r + g + g + b + b;
        } else if (hex.length() == 4) {
            // #ARGB → #AARRGGBB
            char a = hex.charAt(0);
            char r = hex.charAt(1);
            char g = hex.charAt(2);
            char b = hex.charAt(3);
            normalized = "" + a + a + r + r + g + g + b + b;
        } else if (hex.length() == 6) {
            // #RRGGBB → #FFRRGGBB
            normalized = "FF" + hex;
        } else {
            return;
        }
        try {
            cir.setReturnValue(Integer.parseUnsignedInt(normalized, 16));
        } catch (NumberFormatException ignored) {
            // 解析失败留给原方法
        }
    }
}
