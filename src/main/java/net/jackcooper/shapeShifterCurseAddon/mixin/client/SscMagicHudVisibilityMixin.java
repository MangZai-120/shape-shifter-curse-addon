package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.magic.MagicGUIRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only hides the renderer; magic selection, input and cooldowns keep ticking. */
@Mixin(MagicGUIRender.class)
public abstract class SscMagicHudVisibilityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$hideMagicHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (!SSCAddonConfig.client().showSscMagicHud) ci.cancel();
    }
}
