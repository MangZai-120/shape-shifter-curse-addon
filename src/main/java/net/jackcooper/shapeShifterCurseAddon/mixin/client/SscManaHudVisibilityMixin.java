package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.mana.FamiliarFoxManaBar;
import net.onixary.shapeShifterCurseFabric.mana.InstinctBarLikeManaBar;
import net.onixary.shapeShifterCurseFabric.mana.WebResourceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FamiliarFoxManaBar.class, InstinctBarLikeManaBar.class, WebResourceBar.class})
public abstract class SscManaHudVisibilityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$hideManaHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        var config=SSCAddonConfig.client();
        boolean visible=((Object)this) instanceof InstinctBarLikeManaBar
                ? config.showInstinctBar : config.showManaBar;
        if (!visible) ci.cancel();
    }
}
